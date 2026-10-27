package ar.com.biopus.abismocam;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Spinner;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.file.Files;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NdiTransportTest {
    private static Object field(MainActivity activity, String name) {
        try { Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static void awaitFrames(ActivityScenario<MainActivity> scenario) {
        AtomicReference<Long> start = new AtomicReference<>();
        scenario.onActivity(a -> start.set(a.ndiFramesSent));
        long end = SystemClock.elapsedRealtime() + 15000;
        while (SystemClock.elapsedRealtime() < end) {
            AtomicReference<Boolean> ready = new AtomicReference<>(false);
            scenario.onActivity(a -> ready.set((boolean)field(a, "streaming") && a.ndiFramesSent > start.get() + 3));
            if (ready.get()) return;
            SystemClock.sleep(50);
        }
        fail("NDI no reanudó el envío");
    }
    @Test public void switchBothWaysPreservesVideoSettingsAndPersistsSdkConfig() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.stopService(new android.content.Intent(context, TransmissionService.class)); SystemClock.sleep(300);
        String previous = context.getSharedPreferences("config", 0).getString("json", "{}");
        try {
            Config initial = new Config(); initial.save(context);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                awaitFrames(scenario);
                for (boolean multicast : new boolean[] {true, false}) {
                    scenario.onActivity(a -> {
                        View gear = a.findViewById(R.id.settings);
                        long now = SystemClock.uptimeMillis();
                        MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 20, 20, 0);
                        gear.dispatchTouchEvent(event); event.recycle();
                    });
                    SystemClock.sleep(3250);
                    scenario.onActivity(a -> {
                        assertTrue((boolean)field(a, "settingsVisible"));
                        ((Spinner)a.findViewById(R.id.ndi_transport)).setSelection(multicast ? 1 : 0);
                        // La selección no cambia el emisor hasta guardar.
                        assertEquals(!multicast, ((Config)field(a, "config")).ndiMulticast);
                        a.findViewById(R.id.save_settings).performClick();
                    });
                    awaitFrames(scenario);
                    Config saved = Config.load(context);
                    assertEquals(multicast, saved.ndiMulticast);
                    assertEquals("abismoCam_0001", saved.sourceName()); assertEquals(576, saved.width); assertEquals(30, saved.fps);
                    File file = new File(context.getFilesDir(), "ndi/ndi-config.v1.json");
                    JSONObject send = new JSONObject(new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8))
                        .getJSONObject("ndi").getJSONObject("multicast").getJSONObject("send");
                    assertEquals(multicast, send.getBoolean("enable")); assertEquals(1, send.getInt("ttl"));
                    assertEquals(new File(context.getFilesDir(), "ndi").getAbsolutePath(), android.system.Os.getenv("NDI_CONFIG_DIR"));
                }
            }
        } finally { context.getSharedPreferences("config", 0).edit().putString("json", previous).commit(); }
    }
}
