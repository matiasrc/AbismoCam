package ar.com.biopus.abismocam;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import java.nio.ByteBuffer;

/** Buffers reutilizados, exclusivos del executor de cámara. No modifica el original. */
final class FrameScaler {
    private Bitmap input, output;
    private ByteBuffer packed, pixelsA, pixelsB;
    private boolean useSecondBuffer;
    private Canvas canvas;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

    ByteBuffer scale(ByteBuffer source, int width, int height, int stride, Rect viewport, int outWidth, int outHeight) {
        return scale(source, width, height, stride, viewport, outWidth, outHeight, false);
    }

    ByteBuffer scale(ByteBuffer source, int width, int height, int stride, Rect viewport, int outWidth, int outHeight, boolean invert) {
        if (input == null || input.getWidth() != width || input.getHeight() != height) {
            if (input != null) input.recycle();
            input = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            packed = null;
        }
        if (output == null || output.getWidth() != outWidth || output.getHeight() != outHeight) {
            if (output != null) output.recycle();
            output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(output);
            pixelsA = ByteBuffer.allocateDirect(outWidth * outHeight * 4);
            pixelsB = ByteBuffer.allocateDirect(outWidth * outHeight * 4);
            useSecondBuffer = false;
        }
        int rowBytes = width * 4;
        ByteBuffer row = source.duplicate();
        int start = source.position();
        if (stride == rowBytes && row.remaining() >= rowBytes * height) {
            row.limit(start + rowBytes * height);
            input.copyPixelsFromBuffer(row);
        } else {
            if (packed == null || packed.capacity() < rowBytes * height) {
                packed = ByteBuffer.allocateDirect(rowBytes * height);
            }
            packed.clear();
            for (int y = 0; y < height; y++) {
                row.limit(source.limit()); row.position(start + y * stride); row.limit(row.position() + rowBytes);
                packed.put(row);
            }
            packed.flip(); input.copyPixelsFromBuffer(packed);
        }
        // Usar el área de sensor compartida con Preview, ya rotada por CameraX.
        // Ajustar solo el redondeo del rectángulo a píxeles, sin estirar la imagen.
        int cropWidth = viewport.width(), cropHeight = viewport.height();
        if ((long)cropWidth * outHeight > (long)cropHeight * outWidth) cropWidth = cropHeight * outWidth / outHeight;
        else cropHeight = cropWidth * outHeight / outWidth;
        int left = viewport.left + (viewport.width() - cropWidth) / 2;
        int top = viewport.top + (viewport.height() - cropHeight) / 2;
        canvas.save();
        if (invert) canvas.rotate(180f, outWidth / 2f, outHeight / 2f);
        canvas.drawBitmap(input, new Rect(left, top, left + cropWidth, top + cropHeight), new Rect(0, 0, outWidth, outHeight), paint);
        canvas.restore();
        ByteBuffer pixels = useSecondBuffer ? pixelsB : pixelsA;
        useSecondBuffer = !useSecondBuffer;
        pixels.clear(); output.copyPixelsToBuffer(pixels); pixels.flip();
        return pixels;
    }
}
