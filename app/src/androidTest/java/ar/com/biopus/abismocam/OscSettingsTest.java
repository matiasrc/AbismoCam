package ar.com.biopus.abismocam;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Spinner;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class OscSettingsTest {
    @Test public void defaultMigrationAndAllIntervalChoicesPersist() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.stopService(new android.content.Intent(context, TransmissionService.class)); SystemClock.sleep(300);
        String previous = context.getSharedPreferences("config", 0).getString("json", "{}");
        try {
            context.getSharedPreferences("config", 0).edit().putString("json",
                "{\"schemaVersion\":3,\"address\":\"/custom\",\"width\":1280,\"fps\":15}").commit();
            Config migrated = Config.load(context);
            assertEquals(50, migrated.oscIntervalMs);
            assertEquals(5, migrated.countdownSeconds);
            assertEquals("/custom", migrated.address); assertEquals(768, migrated.width); assertEquals(15, migrated.fps);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                for (int choice = 0; choice < 4; choice++) {
                    scenario.onActivity(a -> {
                        View gear = a.findViewById(R.id.settings);
                        long now = SystemClock.uptimeMillis();
                        MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 20, 20, 0);
                        gear.dispatchTouchEvent(event); event.recycle();
                    });
                    SystemClock.sleep(3250);
                    final int selected = choice;
                    scenario.onActivity(a -> {
                        Spinner spinner = a.findViewById(R.id.osc_interval);
                        assertNotNull(spinner);
                        spinner.setSelection(selected);
                        Spinner countdown = a.findViewById(R.id.countdown_seconds);
                        assertEquals(16, countdown.getCount());
                        countdown.setSelection(new int[] {0, 1, 5, 15}[selected]);
                        a.findViewById(R.id.save_settings).performClick();
                    });
                    assertEquals(new int[] {10, 25, 50, 100}[choice], Config.load(context).oscIntervalMs);
                    assertEquals(new int[] {0, 1, 5, 15}[choice], Config.load(context).countdownSeconds);
                    final int duration = new int[] {0, 1, 5, 15}[choice];
                    scenario.onActivity(a -> assertTrue(((android.widget.TextView)a.findViewById(R.id.capture_status)).getText().toString()
                        .contains(duration == 0 ? "sacar la foto" : duration + " s")));
                }
                scenario.recreate();
                assertEquals(100, Config.load(context).oscIntervalMs);
                assertEquals(15, Config.load(context).countdownSeconds);
            }
        } finally { context.getSharedPreferences("config", 0).edit().putString("json", previous).commit(); }
    }
}
