package ar.com.biopus.abismocam;

import android.Manifest;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CameraFlowTest {
    private static Object field(MainActivity activity, String name) {
        try { Field field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); return field.get(activity); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static <T> T read(ActivityScenario<MainActivity> scenario, String name) {
        AtomicReference<T> result = new AtomicReference<>();
        scenario.onActivity(a -> result.set((T)field(a, name)));
        return result.get();
    }
    private static void until(BooleanSupplier condition, long timeout) {
        long end = SystemClock.elapsedRealtime() + timeout;
        while (SystemClock.elapsedRealtime() < end) { if (condition.getAsBoolean()) return; SystemClock.sleep(20); }
        fail("No se cumplió la condición en " + timeout + " ms");
    }
    private static void touch(ActivityScenario<MainActivity> scenario, int action) {
        scenario.onActivity(a -> {
            View shutter = a.findViewById(R.id.shutter);
            long now = SystemClock.uptimeMillis();
            MotionEvent event = MotionEvent.obtain(now, now, action, shutter.getWidth() / 2f, shutter.getHeight() / 2f, 0);
            shutter.dispatchTouchEvent(event); event.recycle();
        });
    }
    private static int receive(DatagramSocket receiver) throws Exception {
        DatagramPacket packet = new DatagramPacket(new byte[128], 128);
        receiver.receive(packet);
        assertEquals(24, packet.getLength());
        return ByteBuffer.wrap(packet.getData(), 20, 4).getInt();
    }
    private static void grantCamera(Context context) throws Exception {
        try (android.os.ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation().getUiAutomation()
            .executeShellCommand("pm grant " + context.getPackageName() + " " + Manifest.permission.CAMERA);
             InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            while (input.read() != -1) { /* esperar la concesión */ }
        }
    }

    @Test public void zeroSecondsCapturesImmediatelyAndSendsBothBursts() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.stopService(new android.content.Intent(context, TransmissionService.class)); SystemClock.sleep(300);
        grantCamera(context);
        String previous = context.getSharedPreferences("config", 0).getString("json", "{}");
        Uri created = null;
        try (DatagramSocket receiver = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            receiver.setSoTimeout(1500);
            Config config = new Config(); config.countdownSeconds = 0; config.oscIntervalMs = 100;
            config.destinations.add(new Osc.Destination("QA", "127.0.0.1", receiver.getLocalPort(), true));
            config.save(context);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                until(() -> (long)read(scenario, "ndiFramesSent") > 4, 20000);
                long before = read(scenario, "ndiFramesSent");
                touch(scenario, MotionEvent.ACTION_DOWN);
                SystemClock.sleep(100);
                scenario.onActivity(a -> assertEquals(CaptureSequence.State.IDLE, ((CaptureSequence)field(a, "sequence")).state()));
                touch(scenario, MotionEvent.ACTION_UP);
                scenario.onActivity(a -> {
                    assertNotEquals(CaptureSequence.State.COUNTDOWN, ((CaptureSequence)field(a, "sequence")).state());
                    assertEquals(View.GONE, a.findViewById(R.id.countdown).getVisibility());
                    assertFalse(a.findViewById(R.id.shutter).isEnabled());
                });
                for (int i = 0; i < 10; i++) assertEquals(i < 5 ? 1 : 0, receive(receiver));
                until(() -> read(scenario, "lastPhotoUri") != null, 6000);
                created = read(scenario, "lastPhotoUri");
                try (InputStream input = context.getContentResolver().openInputStream(created)) {
                    Bitmap photo = BitmapFactory.decodeStream(input);
                    assertNotNull(photo);
                    assertEquals(576, photo.getWidth()); assertEquals(720, photo.getHeight());
                }
                assertTrue((long)read(scenario, "ndiFramesSent") > before);
                until(() -> !((CaptureSequence)read(scenario, "sequence")).isBusy(), 4000);
                receiver.setSoTimeout(200);
                assertThrows(java.net.SocketTimeoutException.class, () -> receive(receiver));
            }
        } finally {
            if (created != null) context.getContentResolver().delete(created, null, null);
            context.getSharedPreferences("config", 0).edit().putString("json", previous).commit();
        }
    }

    @Test public void releaseCountdownPhotoAndNdiStayIndependent() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.stopService(new android.content.Intent(context, TransmissionService.class)); SystemClock.sleep(300);
        grantCamera(context);
        String previous = context.getSharedPreferences("config", 0).getString("json", "{}");
        Uri created = null;
        try (DatagramSocket receiver = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            receiver.setSoTimeout(1200);
            Config config = new Config(); config.width = 384; config.height = 480; config.fps = 15;
            config.destinations.add(new Osc.Destination("QA", "127.0.0.1", receiver.getLocalPort(), true));
            config.save(context);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                until(() -> Boolean.TRUE.equals(read(scenario, "cameraReady")), 20000);
                assertTrue(((Config)read(scenario, "config")).frontCamera);
                until(() -> Boolean.TRUE.equals(read(scenario, "streaming")), 10000);
                until(() -> (long)read(scenario, "ndiFramesSent") > 2, 10000);
                touch(scenario, MotionEvent.ACTION_DOWN);
                SystemClock.sleep(300);
                scenario.onActivity(a -> assertEquals(View.GONE, a.findViewById(R.id.countdown).getVisibility()));
                receiver.setSoTimeout(150);
                assertThrows(java.net.SocketTimeoutException.class, () -> receive(receiver));
                long start = SystemClock.elapsedRealtime();
                long before = read(scenario, "ndiFramesSent");
                touch(scenario, MotionEvent.ACTION_UP);
                receiver.setSoTimeout(1500); assertEquals(1, receive(receiver));
                // Intentos repetidos durante el conteo no generan otra secuencia.
                scenario.onActivity(a -> { for (int i = 0; i < 4; i++) a.findViewById(R.id.shutter).performClick(); });
                for (int i = 0; i < 4; i++) assertEquals(1, receive(receiver));
                receiver.setSoTimeout(6500); assertEquals(0, receive(receiver));
                long elapsed = SystemClock.elapsedRealtime() - start;
                assertTrue("OSC 0 antes de cinco segundos: " + elapsed, elapsed >= 4850);
                until(() -> {
                    AtomicReference<Boolean> visible = new AtomicReference<>(false);
                    scenario.onActivity(a -> visible.set(a.findViewById(R.id.frozen_frame).getVisibility() == View.VISIBLE));
                    return visible.get();
                }, 4000);
                long atFreeze = read(scenario, "ndiFramesSent");
                assertTrue("NDI se detuvo durante el conteo", atFreeze > before);
                AtomicReference<Bitmap> frozen = new AtomicReference<>();
                scenario.onActivity(a -> {
                    assertFalse(a.findViewById(R.id.shutter).isEnabled());
                    frozen.set(((BitmapDrawable)((ImageView)a.findViewById(R.id.frozen_frame)).getDrawable()).getBitmap());
                });
                SystemClock.sleep(400);
                assertTrue("NDI se detuvo durante el congelado", (long)read(scenario, "ndiFramesSent") > atFreeze);
                assertTrue(Boolean.TRUE.equals(read(scenario, "streaming")));
                until(() -> {
                    AtomicReference<Boolean> live = new AtomicReference<>(false);
                    scenario.onActivity(a -> live.set(a.findViewById(R.id.frozen_frame).getVisibility() == View.GONE && a.findViewById(R.id.shutter).isEnabled()));
                    return live.get();
                }, 3000);
                until(() -> read(scenario, "lastPhotoUri") != null, 8000);
                created = read(scenario, "lastPhotoUri");
                try (InputStream input = context.getContentResolver().openInputStream(created)) {
                    Bitmap saved = BitmapFactory.decodeStream(input);
                    assertNotNull(saved); assertEquals(frozen.get().getWidth(), saved.getWidth());
                    assertEquals(frozen.get().getHeight(), saved.getHeight());
                }
                for (int i = 0; i < 4; i++) assertEquals(0, receive(receiver));
                receiver.setSoTimeout(200);
                assertThrows(java.net.SocketTimeoutException.class, () -> receive(receiver));
            }
        } finally {
            if (created != null) context.getContentResolver().delete(created, null, null);
            context.getSharedPreferences("config", 0).edit().putString("json", previous).commit();
        }
    }

    @Test public void cancellingTouchDoesNotStartAndLeavingDuringCountdownClearsOsc() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.stopService(new android.content.Intent(context, TransmissionService.class)); SystemClock.sleep(300);
        grantCamera(context);
        String previous = context.getSharedPreferences("config", 0).getString("json", "{}");
        try (DatagramSocket receiver = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            Config config = new Config(); config.width = 384; config.height = 480; config.fps = 15;
            config.destinations.add(new Osc.Destination("QA", "127.0.0.1", receiver.getLocalPort(), true)); config.save(context);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                until(() -> Boolean.TRUE.equals(read(scenario, "cameraReady")), 20000);
                touch(scenario, MotionEvent.ACTION_DOWN); touch(scenario, MotionEvent.ACTION_CANCEL);
                receiver.setSoTimeout(200); assertThrows(java.net.SocketTimeoutException.class, () -> receive(receiver));
                // Clic accesible también tiene la misma cuenta de cinco segundos.
                scenario.onActivity(a -> a.findViewById(R.id.shutter).performClick());
                receiver.setSoTimeout(1500); assertEquals(1, receive(receiver));
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
                // Puede haber unos 1 ya recibidos antes de onPause; después del primer 0 no debe quedar ninguno.
                int next;
                int ones = 1;
                while ((next = receive(receiver)) == 1) assertTrue(++ones <= 5);
                assertEquals(0, next);
                for (int i = 0; i < 4; i++) assertEquals(0, receive(receiver));
                receiver.setSoTimeout(200);
                assertThrows(java.net.SocketTimeoutException.class, () -> receive(receiver));
            }
        } finally { context.getSharedPreferences("config", 0).edit().putString("json", previous).commit(); }
    }

    @Test public void protectedSettingsKeepNdiAndVideoChoicesPersist() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.stopService(new android.content.Intent(context, TransmissionService.class)); SystemClock.sleep(300);
        grantCamera(context);
        String previous = context.getSharedPreferences("config", 0).getString("json", "{}");
        try {
            context.getSharedPreferences("config", 0).edit().putString("json", "{\"schemaVersion\":2,\"lowQuality\":true}").commit();
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                until(() -> Boolean.TRUE.equals(read(scenario, "streaming")), 15000);
                Config initial = read(scenario, "config");
                assertEquals(576, initial.width); assertEquals(720, initial.height); assertEquals(30, initial.fps);
                assertEquals("abismoCam_0001", initial.sourceName());
                scenario.onActivity(a -> {
                    View viewport = a.findViewById(R.id.camera_viewport);
                    View header = a.findViewById(R.id.camera_header);
                    View footer = a.findViewById(R.id.camera_footer);
                    int[] imagePosition = new int[2], headerPosition = new int[2], footerPosition = new int[2];
                    viewport.getLocationOnScreen(imagePosition); header.getLocationOnScreen(headerPosition); footer.getLocationOnScreen(footerPosition);
                    assertTrue(viewport.getHeight() > 0);
                    assertEquals(viewport.getWidth() * 5, viewport.getHeight() * 4);
                    assertTrue(headerPosition[1] + header.getHeight() <= imagePosition[1]);
                    assertTrue(imagePosition[1] + viewport.getHeight() <= footerPosition[1]);
                    assertNull(a.findViewById(R.id.ndi_toggle));
                    assertFalse(a.findViewById(R.id.ndi_indicator).isClickable());
                    a.findViewById(R.id.settings).performClick();
                });
                assertFalse((boolean)read(scenario, "settingsVisible"));
                for (int choice = 0; choice < 3; choice++) {
                    long before = read(scenario, "ndiFramesSent");
                    gearTouch(scenario, MotionEvent.ACTION_DOWN);
                    SystemClock.sleep(1200);
                    assertFalse((boolean)read(scenario, "settingsVisible"));
                    until(() -> Boolean.TRUE.equals(read(scenario, "settingsVisible")), 3000);
                    gearTouch(scenario, MotionEvent.ACTION_UP);
                    assertTrue((boolean)read(scenario, "streaming"));
                    assertTrue((long)read(scenario, "ndiFramesSent") > before);
                    for (int rate = 0; rate < 2; rate++) {
                        if (rate > 0) {
                            gearTouch(scenario, MotionEvent.ACTION_DOWN);
                            until(() -> Boolean.TRUE.equals(read(scenario, "settingsVisible")), 4000);
                            gearTouch(scenario, MotionEvent.ACTION_UP);
                        }
                        final int selected = choice, selectedRate = rate;
                        scenario.onActivity(a -> {
                            ((android.widget.Spinner)a.findViewById(R.id.resolution)).setSelection(selected);
                            ((android.widget.Spinner)a.findViewById(R.id.fps)).setSelection(selectedRate);
                            a.findViewById(R.id.save_settings).performClick();
                        });
                        until(() -> Boolean.TRUE.equals(read(scenario, "streaming")), 15000);
                        long start = read(scenario, "ndiFramesSent");
                        until(() -> (long)read(scenario, "ndiFramesSent") > start + 2, 15000);
                        Config saved = Config.load(context);
                        assertEquals(new int[] {384, 576, 768}[choice], saved.width);
                        assertEquals(saved.width * 5 / 4, saved.height);
                        assertEquals(rate == 0 ? 15 : 30, saved.fps);
                        NdiSender sender = read(scenario, "ndi");
                        Field frameField = NdiSender.class.getDeclaredField("frame"); frameField.setAccessible(true);
                        NdiSender.VideoFrame frame = (NdiSender.VideoFrame)frameField.get(sender);
                        assertEquals(saved.width, frame.width); assertEquals(saved.height, frame.height);
                        assertEquals(saved.fps, frame.fpsN);
                    }
                }
            }
        } finally { context.getSharedPreferences("config", 0).edit().putString("json", previous).commit(); }
    }

    private static void gearTouch(ActivityScenario<MainActivity> scenario, int action) {
        scenario.onActivity(a -> {
            View gear = (View)field(a, "settingsButton");
            long now = SystemClock.uptimeMillis();
            MotionEvent event = MotionEvent.obtain(now, now, action, gear.getWidth() / 2f, gear.getHeight() / 2f, 0);
            gear.dispatchTouchEvent(event); event.recycle();
        });
    }
}
