package ar.com.biopus.abismocam;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.lang.reflect.Field;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

public class BackgroundTransmissionTest {
    private static Object field(Object object, String name) {
        try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static void until(BooleanSupplier check) {
        long end = SystemClock.elapsedRealtime() + 20000;
        while (SystemClock.elapsedRealtime() < end) { if (check.getAsBoolean()) return; SystemClock.sleep(50); }
        fail("No se cumplió la condición de transmisión");
    }
    private static void shell(String command) throws Exception {
        var fd = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
        try (InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) { while (input.read() != -1) { } }
    }
    @Test public void keepsSenderInBackgroundAndLockedThenStopsWhenTaskRemoved() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.stopService(new Intent(context, TransmissionService.class)); SystemClock.sleep(300);
        shell("pm grant " + context.getPackageName() + " android.permission.CAMERA");
        if (android.os.Build.VERSION.SDK_INT >= 33) shell("pm grant " + context.getPackageName() + " android.permission.POST_NOTIFICATIONS");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<TransmissionService> ref = new AtomicReference<>();
            until(() -> { scenario.onActivity(a -> ref.set((TransmissionService)field(a, "transmission"))); return ref.get() != null && ref.get().frames > 5; });
            TransmissionService service = ref.get();
            Object sender = field(service.ndi, "sender");
            scenario.moveToState(Lifecycle.State.CREATED);
            SystemClock.sleep(6000);
            long background = service.frames;
            until(() -> service.frames > background + 15);
            assertTrue(service.streaming);
            assertEquals(sender, field(service.ndi, "sender"));
            shell("input keyevent 223");
            SystemClock.sleep(6000);
            long locked = service.frames;
            until(() -> service.frames > locked + 15);
            assertTrue(service.streaming);
            assertEquals(sender, field(service.ndi, "sender"));
            shell("input keyevent 224"); shell("wm dismiss-keyguard");
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.recreate();
            until(() -> { scenario.onActivity(a -> ref.set((TransmissionService)field(a, "transmission"))); return ref.get() != null; });
            assertSame(service, ref.get());
            long returned = service.frames;
            until(() -> service.frames > returned + 5);
            assertEquals(sender, field(service.ndi, "sender"));
            // Quitar la tarea como lo hace recientes, no llamar al callback a mano.
            scenario.onActivity(a -> {
                ActivityManager manager = a.getSystemService(ActivityManager.class);
                for (ActivityManager.AppTask task : manager.getAppTasks()) task.finishAndRemoveTask();
            });
            until(() -> (boolean)field(service, "closed") && !service.streaming);
            assertFalse(service.cameraReady);
            until(() -> field(service.ndi, "sender") == null);
        } finally {
            shell("input keyevent 224"); shell("wm dismiss-keyguard");
            context.stopService(new Intent(context, TransmissionService.class));
        }
    }

    @Test public void notificationStopsNdiAndReturningDoesNotRestartIt() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.stopService(new Intent(context, TransmissionService.class)); SystemClock.sleep(300);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<TransmissionService> ref = new AtomicReference<>();
            until(() -> { scenario.onActivity(a -> ref.set((TransmissionService)field(a, "transmission"))); return ref.get() != null && ref.get().frames > 5; });
            TransmissionService service = ref.get();
            scenario.moveToState(Lifecycle.State.CREATED);
            context.startService(new Intent(context, TransmissionService.class).setAction(TransmissionService.STOP));
            until(() -> !service.streaming);
            scenario.moveToState(Lifecycle.State.RESUMED);
            SystemClock.sleep(500);
            assertFalse(service.streaming); assertFalse(service.wanted);
            scenario.onActivity(a -> service.setWanted(true));
            long frames = service.frames;
            until(() -> service.frames > frames + 5);
        } finally { context.stopService(new Intent(context, TransmissionService.class)); }
    }
}
