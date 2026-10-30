package ar.com.biopus.abismocam;

import android.Manifest;
import android.app.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.nsd.NsdManager;
import android.net.wifi.WifiManager;
import android.os.*;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import androidx.camera.core.*;
import androidx.camera.core.resolutionselector.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import java.io.File;
import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/** La cámara y el sender pertenecen al servicio, nunca a la actividad. */
public final class TransmissionService extends Service implements LifecycleOwner {
    static final String STOP = "ar.com.biopus.abismocam.STOP_NDI";
    private static final String CHANNEL = "transmission";
    private final LifecycleRegistry lifecycle = new LifecycleRegistry(this);
    private final Handler main = new Handler(Looper.getMainLooper());
    // Serializar también el cierre de una sesión y el inicio de la siguiente:
    // NDIlib_destroy afecta al SDK del proceso, no solo a un sender.
    private static final ExecutorService CAMERA_EXECUTOR = Executors.newSingleThreadExecutor();
    private final ExecutorService executor = CAMERA_EXECUTOR;
    private final FrameScaler scaler = new FrameScaler();
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();
    private final LocalBinder binder = new LocalBinder();
    private ProcessCameraProvider provider;
    private Preview preview;
    private Preview.SurfaceProvider surface;
    private Listener listener;
    private volatile Config config;
    NdiSender ndi;
    volatile boolean streaming;
    boolean starting, wanted = true, cameraReady;
    volatile long frames;
    private volatile long lastAnalysisMs, lastSendStartMs, lastSendEndMs;
    private volatile Thread analysisThread;
    String message = "Iniciando cámara…", cameraMessage = "Iniciando cámara…";
    private int cameraGeneration, senderGeneration;
    private volatile boolean closed;
    private long lastSent, statsStart, lastConnectionsCheckMs;
    private int frameCount, lastConnections, consecutiveErrors;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private WifiManager.MulticastLock multicastLock;
    // El SDK requiere una instancia de NsdManager antes de inicializarse.
    private NsdManager nsdManager;
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback networkCallback;
    private final Map<Network, String> networkAddresses = new ConcurrentHashMap<>();
    private volatile boolean networkNeedsRestart;
    private final Runnable networkRestart = this::restartSenderForNetwork;

    interface Listener { void changed(); }
    interface PhotoCallback { void ready(Bitmap bitmap); void failed(RuntimeException error); }
    private static final class Snapshot {
        final boolean mirrored;
        final PhotoCallback callback;
        Snapshot(boolean mirrored, PhotoCallback callback) { this.mirrored = mirrored; this.callback = callback; }
    }
    final class LocalBinder extends Binder { TransmissionService service() { return TransmissionService.this; } }
    @Override public Lifecycle getLifecycle() { return lifecycle; }

    @Override public void onCreate() {
        super.onCreate();
        config = Config.load(this);
        nsdManager = (NsdManager)getSystemService(NSD_SERVICE);
        lifecycle.setCurrentState(Lifecycle.State.CREATED);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Transmisión de cámara", NotificationManager.IMPORTANCE_LOW));
        registerNetworkMonitor();
    }

    private static String ipv4Signature(LinkProperties properties) {
        if (properties == null) return "";
        StringBuilder sb = new StringBuilder();
        String iface = properties.getInterfaceName();
        if (iface != null) sb.append(iface);
        for (LinkAddress address : properties.getLinkAddresses()) {
            if (address.getAddress() instanceof Inet4Address && !address.getAddress().isLoopbackAddress()) {
                sb.append('|').append(address.getAddress().getHostAddress()).append('/').append(address.getPrefixLength());
            }
        }
        return sb.toString();
    }

    private void bindPreferredNetwork() {
        if (connectivity == null) return;
        Network preferred = null;
        for (Network net : new ArrayList<>(networkAddresses.keySet())) {
            String sig = networkAddresses.get(net);
            if (sig == null || !sig.contains("|")) continue;
            NetworkCapabilities caps = connectivity.getNetworkCapabilities(net);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                preferred = net;
                break;
            }
            if (preferred == null) preferred = net;
        }
        try {
            connectivity.bindProcessToNetwork(preferred);
        } catch (RuntimeException ignored) { }
    }

    private void registerNetworkMonitor() {
        connectivity = getSystemService(ConnectivityManager.class);
        if (connectivity == null) return;
        for (Network net : connectivity.getAllNetworks()) {
            NetworkCapabilities caps = connectivity.getNetworkCapabilities(net);
            if (caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
                String sig = ipv4Signature(connectivity.getLinkProperties(net));
                if (!sig.isEmpty()) networkAddresses.put(net, sig);
            }
        }
        bindPreferredNetwork();
        NetworkRequest request = new NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                LinkProperties props = connectivity.getLinkProperties(network);
                String sig = ipv4Signature(props);
                String previous = networkAddresses.put(network, sig);
                if (previous == null || networkNeedsRestart) {
                    networkNeedsRestart = true;
                    scheduleNetworkRestart();
                }
            }
            @Override public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
                String sig = ipv4Signature(linkProperties);
                String previous = networkAddresses.put(network, sig);
                if (!sig.equals(previous) || networkNeedsRestart) {
                    if (sig.contains("|")) networkNeedsRestart = false;
                    scheduleNetworkRestart();
                }
            }
            @Override public void onLost(Network network) {
                networkAddresses.remove(network);
                networkNeedsRestart = true;
                main.post(() -> handleNetworkLost());
            }
        };
        try {
            connectivity.registerNetworkCallback(request, networkCallback);
        } catch (RuntimeException error) {
            android.util.Log.w("AbismoNet", "No se pudo registrar NetworkCallback", error);
        }
    }

    private void handleNetworkLost() {
        if (closed) return;
        bindPreferredNetwork();
        if (networkAddresses.isEmpty()) {
            main.removeCallbacks(networkRestart);
            senderGeneration++;
            starting = false;
            streaming = false;
            if (!executor.isShutdown()) executor.execute(() -> { if (ndi != null) ndi.close(); });
            if (wanted) message = "Sin red local · esperando reconexión NDI…\n" + config.sourceName();
            publish(true);
        } else {
            scheduleNetworkRestart();
        }
    }

    private void scheduleNetworkRestart() {
        main.removeCallbacks(networkRestart);
        main.postDelayed(networkRestart, 450);
    }

    private void restartSenderForNetwork() {
        if (closed || !wanted || !cameraReady) return;
        boolean hasIpv4 = false;
        if (connectivity != null) {
            for (Network net : new ArrayList<>(networkAddresses.keySet())) {
                LinkProperties props = connectivity.getLinkProperties(net);
                if (props != null) {
                    String sig = ipv4Signature(props);
                    networkAddresses.put(net, sig);
                    if (sig.contains("|")) hasIpv4 = true;
                }
            }
        }
        bindPreferredNetwork();
        if (!hasIpv4 && !networkAddresses.isEmpty()) {
            message = "Esperando IP local para NDI…\n" + config.sourceName();
            publish(true);
            main.removeCallbacks(networkRestart);
            main.postDelayed(networkRestart, 1000);
            return;
        }
        if (networkAddresses.isEmpty()) return;
        networkNeedsRestart = false;
        if (multicastLock != null) {
            try {
                if (multicastLock.isHeld()) multicastLock.release();
                multicastLock.acquire();
            } catch (RuntimeException ignored) { }
        }
        nsdManager = (NsdManager)getSystemService(NSD_SERVICE);
        senderGeneration++;
        starting = false;
        streaming = false;
        if (!executor.isShutdown()) executor.execute(() -> { if (ndi != null) ndi.close(); });
        startSender();
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (STOP.equals(intent == null ? null : intent.getAction())) {
            wanted = false;
            stopSender();
            return START_NOT_STICKY;
        }
        if (closed) return START_NOT_STICKY;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { stopSelf(); return START_NOT_STICKY; }
        try {
            if (Build.VERSION.SDK_INT >= 30) startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
            else startForeground(1, notification());
        } catch (RuntimeException error) {
            android.util.Log.w("AbismoService", "No se pudo elevar el servicio a primer plano", error);
        }
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "abismoCam:transmission");
            wakeLock.acquire();
            WifiManager wifi = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifi != null) {
                int wifiMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                    : WifiManager.WIFI_MODE_FULL_HIGH_PERF;
                wifiLock = wifi.createWifiLock(wifiMode, "abismoCam:stream");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
                multicastLock = wifi.createMulticastLock("abismoCam:discovery");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            }
        }
        lifecycle.setCurrentState(Lifecycle.State.RESUMED);
        if (provider == null && !cameraReady) bindCamera();
        return START_NOT_STICKY;
    }

    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, TransmissionService.class).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_app)
            .setContentTitle(streaming ? "abismoCam · NDI transmitiendo" : "abismoCam · cámara activa")
            .setContentText(config.sourceName() + (wanted ? "" : " · NDI detenido"))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null, "Detener NDI", stop).build()).build();
    }
    private void publish(boolean notification) {
        if (closed) return;
        if (listener != null) listener.changed();
        if (notification) getSystemService(NotificationManager.class).notify(1, notification());
    }
    @Override public IBinder onBind(Intent intent) { return binder; }

    void attach(Preview.SurfaceProvider surface, Listener listener) {
        this.surface = surface; this.listener = listener;
        if (preview != null) preview.setSurfaceProvider(surface);
        publish(false);
    }
    void detach() {
        listener = null; surface = null; snapshot.set(null);
        if (preview != null) preview.setSurfaceProvider(null);
    }
    void capture(boolean mirrored, PhotoCallback callback) { snapshot.set(new Snapshot(mirrored, callback)); }
    void cancelCapture() { snapshot.set(null); }

    void logCaptureTimeout() {
        android.util.Log.w("AbismoCapture", "frames=" + frames + " analysis=" + lastAnalysisMs
            + " send=" + lastSendStartMs + "/" + lastSendEndMs + " now=" + SystemClock.elapsedRealtime()
            + " stack=" + (analysisThread == null ? "none" : java.util.Arrays.toString(analysisThread.getStackTrace())));
    }

    void configure(Config next, boolean rebind, boolean restart) {
        config = Config.load(this);
        if (rebind) { stopSender(); bindCamera(); }
        else if (restart) { boolean resume = streaming || starting; stopSender(); if (resume && wanted) startSender(); }
        publish(true);
    }
    void setWanted(boolean value) { wanted = value; if (value) startSender(); else stopSender(); }

    private void bindCamera() {
        if (closed) return;
        final int generation = ++cameraGeneration;
        cameraReady = false;
        var future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (closed || generation != cameraGeneration) return;
            try {
                provider = future.get(); provider.unbindAll();
                ResolutionSelector resolution = new ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(new ResolutionStrategy(new Size(config.height, config.width), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build();
                preview = new Preview.Builder().setResolutionSelector(resolution).setTargetRotation(Surface.ROTATION_0)
                    .setTargetFrameRate(new Range<>(config.fps, config.fps)).build();
                if (surface != null) preview.setSurfaceProvider(surface);
                ImageAnalysis analysis = new ImageAnalysis.Builder().setResolutionSelector(resolution).setTargetRotation(Surface.ROTATION_0)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).setOutputImageRotationEnabled(true)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                analysis.setAnalyzer(executor, this::analyze);
                UseCaseGroup group = new UseCaseGroup.Builder().setViewPort(new ViewPort.Builder(new Rational(9, 16), Surface.ROTATION_0)
                    .setScaleType(ViewPort.FILL_CENTER).build()).addUseCase(preview).addUseCase(analysis).build();
                provider.bindToLifecycle(this, config.frontCamera ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA, group);
                cameraReady = true;
                cameraMessage = (config.frontCamera ? "Frontal" : "Trasera") + " · sin audio";
                if (wanted && !streaming && !starting) startSender();
                publish(false);
            } catch (Exception error) { cameraMessage = "Error de cámara: " + error.getMessage(); publish(false); }
        }, ContextCompat.getMainExecutor(this));
    }

    void startSender() {
        if (closed || starting || streaming || !cameraReady) return;
        starting = true;
        int generation = ++senderGeneration;
        String name = config.sourceName(); int fps = config.fps; boolean multicast = config.ndiMulticast;
        bindPreferredNetwork();
        message = "Iniciando NDI…"; publish(true);
        executor.execute(() -> {
            String failure = null;
            try { if (ndi == null) ndi = new NdiSender(); ndi.start(name, fps, new File(getFilesDir(), "ndi"), multicast); }
            catch (RuntimeException | LinkageError error) { failure = error.toString(); }
            String result = failure;
            main.post(() -> {
                if (closed || generation != senderGeneration) return;
                starting = false;
                if (result != null) { stopSender(); message = "NDI no disponible · " + result; publish(true); return; }
                streaming = true; lastSent = 0; statsStart = SystemClock.elapsedRealtime(); frameCount = 0;
                message = "● NDI activo · " + (multicast ? "Multicast habilitado" : "Unicast") + "\n" + name;
                publish(true);
            });
        });
    }
    void stopSender() {
        senderGeneration++; starting = false; streaming = false;
        if (!executor.isShutdown()) executor.execute(() -> { if (ndi != null) ndi.stop(); });
        message = "NDI detenido"; publish(true);
    }

    private void analyze(ImageProxy image) {
        analysisThread = Thread.currentThread(); lastAnalysisMs = SystemClock.elapsedRealtime();
        try {
            Snapshot request = snapshot.getAndSet(null);
            long now = System.nanoTime();
            Config current = config;
            boolean send = streaming && now - lastSent >= (long)(1_000_000_000.0 / current.fps * 0.9);
            if (!send && request == null) return;
            var plane = image.getPlanes()[0];
            if (plane.getPixelStride() != 4) throw new IllegalStateException("La cámara no produjo RGBA");
            ByteBuffer pixels = scaler.scale(plane.getBuffer(), image.getWidth(), image.getHeight(), plane.getRowStride(),
                image.getCropRect(), current.width, current.height, current.invertVertical);
            if (request != null) {
                try {
                    Bitmap bitmap = Bitmap.createBitmap(current.width, current.height, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(pixels.duplicate());
                    if (request.mirrored) {
                        Matrix mirror = new Matrix(); mirror.setScale(-1, 1);
                        Bitmap mirrored = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), mirror, false);
                        bitmap.recycle(); bitmap = mirrored;
                    }
                    Bitmap photo = bitmap;
                    main.post(() -> request.callback.ready(photo));
                } catch (RuntimeException error) { main.post(() -> request.callback.failed(error)); }
            }
            if (!send || ndi == null) return;
            lastSent = now;
            lastSendStartMs = SystemClock.elapsedRealtime();
            ndi.send(pixels, current.width, current.height, current.width * 4);
            lastSendEndMs = SystemClock.elapsedRealtime();
            consecutiveErrors = 0;
            frames++; frameCount++;
            long nowMs = SystemClock.elapsedRealtime();
            long elapsed = nowMs - statsStart;
            if (elapsed >= 1000) {
                if (nowMs - lastConnectionsCheckMs >= 3000) {
                    lastConnections = ndi.connections();
                    lastConnectionsCheckMs = nowMs;
                }
                String status = String.format(Locale.US, "%d × %d · %.1f fps · %d receptor(es)", current.width, current.height,
                    frameCount * 1000.0 / elapsed, lastConnections);
                main.post(() -> cameraMessage = status);
                frameCount = 0; statsStart = nowMs;
            }
            main.post(() -> publish(false));
        } catch (LinkageError error) {
            streaming = false;
            main.post(() -> { stopSender(); message = "Error de transmisión · " + error.getMessage(); publish(true); });
        } catch (RuntimeException error) {
            android.util.Log.w("AbismoNdi", "Error transitorio en cuadro NDI", error);
            if (++consecutiveErrors >= 10) {
                consecutiveErrors = 0;
                streaming = false;
                main.post(() -> {
                    stopSender();
                    if (wanted && !closed) {
                        message = "Reconectando NDI…";
                        publish(true);
                        main.postDelayed(this::startSender, 500);
                    } else {
                        message = "Error de transmisión · " + error.getMessage();
                        publish(true);
                    }
                });
            }
        } finally { image.close(); }
    }

    @Override public void onTaskRemoved(Intent rootIntent) { shutdown(); stopSelf(); }
    @Override public void onDestroy() { shutdown(); super.onDestroy(); }
    private void shutdown() {
        if (closed) return;
        closed = true; cameraGeneration++; senderGeneration++;
        main.removeCallbacks(networkRestart);
        if (connectivity != null) {
            if (networkCallback != null) {
                try { connectivity.unregisterNetworkCallback(networkCallback); } catch (RuntimeException ignored) { }
            }
            try { connectivity.bindProcessToNetwork(null); } catch (RuntimeException ignored) { }
        }
        streaming = false; starting = false; cameraReady = false; wanted = false;
        snapshot.set(null); message = "NDI detenido";
        if (listener != null) listener.changed(); listener = null; surface = null;
        lifecycle.setCurrentState(Lifecycle.State.DESTROYED);
        if (provider != null) provider.unbindAll();
        executor.execute(() -> { if (ndi != null) ndi.close(); });
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
    }
}
