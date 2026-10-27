package ar.com.biopus.abismocam;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import java.nio.ByteBuffer;
import java.util.Collections;

/** ABI C del SDK NDI. Todas las llamadas se realizan en el executor de cámara. */
final class NdiSender {
    private NativeLibrary library;
    private Function sendVideo;
    private Pointer sender;
    private boolean initialized;
    private final VideoFrame frame = new VideoFrame();
    private CreateSettings settings;
    private Boolean multicastMode;

    @Structure.FieldOrder({"name", "groups", "clockVideo", "clockAudio"})
    public static class CreateSettings extends Structure {
        public String name;
        public Pointer groups;
        // C++ bool ocupa un byte; JNA boolean ocuparía cuatro.
        // clockVideo = 1 mantiene cadencia estable hacia OBS (obs-ndi / DistroAV) para evitar congelamientos de buffer.
        public byte clockVideo = 1;
        public byte clockAudio = 0;
    }

    @Structure.FieldOrder({"width", "height", "fourCC", "fpsN", "fpsD", "aspect", "format", "timecode", "data", "stride", "metadata", "timestamp"})
    public static class VideoFrame extends Structure {
        public int width, height;
        public int fourCC = ('R') | ('G' << 8) | ('B' << 16) | ('A' << 24);
        public int fpsN = 30, fpsD = 1;
        public float aspect;
        public int format = 1; // NDIlib_frame_format_type_progressive
        public long timecode = Long.MAX_VALUE; // NDIlib_send_timecode_synthesize
        public Pointer data;
        public int stride;
        public Pointer metadata;
        public long timestamp;
    }

    void start(String name, int fps, java.io.File configDirectory, boolean multicast) {
        close();
        NdiTransport.configure(configDirectory, multicast);
        multicastMode = multicast;
        if (library == null) library = NativeLibrary.getInstance("ndi", Collections.singletonMap("string-encoding", "UTF-8"));
        int ok = library.getFunction("NDIlib_initialize").invokeInt(new Object[0]);
        if ((ok & 0xff) == 0) throw new IllegalStateException("El SDK NDI no pudo inicializarse.");
        initialized = true;
        settings = new CreateSettings();
        settings.name = name;
        settings.write();
        sender = library.getFunction("NDIlib_send_create").invokePointer(new Object[] {settings.getPointer()});
        if (sender == null) throw new IllegalStateException("No se pudo crear la fuente NDI.");
        sendVideo = library.getFunction("NDIlib_send_send_video_v2");
        frame.fpsN = fps;
    }

    void send(ByteBuffer pixels, int width, int height, int stride) {
        if (sender == null) return;
        if (!pixels.isDirect()) throw new IllegalArgumentException("Se esperaba un buffer directo de cámara.");
        if (stride < width * 4 || pixels.remaining() < (long)(height - 1) * stride + width * 4L)
            throw new IllegalArgumentException("Tamaño de cuadro RGBA inválido.");
        frame.width = width;
        frame.height = height;
        frame.aspect = (float)width / height;
        frame.data = Native.getDirectBufferPointer(pixels.slice());
        frame.stride = stride;
        frame.write();
        // Envío síncrono con clockVideo = 1 para regular cadencia exacta hacia receptores NDI.
        sendVideo.invokeVoid(new Object[] {sender, frame.getPointer()});
    }

    int connections() {
        return sender == null ? 0 : library.getFunction("NDIlib_send_get_no_connections").invokeInt(new Object[] {sender, 0});
    }

    void stop() {
        if (sender != null) {
            library.getFunction("NDIlib_send_destroy").invokeVoid(new Object[] {sender});
            sender = null;
        }
        settings = null;
    }

    void close() {
        stop();
        if (initialized) {
            library.getFunction("NDIlib_destroy").invokeVoid(new Object[0]);
            initialized = false;
        }
    }
}
