package ar.com.biopus.abismocam;

import android.content.Context;
import android.os.SystemClock;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class SourceIdentityAndroidTest {
    private static Object field(Object instance, String name) {
        try { Field f = instance.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(instance); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static void settings(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(a -> {
            try { Method m = MainActivity.class.getDeclaredMethod("showSettings"); m.setAccessible(true); m.invoke(a); }
            catch (Exception e) { throw new AssertionError(e); }
        });
    }
    private static void awaitSource(ActivityScenario<MainActivity> scenario, String expected) {
        long until = SystemClock.elapsedRealtime() + 20000;
        while (SystemClock.elapsedRealtime() < until) {
            AtomicBoolean ready = new AtomicBoolean();
            scenario.onActivity(a -> {
                if (!(boolean)field(a, "streaming")) return;
                Object sender = field(a, "ndi");
                assertEquals(expected, field(field(sender, "settings"), "name"));
                ready.set(a.ndiFramesSent > 2);
            });
            if (ready.get()) return;
            SystemClock.sleep(50);
        }
        fail("No inició la fuente " + expected);
    }

    @Test public void migrationAndCounterSurviveReloadWithoutLosingOtherSettings() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.stopService(new android.content.Intent(context, TransmissionService.class)); SystemClock.sleep(300);
        String previous = context.getSharedPreferences("config", 0).getString("json", "{}");
        try {
            context.getSharedPreferences("config", 0).edit().putString("json",
                "{\"schemaVersion\":4,\"width\":768,\"fps\":15,\"address\":\"/custom\",\"ndiMulticast\":true,\"countdownSeconds\":7}").commit();
            Config c = Config.load(context);
            assertEquals("abismoCam_0001", c.sourceName());
            c.sourceIdentifier = "Li"; c.sourceCounter = new BigInteger("9999"); c.renewSource(); c.save(context);
            c = Config.load(context);
            assertEquals("abismoCam_Li_10000", c.sourceName());
            assertEquals(768, c.width); assertEquals(960, c.height); assertEquals(15, c.fps);
            assertEquals(7, c.countdownSeconds); assertEquals("/custom", c.address); assertTrue(c.ndiMulticast);
        } finally { context.getSharedPreferences("config", 0).edit().putString("json", previous).commit(); }
    }

    @Test public void renewAndRenamePreserveStateAndUsePersistedIdentity() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.stopService(new android.content.Intent(context, TransmissionService.class)); SystemClock.sleep(300);
        String previous = context.getSharedPreferences("config", 0).getString("json", "{}");
        try {
            Config c = new Config(); c.sourceIdentifier = "Li"; c.save(context);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                awaitSource(scenario, "abismoCam_Li_0001");
                settings(scenario);
                scenario.onActivity(a -> {
                    ((EditText)a.findViewById(R.id.ndi_source_identifier)).setText("Ana");
                    a.findViewById(R.id.ndi_renew_source).performClick();
                    assertEquals("Fuente NDI: abismoCam_Li_0002", ((TextView)a.findViewById(R.id.ndi_source_name)).getText().toString());
                });
                awaitSource(scenario, "abismoCam_Li_0002");
                assertEquals("abismoCam_Li_0002", Config.load(context).sourceName());
                scenario.onActivity(a -> a.findViewById(R.id.save_settings).performClick());
                awaitSource(scenario, "abismoCam_Ana_0002");
                settings(scenario);
                scenario.onActivity(a -> {
                    a.findViewById(R.id.ndi_toggle).performClick();
                    a.findViewById(R.id.ndi_renew_source).performClick();
                    assertFalse((boolean)field(a, "streaming")); assertFalse((boolean)field(a, "starting"));
                    ((EditText)a.findViewById(R.id.ndi_source_identifier)).setText("Juan");
                    a.findViewById(R.id.save_settings).performClick();
                    assertFalse((boolean)field(a, "ndiWanted")); assertFalse((boolean)field(a, "starting"));
                });
                assertEquals("abismoCam_Juan_0003", Config.load(context).sourceName());
                settings(scenario);
                scenario.onActivity(a -> a.findViewById(R.id.ndi_toggle).performClick());
                awaitSource(scenario, "abismoCam_Juan_0003");
                scenario.recreate();
                awaitSource(scenario, "abismoCam_Juan_0003");
                assertEquals("abismoCam_Juan_0003", Config.load(context).sourceName());
            }
        } finally { context.getSharedPreferences("config", 0).edit().putString("json", previous).commit(); }
    }
}
