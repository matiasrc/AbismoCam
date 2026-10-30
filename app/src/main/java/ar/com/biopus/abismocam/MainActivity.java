package ar.com.biopus.abismocam;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends ComponentActivity {
    private static final int BG = 0xff090909, PANEL = 0xff232323, ACCENT = 0xffffffff, TEXT = 0xffffffff, MUTED = 0xffbdbdbd;
    private final Handler main = new Handler(Looper.getMainLooper());

    private final OscRepeater oscRepeater = new OscRepeater(this::sendOscPacket);
    private final ExecutorService photoExecutor = Executors.newSingleThreadExecutor();
    private final Osc.Hold hold = new Osc.Hold(this::queueOsc);
    private final CaptureSequence sequence = new CaptureSequence(new CaptureSequence.Listener() {
        @Override public void onOsc(int value) {
            if (value == 1) hold.press(config.targets(), config.address);
            else hold.release();
        }
        @Override public void onCount(int seconds) {
            if (countdown == null) return;
            countdown.setText(seconds > 0 ? Integer.toString(seconds) : "");
            countdown.setVisibility(seconds > 0 ? View.VISIBLE : View.GONE);
            if (seconds > 0) {
                countdown.setScaleX(1.12f); countdown.setScaleY(1.12f);
                countdown.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
                captureStatus.setText("Prepará tu foto");
            }
        }
        @Override public void onCapture() {
            android.util.Log.d("AbismoCapture", "request " + captureGeneration);
            captureStatus.setText("Capturando…");
            final int generation = captureGeneration;
            if (transmission != null) transmission.capture(config.frontCamera, new TransmissionService.PhotoCallback() {
                @Override public void ready(Bitmap photo) { acceptSnapshot(generation, photo); }
                @Override public void failed(RuntimeException error) { captureFailed(generation, error); }
            });
        }
        @Override public void onLive() { restoreLiveView(); }
        @Override public void onTimeout() {
            android.util.Log.w("AbismoCapture", "timeout " + captureGeneration);
            if (transmission != null) transmission.logCaptureTimeout();
            captureGeneration++;
            if (transmission != null) transmission.cancelCapture();
            toast("No llegó un cuadro de cámara. Volvé a intentarlo.");
        }
    });
    private final Runnable countdownTick = new Runnable() {
        @Override public void run() {
            sequence.tick(SystemClock.elapsedRealtime());
            if (sequence.isBusy()) main.postDelayed(this, 20);
        }
    };
    private Config config;
    private NdiSender ndi;
    private PreviewView previewView;
    private TextView cameraStatus, oscStatus, ndiStatus, countdown, captureStatus, timerLabel;
    private ShutterButton trigger;
    private Button streamButton;
    private TextView ndiIndicator;
    private FrameLayout cameraScreen, cameraControls;
    private LinearLayout settingsPanel;
    private boolean ndiWanted = true;
    private final Runnable openSettings = () -> { if (this.resumed && !sequence.isBusy()) showSettings(); };
    private ImageButton settingsButton;
    private ImageView frozenFrame, lastPhoto;
    private View flash;
    private Uri lastPhotoUri;
    private Bitmap lastThumbnail;
    private volatile boolean streaming;
    private boolean starting, resumed, settingsVisible, cameraReady;
    private int captureGeneration;
    volatile long ndiFramesSent;
    private String oscMessage = "Configurá los destinos para enviar OSC.";
    private String ndiMessage = "NDI detenido";
    private final ActivityResultLauncher<String> storagePermission = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted && resumed && !settingsVisible) beginCountdown();
            else if (!granted) toast("Hace falta permiso para guardar la foto en este Android.");
        });
    private final ActivityResultLauncher<String> cameraPermission = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted && !settingsVisible) bindCamera();
            else if (!granted && cameraStatus != null) cameraStatus.setText("Cámara sin permiso · tocá aquí para habilitarla");
        });

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        config = Config.load(this);
        config.save(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (settingsVisible) showCamera();
                else { cancelCapture(); moveTaskToBack(true); }
            }
        });
        showCamera();
    }

    private void applyOrientation() {
        boolean inv = config != null && config.invertVertical;
        float rot = inv ? 180f : 0f;
        if (cameraControls != null) {
            cameraControls.setRotation(rot);
            ViewCompat.requestApplyInsets(cameraControls);
        }
        if (settingsPanel != null) {
            settingsPanel.setRotation(rot);
            ViewCompat.requestApplyInsets(settingsPanel);
        }
        if (lastPhoto != null) {
            lastPhoto.setRotation(rot);
        }
    }

    private LinearLayout root() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(20), dp(12), dp(20), dp(12));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            boolean inv = config != null && config.invertVertical;
            v.setPadding(dp(20) + (inv ? bars.right : bars.left), dp(12) + (inv ? bars.bottom : bars.top),
                dp(20) + (inv ? bars.left : bars.right), dp(12) + (inv ? bars.top : bars.bottom));
            return insets;
        });
        settingsPanel = root;
        cameraScreen.addView(root, new FrameLayout.LayoutParams(-1, -1));
        ViewCompat.requestApplyInsets(root);
        return root;
    }

    private void showCamera() {
        if (settingsPanel != null) {
            cameraScreen.removeView(settingsPanel);
            settingsPanel = null;
            streamButton = null;
            settingsVisible = false;
            applyOrientation();
            updateCountdownLabels();
            return;
        }
        settingsVisible = false;
        FrameLayout screen = new FrameLayout(this);
        cameraScreen = screen;
        screen.setBackgroundColor(Color.BLACK);
        setContentView(screen);

        previewView = new PreviewView(this);
        previewView.setId(R.id.camera_viewport);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        screen.addView(previewView, new FrameLayout.LayoutParams(-1, -1));

        frozenFrame = new ImageView(this);
        frozenFrame.setId(R.id.frozen_frame);
        frozenFrame.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frozenFrame.setVisibility(View.GONE);
        screen.addView(frozenFrame, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout controls = new FrameLayout(this);
        cameraControls = controls;
        screen.addView(controls, new FrameLayout.LayoutParams(-1, -1));
        ViewCompat.setOnApplyWindowInsetsListener(controls, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            boolean inv = config != null && config.invertVertical;
            view.setPadding(inv ? bars.right : bars.left, inv ? bars.bottom : bars.top,
                inv ? bars.left : bars.right, inv ? bars.top : bars.bottom);
            return insets;
        });

        View topShade = new View(this);
        topShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xaa000000, Color.TRANSPARENT}));
        controls.addView(topShade, new FrameLayout.LayoutParams(-1, dp(180), Gravity.TOP));

        View bottomShade = new View(this);
        bottomShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[] {0xee000000, Color.TRANSPARENT}));
        controls.addView(bottomShade, new FrameLayout.LayoutParams(-1, dp(320), Gravity.BOTTOM));

        LinearLayout header = row();
        ndiIndicator = label("○ NDI", 12, MUTED);
        ndiIndicator.setId(R.id.ndi_indicator);
        ndiIndicator.setGravity(Gravity.CENTER);
        ndiIndicator.setContentDescription(ndiMessage);
        header.addView(ndiIndicator, new LinearLayout.LayoutParams(dp(94), dp(48)));
        TextView title = label("EL ABISMO", 12, TEXT);
        title.setLetterSpacing(0.2f); title.setGravity(Gravity.CENTER); title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        settingsButton = new ImageButton(this);
        settingsButton.setId(R.id.settings);
        settingsButton.setImageResource(R.drawable.ic_settings);
        settingsButton.setContentDescription("Ajustes: mantener pulsado 3 segundos");
        settingsButton.setPadding(dp(12), dp(12), dp(12), dp(12));
        settingsButton.setBackground(background(0x33000000, 24));
        settingsButton.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && v.isEnabled()) {
                main.removeCallbacks(openSettings);
                main.postDelayed(openSettings, 3000);
                v.setPressed(true);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL
                || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN
                || (event.getActionMasked() == MotionEvent.ACTION_MOVE && (event.getX() < 0 || event.getY() < 0 || event.getX() > v.getWidth() || event.getY() > v.getHeight()))) {
                main.removeCallbacks(openSettings); v.setPressed(false);
            }
            return true;
        });
        FrameLayout settingsSlot = new FrameLayout(this);
        settingsSlot.addView(settingsButton, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END));
        header.addView(settingsSlot, new LinearLayout.LayoutParams(dp(94), dp(48)));
        header.setId(R.id.camera_header);
        header.setPadding(dp(18), dp(6), dp(18), dp(6));
        controls.addView(header, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        countdown = label("", 136, Color.WHITE);
        countdown.setId(R.id.countdown);
        countdown.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        countdown.setGravity(Gravity.CENTER);
        countdown.setShadowLayer(dp(16), 0, dp(2), 0x77000000);
        countdown.setVisibility(View.GONE);
        countdown.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        controls.addView(countdown, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        LinearLayout footer = column();
        footer.setId(R.id.camera_footer);
        footer.setPadding(dp(18), 0, dp(18), dp(14));
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        cameraStatus = label("Iniciando cámara…", 10, MUTED);
        cameraStatus.setGravity(Gravity.CENTER);
        cameraStatus.setShadowLayer(dp(3), 0, 1, Color.BLACK);
        cameraStatus.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            } else if (!cameraReady) bindCamera();
        });
        captureStatus = label(idleCaptureText(), 13, TEXT);
        captureStatus.setId(R.id.capture_status);
        captureStatus.setGravity(Gravity.CENTER);
        captureStatus.setPadding(0, dp(12), 0, dp(6));
        footer.addView(captureStatus);
        FrameLayout shutterRow = new FrameLayout(this);
        trigger = new ShutterButton(this);
        trigger.setId(R.id.shutter);
        trigger.setOnClickListener(v -> beginCountdown());
        shutterRow.addView(trigger, new FrameLayout.LayoutParams(dp(92), dp(92), Gravity.CENTER));
        lastPhoto = new ImageView(this);
        lastPhoto.setContentDescription("Abrir última foto guardada");
        lastPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        lastPhoto.setBackground(background(0x55ffffff, 10)); lastPhoto.setClipToOutline(true);
        if (lastThumbnail != null) lastPhoto.setImageBitmap(lastThumbnail);
        else lastPhoto.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams thumbLayout = new FrameLayout.LayoutParams(dp(46), dp(56), Gravity.START | Gravity.CENTER_VERTICAL);
        thumbLayout.leftMargin = dp(22);
        shutterRow.addView(lastPhoto, thumbLayout);
        lastPhoto.setOnClickListener(v -> {
            if (lastPhotoUri == null || sequence.isBusy()) return;
            try { startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(lastPhotoUri, "image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
            catch (android.content.ActivityNotFoundException unavailable) { toast("Foto guardada en Pictures/AbismoCam."); }
        });
        timerLabel = label(config.countdownSeconds + " s", 14, TEXT); timerLabel.setGravity(Gravity.CENTER);
        updateCountdownLabels();
        FrameLayout.LayoutParams timerLayout = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END | Gravity.CENTER_VERTICAL);
        timerLayout.rightMargin = dp(22); shutterRow.addView(timerLabel, timerLayout);
        footer.addView(shutterRow, new LinearLayout.LayoutParams(-1, dp(106)));
        TextView mode = label("FOTO", 11, TEXT); mode.setGravity(Gravity.CENTER); mode.setLetterSpacing(0.2f); mode.setTypeface(null, Typeface.BOLD);
        footer.addView(mode);
        cameraStatus.setPadding(0, dp(12), 0, 0);
        footer.addView(cameraStatus);
        ndiStatus = label(ndiMessage, 10, MUTED);
        ndiStatus.setGravity(Gravity.CENTER);
        footer.addView(ndiStatus);
        oscStatus = label(targetSummary(), 10, MUTED);
        oscStatus.setGravity(Gravity.CENTER); oscStatus.setMaxLines(2);
        footer.addView(oscStatus);
        TextView ndiLink = label("NDI® · ndi.video", 10, MUTED);
        ndiLink.setGravity(Gravity.CENTER); ndiLink.setPadding(0, dp(4), 0, 0);
        footer.addView(ndiLink);
        controls.addView(footer, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        flash = new View(this); flash.setId(R.id.screen_flash); flash.setBackgroundColor(Color.WHITE); flash.setVisibility(View.GONE);
        screen.addView(flash, new FrameLayout.LayoutParams(-1, -1));
        applyOrientation();
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) bindCamera();
        else cameraPermission.launch(Manifest.permission.CAMERA);
    }

    private TransmissionService transmission;
    private boolean serviceBound;
    private final android.content.ServiceConnection serviceConnection = new android.content.ServiceConnection() {
        @Override public void onServiceConnected(android.content.ComponentName name, android.os.IBinder binder) {
            transmission = ((TransmissionService.LocalBinder)binder).service();
            if (resumed) transmission.attach(previewView.getSurfaceProvider(), MainActivity.this::refreshTransmission);
            refreshTransmission();
            if (Build.VERSION.SDK_INT >= 33 && resumed && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
        @Override public void onServiceDisconnected(android.content.ComponentName name) {
            transmission = null; streaming = false; starting = false; cameraReady = false;
            setNdiMessage("NDI detenido");
        }
    };
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(), granted -> { });

    private void bindCamera() {
        if (!resumed || isDestroyed() || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        if (transmission != null) {
            transmission.configure(config, true, false);
            transmission.attach(previewView.getSurfaceProvider(), this::refreshTransmission);
            return;
        }
        if (serviceBound) return;
        try {
            Intent service = new Intent(this, TransmissionService.class);
            ContextCompat.startForegroundService(this, service);
            serviceBound = bindService(service, serviceConnection, BIND_AUTO_CREATE);
        } catch (RuntimeException error) { cameraStatus.setText("No se pudo iniciar la cámara: " + error.getMessage()); }
    }

    private static void updateText(TextView view, String text) {
        if (view == null) return;
        String safe = text == null ? "" : text;
        CharSequence current = view.getText();
        if (current == null || !safe.contentEquals(current)) view.setText(safe);
    }

    private void refreshTransmission() {
        if (transmission == null || isDestroyed()) return;
        cameraReady = transmission.cameraReady; streaming = transmission.streaming; starting = transmission.starting;
        ndiWanted = transmission.wanted; ndiFramesSent = transmission.frames; ndi = transmission.ndi;
        setNdiMessage(transmission.message);
        if (cameraStatus != null && !settingsVisible) updateText(cameraStatus, transmission.cameraMessage);
        if (streamButton != null) {
            streamButton.setEnabled(!starting);
            updateText(streamButton, streaming ? "Detener NDI" : "Iniciar NDI");
        }
    }

    private void startNdi() {
        if (transmission != null) { transmission.configure(config, false, false); transmission.setWanted(true); refreshTransmission(); }
    }
    private void stopNdi() {
        if (transmission != null) { transmission.stopSender(); refreshTransmission(); }
    }

    private void setNdiMessage(String message) {
        String safe = message == null ? "" : message;
        ndiMessage = safe;
        updateText(ndiStatus, safe);
        if (ndiIndicator != null) {
            updateText(ndiIndicator, streaming ? "● NDI" : "○ NDI");
            CharSequence currentDesc = ndiIndicator.getContentDescription();
            if (currentDesc == null || !safe.contentEquals(currentDesc)) ndiIndicator.setContentDescription(safe);
        }
    }

    private void queueOsc(List<Osc.Destination> targets, String address, int value) {
        if (config.countdownSeconds == 0) {
            // El 0 de la captura inmediata ya queda programado después de los diez 1.
            if (value == 1) oscRepeater.sendPulse(targets, address, config.oscIntervalMs);
        } else oscRepeater.send(targets, address, value, config.oscIntervalMs);
    }

    private void sendOscPacket(List<Osc.Destination> targets, String address, int value) {
            Osc.Result result = Osc.send(targets, address, value);
            String report = address + " " + value + " → " + result.sent + " destino(s) · UDP sin confirmación";
            if (!result.errors.isEmpty()) report += "\nError: " + String.join("; ", result.errors);
            String message = report;
            main.post(() -> {
                if (isDestroyed()) return;
                oscMessage = message;
                if (!settingsVisible && oscStatus != null) oscStatus.setText(message);
            });
    }

    private void beginCountdown() {
        if (sequence.isBusy() || settingsVisible || !resumed) return;
        if (!cameraReady) { toast("Esperá a que la cámara esté disponible."); return; }
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        captureGeneration++;
        sequence.start(SystemClock.elapsedRealtime(), config.countdownSeconds);
        trigger.setEnabled(false);
        settingsButton.setEnabled(false);
        // No tocar la cámara ni el emisor. Solo cambia la capa de presentación.
        main.removeCallbacks(countdownTick);
        main.post(countdownTick);
    }

    private void acceptSnapshot(int generation, Bitmap photo) {
        android.util.Log.d("AbismoCapture", "ready " + generation + " current=" + captureGeneration + " state=" + sequence.state());
        if (generation != captureGeneration || !resumed || settingsVisible || isDestroyed()) { photo.recycle(); return; }
        if (!sequence.frameReady(SystemClock.elapsedRealtime())) return;
        frozenFrame.setImageBitmap(photo);
        frozenFrame.setVisibility(View.VISIBLE);
        captureStatus.setText("Guardando foto…");
        flash.animate().cancel();
        flash.setAlpha(1f); flash.setVisibility(View.VISIBLE);
        flash.animate().alpha(0f).setDuration(CaptureSequence.FLASH_MS).withEndAction(() -> flash.setVisibility(View.GONE)).start();
        // Persistencia en otro executor: ni el JPEG ni MediaStore bloquean NDI.
        android.content.Context app = getApplicationContext();
        photoExecutor.execute(() -> {
            try {
                Uri saved = PhotoStore.save(app, photo);
                Bitmap thumb = Bitmap.createScaledBitmap(photo, 120, Math.max(1, photo.getHeight() * 120 / photo.getWidth()), true);
                main.post(() -> {
                    if (isDestroyed()) return;
                    lastPhotoUri = saved; lastThumbnail = thumb;
                    if (!settingsVisible) {
                        lastPhoto.setImageBitmap(thumb); lastPhoto.setVisibility(View.VISIBLE);
                        if (generation == captureGeneration) captureStatus.setText("Foto guardada");
                    }
                });
            } catch (java.io.IOException | RuntimeException failure) {
                main.post(() -> { if (!isDestroyed()) toast("No se pudo guardar la foto: " + failure.getMessage()); });
            }
        });
    }

    private void captureFailed(int generation, RuntimeException failure) {
        android.util.Log.e("AbismoCapture", "No se pudo copiar el cuadro", failure);
        if (generation != captureGeneration || isDestroyed()) return;
        cancelCapture();
        toast("No se pudo capturar la foto: " + failure.getMessage());
    }

    private void restoreLiveView() {
        if (transmission != null) transmission.cancelCapture();
        if (frozenFrame != null) { frozenFrame.setVisibility(View.GONE); frozenFrame.setImageDrawable(null); }
        if (flash != null) { flash.animate().cancel(); flash.setVisibility(View.GONE); }
        if (trigger != null) trigger.setEnabled(true);
        if (settingsButton != null) settingsButton.setEnabled(true);
        updateCountdownLabels();
    }

    private String idleCaptureText() {
        return config.countdownSeconds == 0 ? "Soltá el botón para sacar la foto" : "Soltá el botón para iniciar · " + config.countdownSeconds + " s";
    }

    private void updateCountdownLabels() {
        if (captureStatus != null) captureStatus.setText(idleCaptureText());
        if (timerLabel != null) timerLabel.setText(config.countdownSeconds + " s");
        if (trigger != null) trigger.setContentDescription(config.countdownSeconds == 0 ? "Capturar foto sin cuenta regresiva"
            : "Capturar foto con cuenta regresiva de " + config.countdownSeconds + " segundos");
    }

    private void cancelCapture() {
        captureGeneration++;
        if (transmission != null) transmission.cancelCapture();
        main.removeCallbacks(countdownTick);
        sequence.cancel();
        if (trigger != null) trigger.cancelTouch();
    }

    private String targetSummary() {
        if (config.broadcast) return "BROADCAST · " + (config.broadcastIp.isEmpty() ? "sin configurar" : config.broadcastIp + ":" + config.broadcastPort);
        long count = config.destinations.stream().filter(d -> d.enabled).count();
        return "OSC · " + count + " destino(s) habilitado(s) · " + config.address;
    }

    private void showSettings() {
        if (settingsVisible || sequence.isBusy()) return;
        main.removeCallbacks(openSettings);
        settingsButton.setPressed(false);
        cancelCapture();
        settingsVisible = true;
        LinearLayout root = root();
        applyOrientation();
        LinearLayout top = row();
        top.addView(label("Configuración", 24, TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        Button back = button("Volver", false);
        back.setOnClickListener(v -> showCamera());
        top.addView(back);
        root.addView(top);
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = column();
        scroll.addView(content);
        content.addView(section("01 / CÁMARA"));
        TextView sourceLabel = label("Fuente NDI: " + config.sourceName(), 14, TEXT);
        sourceLabel.setId(R.id.ndi_source_name);
        content.addView(sourceLabel);
        EditText sourceIdentifier = input(content, "Identificador del teléfono", config.sourceIdentifier, false);
        sourceIdentifier.setId(R.id.ndi_source_identifier);
        sourceIdentifier.setHint("Ejemplo: Li");
        content.addView(label("Elegí uno distinto para cada teléfono. Se aplica al guardar, conservando el número. Sin identificador: abismoCam_0001.", 12, MUTED));
        Button renewSource = button("Renovar fuente NDI", false);
        renewSource.setId(R.id.ndi_renew_source);
        content.addView(renewSource, spaced(-1, 56));
        content.addView(label("Renovar incrementa y guarda el número inmediatamente, usando el identificador ya guardado. Si NDI está activo, se reinicia con el nombre nuevo; si está detenido, permanece detenido. Seleccioná la nueva fuente en los receptores.", 12, MUTED));
        renewSource.setOnClickListener(v -> {
            boolean restart = streaming || starting;
            stopNdi();
            config.renewSource();
            config.save(this);
            if (transmission != null) transmission.configure(config, false, false);
            sourceLabel.setText("Fuente NDI: " + config.sourceName());
            if (restart && ndiWanted && resumed) startNdi();
        });
        streamButton = button(streaming ? "Detener NDI" : "Iniciar NDI", false);
        streamButton.setId(R.id.ndi_toggle);
        streamButton.setEnabled(!starting);
        streamButton.setOnClickListener(v -> { if (transmission != null) { transmission.setWanted(!(streaming || starting)); refreshTransmission(); } });
        content.addView(streamButton);
        content.addView(label("Transporte NDI", 13, TEXT));
        android.widget.Spinner transport = new android.widget.Spinner(this);
        transport.setId(R.id.ndi_transport);
        transport.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[] {"Unicast", "Multicast"}));
        transport.setSelection(config.ndiMulticast ? 1 : 0);
        content.addView(transport);
        content.addView(label("Al guardar un cambio de transporte se reinicia NDI: los receptores pueden mostrar negro o el último cuadro mientras reconectan. Multicast requiere una red compatible; cada receptor negocia el modo y puede seguir usando unicast.", 12, MUTED));
        CheckBox front = check("Usar cámara frontal", config.frontCamera);
        content.addView(front);
        CheckBox invertVertical = check("Rotar 180° (celular boca abajo)", config.invertVertical);
        content.addView(invertVertical);
        content.addView(label("Resolución", 13, TEXT));
        android.widget.Spinner resolution = new android.widget.Spinner(this);
        resolution.setId(R.id.resolution);
        resolution.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[] {"nHD · 640 × 360", "qHD · 960 × 540", "HD · 1280 × 720"}));
        resolution.setSelection(config.width == 360 ? 0 : config.width == 540 ? 1 : 2);
        content.addView(resolution);
        content.addView(label("Fotogramas por segundo", 13, TEXT));
        android.widget.Spinner fpsChoice = new android.widget.Spinner(this);
        fpsChoice.setId(R.id.fps);
        fpsChoice.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[] {"15 fps", "30 fps"}));
        fpsChoice.setSelection(config.fps == 15 ? 0 : 1);
        content.addView(fpsChoice);
        content.addView(label("Video vertical 9:16 (nHD 360×640, qHD 540×960, HD 720×1280). La vista previa, NDI y la foto comparten el mismo encuadre; las bandas negras no se transmiten ni se guardan. Cambiar cámara, resolución o FPS reinicia brevemente el video al guardar.", 12, MUTED));
        content.addView(label("Cuenta regresiva", 13, TEXT));
        android.widget.Spinner countdownChoice = new android.widget.Spinner(this);
        countdownChoice.setId(R.id.countdown_seconds);
        String[] countdownOptions = new String[16];
        for (int i = 0; i <= 15; i++) countdownOptions[i] = i == 0 ? "0 s · foto inmediata" : i + " s";
        countdownChoice.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, countdownOptions));
        countdownChoice.setSelection(config.countdownSeconds);
        content.addView(countdownChoice);
        content.addView(label("La cuenta empieza al soltar. Con 0 s se toma la foto directamente, sin mostrar números.", 12, MUTED));
        content.addView(section("02 / MENSAJE OSC"));
        EditText address = input(content, "Dirección OSC", config.address, false);
        content.addView(label("Intervalo entre repeticiones OSC", 13, TEXT));
        android.widget.Spinner oscInterval = new android.widget.Spinner(this);
        oscInterval.setId(R.id.osc_interval);
        oscInterval.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[] {"10 ms", "25 ms", "50 ms", "100 ms"}));
        oscInterval.setSelection(config.oscIntervalMs == 10 ? 0 : config.oscIntervalMs == 25 ? 1 : config.oscIntervalMs == 100 ? 3 : 2);
        content.addView(oscInterval);
        content.addView(label("Cada valor (1 y 0) se envía 10 veces. El primero sale inmediatamente. Configurá los receptores para actuar solo al cambiar el valor, sin repetir acciones por cada mensaje.", 12, MUTED));
        content.addView(label("OSC 1 al comenzar y OSC 0 al finalizar la cuenta. Con 0 s: diez 1 seguidos de diez 0, sin retrasar la foto. Flash y foto congelada 1 s solo en pantalla.", 13, ACCENT));
        content.addView(label("Las fotos se guardan en Pictures/AbismoCam. Frontal espejada en pantalla y foto; NDI siempre sin espejo.", 12, MUTED));
        content.addView(section("03 / DESTINOS"));
        RadioGroup mode = new RadioGroup(this);
        RadioButton individual = radio("Computadoras individuales", R.id.mode_individual);
        RadioButton broadcast = radio("Broadcast a la subred", R.id.mode_broadcast);
        mode.addView(individual); mode.addView(broadcast);
        mode.check(config.broadcast ? R.id.mode_broadcast : R.id.mode_individual);
        content.addView(mode);
        LinearLayout destinationBox = column();
        content.addView(destinationBox);
        List<Osc.Destination> draft = new ArrayList<>(config.destinations);
        refreshDestinations(destinationBox, draft);
        Button add = button("+ Agregar computadora", false);
        destinationBox.addView(add);
        add.setOnClickListener(v -> editDestination(draft, -1, () -> { refreshDestinations(destinationBox, draft); destinationBox.addView(add); }));
        LinearLayout broadcastBox = column();
        content.addView(broadcastBox);
        EditText ip = input(broadcastBox, "Dirección de broadcast", config.broadcastIp, false);
        ip.setHint("Ejemplo: 192.168.1.255");
        EditText port = input(broadcastBox, "Puerto OSC", Integer.toString(config.broadcastPort), true);
        Button detect = button("Detectar broadcast de Wi-Fi / Ethernet", false);
        broadcastBox.addView(detect);
        detect.setOnClickListener(v -> {
            String detected = detectBroadcast();
            if (detected == null) toast("No se encontró una subred IPv4 local. Ingresá el broadcast manualmente.");
            else ip.setText(detected);
        });
        broadcastBox.addView(label("Todos los receptores deben escuchar el mismo puerto. La red debe permitir broadcast.", 12, MUTED));
        mode.setOnCheckedChangeListener((group, checked) -> {
            destinationBox.setVisibility(checked == R.id.mode_individual ? View.VISIBLE : View.GONE);
            broadcastBox.setVisibility(checked == R.id.mode_broadcast ? View.VISIBLE : View.GONE);
        });
        destinationBox.setVisibility(config.broadcast ? View.GONE : View.VISIBLE);
        broadcastBox.setVisibility(config.broadcast ? View.VISIBLE : View.GONE);
        TextView error = label("", 13, 0xffffb4a9);
        content.addView(error);
        Button save = button("Guardar y volver a cámara", true);
        save.setId(R.id.save_settings);
        content.addView(save, spaced(-1, 56));
        save.setOnClickListener(v -> {
            try {
                String identifierText = SourceIdentity.identifier(sourceIdentifier.getText().toString());
                String addressText = address.getText().toString().trim();
                Osc.validateAddress(addressText);
                String ipText = ip.getText().toString().trim();
                boolean isBroadcast = mode.getCheckedRadioButtonId() == R.id.mode_broadcast;
                if (isBroadcast || !ipText.isEmpty()) Osc.ipv4(ipText);
                int portValue = parsePort(port);
                int width = new int[] {360, 540, 720}[resolution.getSelectedItemPosition()];
                int fps = fpsChoice.getSelectedItemPosition() == 0 ? 15 : 30;
                boolean reconfigure = config.width != width || config.fps != fps || config.frontCamera != front.isChecked() || config.invertVertical != invertVertical.isChecked();
                boolean transportChanged = config.ndiMulticast != (transport.getSelectedItemPosition() == 1);
                boolean sourceChanged = !config.sourceIdentifier.equals(identifierText);
                boolean restartSource = sourceChanged && (streaming || starting);
                if (reconfigure || transportChanged || sourceChanged) stopNdi();
                config.ndiMulticast = transport.getSelectedItemPosition() == 1;
                config.sourceIdentifier = identifierText; config.address = addressText;
                config.broadcast = isBroadcast; config.broadcastIp = ipText; config.broadcastPort = portValue;
                config.frontCamera = front.isChecked();
                config.invertVertical = invertVertical.isChecked();
                config.width = width;
                config.height = width * 16 / 9;
                config.fps = fps;
                config.countdownSeconds = countdownChoice.getSelectedItemPosition();
                config.oscIntervalMs = new int[] {10, 25, 50, 100}[oscInterval.getSelectedItemPosition()];
                config.destinations.clear(); config.destinations.addAll(draft);
                config.save(this);
                if (transmission != null) transmission.configure(config, false, false);
                oscMessage = "Configuración guardada. Listo para probar OSC.";
                showCamera();
                oscStatus.setText(targetSummary());
                if (reconfigure) bindCamera();
                else if ((transportChanged || restartSource) && ndiWanted) startNdi();
            } catch (IllegalArgumentException invalid) { error.setText(invalid.getMessage()); }
        });
        content.addView(section("ACERCA DE ESTA PRUEBA"));
        content.addView(label("abismoCam · 0.9.0 · Lisandro Peralta\nNDI automático al abrir. Continúa durante el conteo, la foto y los ajustes. Continúa en segundo plano y con pantalla bloqueada. Se detiene al quitar la app de recientes. OSC no tiene confirmación de recepción.\nNDI® is a registered trademark of Vizrt NDI AB.", 12, MUTED));
        Button ndiInfo = button("NDI® · ndi.video", false);
        ndiInfo.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://ndi.video"))));
        content.addView(ndiInfo);
    }

    private void refreshDestinations(LinearLayout container, List<Osc.Destination> draft) {
        container.removeAllViews();
        if (draft.isEmpty()) container.addView(label("Todavía no hay computadoras. Agregá la IP y el puerto de cada receptor.", 13, MUTED));
        for (int i = 0; i < draft.size(); i++) {
            final int index = i;
            Osc.Destination d = draft.get(i);
            LinearLayout row = row();
            CheckBox enabled = check(d.name + "\n" + d.ip + ":" + d.port, d.enabled);
            enabled.setOnCheckedChangeListener((v, checked) -> draft.set(index, new Osc.Destination(d.name, d.ip, d.port, checked)));
            row.addView(enabled, new LinearLayout.LayoutParams(0, -2, 1));
            Button edit = button("Editar", false);
            edit.setOnClickListener(v -> {
                View add = container.getChildAt(container.getChildCount() - 1);
                editDestination(draft, index, () -> { refreshDestinations(container, draft); container.addView(add); });
            });
            row.addView(edit);
            container.addView(row);
        }
    }

    private void editDestination(List<Osc.Destination> draft, int index, Runnable changed) {
        Osc.Destination existing = index < 0 ? null : draft.get(index);
        LinearLayout form = column(); form.setPadding(dp(24), dp(8), dp(24), dp(8));
        EditText name = input(form, "Nombre", existing == null ? "Computadora " + (draft.size() + 1) : existing.name, false);
        EditText ip = input(form, "IP de la computadora", existing == null ? "" : existing.ip, false);
        ip.setHint("192.168.1.20");
        EditText port = input(form, "Puerto OSC", existing == null ? "9000" : Integer.toString(existing.port), true);
        TextView error = label("", 12, 0xffffb4a9); form.addView(error);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(index < 0 ? "Agregar computadora" : "Editar computadora")
            .setView(form).setPositiveButton("Guardar", null).setNegativeButton("Cancelar", null);
        if (index >= 0) builder.setNeutralButton("Eliminar", (d, w) -> { draft.remove(index); changed.run(); });
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            if (config != null && config.invertVertical && dialog.getWindow() != null)
                dialog.getWindow().getDecorView().setRotation(180f);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    Osc.Destination next = new Osc.Destination(name.getText().toString(), ip.getText().toString().trim(), parsePort(port), existing == null || existing.enabled);
                    for (int j = 0; j < draft.size(); j++) if (j != index && draft.get(j).ip.equals(next.ip) && draft.get(j).port == next.port)
                        throw new IllegalArgumentException("Ese destino ya está en la lista.");
                    if (index < 0) draft.add(next); else draft.set(index, next);
                    changed.run(); dialog.dismiss();
                } catch (IllegalArgumentException invalid) { error.setText(invalid.getMessage()); }
            });
        });
        dialog.show();
    }

    private String detectBroadcast() {
        ConnectivityManager manager = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities caps = manager.getNetworkCapabilities(network);
            if (caps == null || (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) continue;
            LinkProperties properties = manager.getLinkProperties(network);
            if (properties == null) continue;
            for (LinkAddress link : properties.getLinkAddresses()) {
                if (!(link.getAddress() instanceof Inet4Address) || link.getPrefixLength() > 30 || link.getPrefixLength() < 1) continue;
                byte[] bytes = link.getAddress().getAddress();
                int value = ((bytes[0] & 255) << 24) | ((bytes[1] & 255) << 16) | ((bytes[2] & 255) << 8) | (bytes[3] & 255);
                int broadcast = value | (-1 >>> link.getPrefixLength());
                try { return InetAddress.getByAddress(new byte[] {(byte)(broadcast >>> 24), (byte)(broadcast >>> 16), (byte)(broadcast >>> 8), (byte)broadcast}).getHostAddress(); }
                catch (java.net.UnknownHostException impossible) { throw new AssertionError(impossible); }
            }
        }
        return null;
    }

    private int parsePort(EditText input) {
        try {
            int port = Integer.parseInt(input.getText().toString().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException error) { throw new IllegalArgumentException("El puerto debe estar entre 1 y 65535."); }
    }

    @Override protected void onResume() {
        super.onResume(); resumed = true;
        if (transmission != null) transmission.attach(previewView.getSurfaceProvider(), this::refreshTransmission);
        else if (config != null && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) bindCamera();
    }
    @Override protected void onPause() { main.removeCallbacks(openSettings); resumed = false; cancelCapture(); super.onPause(); }
    @Override protected void onStop() {
        if (transmission != null) transmission.detach();
        super.onStop();
    }
    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus); if (!focus) { main.removeCallbacks(openSettings); if (trigger != null) trigger.cancelTouch(); } }
    @Override protected void onDestroy() {
        cancelCapture();
        if (transmission != null) transmission.detach();
        if (serviceBound) { unbindService(serviceConnection); serviceBound = false; }
        transmission = null;
        photoExecutor.shutdown(); oscRepeater.close();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView label(String text, int size, int color) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color); return v;
    }
    private TextView section(String text) {
        TextView v = label(text, 12, ACCENT); v.setTypeface(null, Typeface.BOLD); v.setPadding(0, dp(24), 0, dp(10)); return v;
    }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private Button button(String text, boolean primary) {
        Button v = new Button(this); v.setText(text); v.setAllCaps(false); v.setTextColor(primary ? BG : TEXT);
        v.setTextSize(13); v.setMinHeight(dp(48)); v.setMinimumHeight(dp(48)); v.setPadding(dp(14), dp(6), dp(14), dp(6));
        v.setBackground(background(primary ? ACCENT : PANEL, 14)); return v;
    }
    private CheckBox check(String text, boolean value) { CheckBox v = new CheckBox(this); v.setText(text); v.setTextColor(TEXT); v.setChecked(value); v.setMinHeight(dp(48)); return v; }
    private RadioButton radio(String text, int id) { RadioButton v = new RadioButton(this); v.setId(id); v.setText(text); v.setTextColor(TEXT); return v; }
    private EditText input(LinearLayout parent, String title, String value, boolean number) {
        TextView titleView = label(title, 12, MUTED); titleView.setPadding(0, dp(10), 0, dp(4)); parent.addView(titleView);
        EditText v = new EditText(this); v.setTextColor(TEXT); v.setHintTextColor(MUTED); v.setSingleLine(true); v.setTextSize(16);
        v.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        v.setText(value); v.setPadding(dp(12), dp(10), dp(12), dp(10)); v.setBackground(background(PANEL, 10));
        v.setContentDescription(title); parent.addView(v, new LinearLayout.LayoutParams(-1, dp(50))); return v;
    }
    private LinearLayout.LayoutParams spaced(int width, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(height)); params.setMargins(0, dp(12), 0, dp(12)); return params;
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
