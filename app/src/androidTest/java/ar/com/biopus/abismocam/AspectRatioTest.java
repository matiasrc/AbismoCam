package ar.com.biopus.abismocam;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.nio.ByteBuffer;
import static org.junit.Assert.*;

public class AspectRatioTest {
    @Test public void migratesEachQualityAndPreservesOtherSettings() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String previous = context.getSharedPreferences("config", 0).getString("json", "{}");
        try {
            int[] oldWidths = {640, 960, 1280};
            int[] widths = {384, 576, 768};
            for (int i = 0; i < 3; i++) {
                context.getSharedPreferences("config", 0).edit().putString("json",
                    "{\"schemaVersion\":3,\"width\":" + oldWidths[i] + ",\"fps\":15,\"countdownSeconds\":7,"
                    + "\"oscIntervalMs\":25,\"ndiMulticast\":true,\"frontCamera\":false,\"address\":\"/test\","
                    + "\"destinations\":[{\"name\":\"PC\",\"ip\":\"192.168.0.2\",\"port\":9000,\"enabled\":true}]}").commit();
                Config config = Config.load(context);
                assertEquals(widths[i], config.width); assertEquals(widths[i] * 5 / 4, config.height);
                assertEquals(15, config.fps); assertEquals(7, config.countdownSeconds); assertEquals(25, config.oscIntervalMs);
                assertTrue(config.ndiMulticast); assertFalse(config.frontCamera);
                assertEquals("/test", config.address); assertEquals(1, config.targets().size());
                config.save(context);
                assertEquals(widths[i], Config.load(context).width);
            }
            context.getSharedPreferences("config", 0).edit().putString("json", "{}").commit();
            Config defaults = Config.load(context);
            assertEquals(576, defaults.width); assertEquals(720, defaults.height); assertEquals(30, defaults.fps);
        } finally { context.getSharedPreferences("config", 0).edit().putString("json", previous).commit(); }
    }

    @Test public void respectsCameraCropWithPaddedRowsAtAllOutputSizes() {
        // Rectángulo desplazado: verde a la izquierda, azul a la derecha y rojo fuera.
        // Así se detectan tanto el recorte equivocado como un espejo accidental en NDI.
        int width = 12, height = 16, stride = width * 4 + 12, offset = 8;
        ByteBuffer buffer = ByteBuffer.allocateDirect(offset + stride * height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int pos = offset + y * stride + x * 4;
            boolean inside = x >= 4 && y >= 4 && y < 14;
            buffer.put(pos, (byte)(inside ? 0 : 255));
            buffer.put(pos + 1, (byte)(inside && x < 8 ? 255 : 0));
            buffer.put(pos + 2, (byte)(inside && x >= 8 ? 255 : 0));
            buffer.put(pos + 3, (byte)255);
        }
        buffer.position(offset);
        FrameScaler scaler = new FrameScaler();
        for (int outWidth : new int[] {384, 576, 768}) {
            int outHeight = outWidth * 5 / 4;
            ByteBuffer output = scaler.scale(buffer, width, height, stride, new Rect(4, 4, 12, 14), outWidth, outHeight);
            assertEquals(offset, buffer.position());
            assertEquals(outWidth * outHeight * 4, output.remaining());
            Bitmap bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(output);
            for (int y : new int[] {outHeight / 4, outHeight * 3 / 4}) {
                assertEquals(Color.GREEN, bitmap.getPixel(outWidth / 4, y));
                assertEquals(Color.BLUE, bitmap.getPixel(outWidth * 3 / 4, y));
            }
            bitmap.recycle();
        }
    }

    @Test public void viewportFitsTallAndShortScreensWithoutStretching() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            CameraViewport viewport = new CameraViewport(InstrumentationRegistry.getInstrumentation().getTargetContext());
            for (int[] space : new int[][] {{1080, 1600}, {1080, 800}, {721, 901}}) {
                viewport.measure(View.MeasureSpec.makeMeasureSpec(space[0], View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(space[1], View.MeasureSpec.EXACTLY));
                assertTrue(viewport.getMeasuredWidth() <= space[0]);
                assertTrue(viewport.getMeasuredHeight() <= space[1]);
                assertEquals(viewport.getMeasuredWidth() * 5, viewport.getMeasuredHeight() * 4);
            }
        });
    }
}
