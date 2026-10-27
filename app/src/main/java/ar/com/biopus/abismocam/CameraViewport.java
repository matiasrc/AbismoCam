package ar.com.biopus.abismocam;

import android.content.Context;
import android.widget.FrameLayout;

/** Ventana 9:16 completa: se adapta al espacio libre entre los controles. */
final class CameraViewport extends FrameLayout {
    CameraViewport(Context context) { super(context); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int unit = Math.min(MeasureSpec.getSize(widthSpec) / 9, MeasureSpec.getSize(heightSpec) / 16);
        super.onMeasure(MeasureSpec.makeMeasureSpec(unit * 9, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(unit * 16, MeasureSpec.EXACTLY));
    }
}
