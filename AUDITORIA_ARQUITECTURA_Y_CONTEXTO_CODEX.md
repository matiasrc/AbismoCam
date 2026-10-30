# Briefing Técnico y Guía de Auditoría de Software: abismoCam (v0.9.1)
**Proyecto:** *El abismo de la visibilidad* (Lisandro Peralta / Biopus)  
**Destinatario de este documento:** Modelo de IA Auditor (Codex / Claude / GPT)  
**Propósito:** Proveer todo el contexto operativo, requerimientos de la obra, arquitectura de software, decisiones de ingeniería y vectores de análisis crítico para realizar una auditoría de código profunda y rigurosa sobre el repositorio.

---

## 1. Contexto del Proyecto y Requerimientos Operativos

### 1.1. La Obra y la Instalación
*abismoCam* es una aplicación móvil desarrollada para Android (dispositivo objetivo: **Samsung Galaxy A51** y compatibles con Android 8.0+ / API 26+, arquitectura ARM64). Funciona como el núcleo sensorial y de captura en una instalación de arte interactivo (*El abismo de la visibilidad*).

### 1.2. Topología de Red y Ecosistema de la Instalación
El sistema opera en una red de área local cableada e inalámbrica administrada por un router dedicado **TP-Link Archer AX55 (Wi-Fi 6 AX3000)** en un espacio de exhibición, habitualmente **aislado de internet**:

```
                              [ Router TP-Link AX55 ]
                               /          |          \
        (Wi-Fi 5 GHz Exclusivo)   (Wi-Fi 2.4 GHz)   (LAN Gigabit Ethernet)
                  /                       |                       \
     [ Celular abismoCam ]      [ 4 PCs Receptoras ]     [ PC 1, PC 2, PC 3 ]
     - Video NDI constante      - Reciben ráfagas OSC    - PC 1 y PC 2: OBS + TouchDesigner
     - Ráfagas de disparo OSC     (disparo interactivo)  - PC 3: Servidor central del show
```

### 1.3. Requerimientos Funcionales Esenciales
1. **Transmisión de Video NDI High Bandwidth Continua e Ininterrumpida:**
   - La cámara debe transmitir video de alta calidad sin audio a 15 o 30 fps mediante NDI en formato vertical 9:16 (`nHD 360×640`, `qHD 540×960`, o `HD 720×1280`).
   - El streaming **no puede cortarse** si el usuario minimiza la app, bloquea la pantalla, despliega notificaciones o entra a los ajustes.
2. **Kiosco de Espejo / Selfie Interactivo:**
   - Pantalla completa (*edge-to-edge*), con vista de cámara frontal espejada y controles flotantes superpuestos (como una app de cámara nativa).
   - Al soltar el obturador táctil, se dispara una cuenta regresiva (0 a 15 segundos). Durante el conteo, flash y congelado de imagen, el streaming NDI **sigue enviando la imagen en vivo sin interrupciones**.
   - Al llegar a 0, se guarda una fotografía JPEG local en `Pictures/AbismoCam` y se muestra un fotograma congelado durante ~1.2 segundos con un flash blanco.
3. **Señalización OSC Redundante y de Alta Prioridad:**
   - Debe notificar a los servidores multimedia (TouchDesigner, Resolume, scripts de control) enviando el mensaje OSC `/camara/disparo` (`1` al iniciar el conteo o disparar, y `0` al concluir la captura).
   - Como se transmite por UDP sobre Wi-Fi en un entorno con tráfico denso, **no debe perderse ningún paquete de cambio de estado**.
4. **Montaje Físico Invertido (Celular Boca Abajo):**
   - En muchas estructuras físicas del montaje, el teléfono se coloca boca abajo para facilitar el ruteo del cable USB de alimentación/red.
   - Debe existir una opción de software que gire 180° la interfaz, el visor en pantalla ("el espejo"), la foto guardada y el stream NDI, de modo que todo se observe derecho.

---

## 2. Arquitectura de Software y Mapa de Componentes

La aplicación sigue una arquitectura desacoplada para separar el ciclo de vida de la interfaz de usuario del motor de captura y transmisión de red:

```
[ MainActivity (UI Layer) ]
   ├── PreviewView (CameraX SurfaceProvider / TextureView COMPATIBLE)
   ├── FrameLayout cameraControls (Header, Shutter, Footer, Overlays)
   └── applyOrientation() (Rotación 180° de controles e insets)
          │
          │ BIND_AUTO_CREATE / startForegroundService
          ▼
[ TransmissionService (Foreground Service / Core Engine) ]
   ├── CameraX Pipeline (ProcessCameraProvider, Preview, ImageAnalysis, ViewPort)
   ├── FrameScaler (Double-buffered direct ByteBuffer scaler & fast-path)
   ├── NdiSender (JNA Bridge -> libndi.so nativo C)
   ├── ConnectivityManager.NetworkCallback (Auto-reconexión y bindProcessToNetwork)
   ├── WifiLock (WIFI_MODE_FULL_LOW_LATENCY) + MulticastLock + WakeLock
   └── Snapshot Capture Queue (Snapshot request handler)
          │
          ▼
[ OscRepeater & Osc (OSC Subsystem) ]
   ├── OscRepeater (Ráfaga de 10 paquetes UDP espaciados 10-100 ms)
   └── Osc.send() (DatagramSocket con DSCP 0xB8 / Expedited Forwarding)
```

---

## 3. Desglose Técnico en Profundidad de cada Componente

### 3.1. Servicio en Primer Plano y Ciclo de Vida (`TransmissionService.java`)
- **Problema que resuelve:** En Android, una `Activity` en segundo plano o con la pantalla apagada es degradada, pausada y eventualmente destruida por el sistema operativo (Memory Killer), lo que cortaría la transmisión de video hacia la instalación.
- **Solución implementada:** Se diseñó un `Service` en primer plano (`android.app.Service` + `startForeground`) de tipo `FOREGROUND_SERVICE_TYPE_CAMERA` (API 30+).
- **Detalle técnico:**
  - El servicio mantiene un `Notification` persistente con acción para detener NDI manualmente.
  - Adquiere locks de hardware a nivel de sistema:
    - `PowerManager.PARTIAL_WAKE_LOCK`: Impide que la CPU pase a suspensión profunda (Deep Sleep).
    - `WifiManager.WIFI_MODE_FULL_LOW_LATENCY` (API 29+) con `setReferenceCounted(false)`: Deshabilita el ahorro de energía del chip Wi-Fi (DTIM) para evitar retardos intermitentes entre fotogramas.
    - `MulticastLock`: Permite que el anuncio y descubrimiento mDNS de NDI reciba y envíe paquetes multicast en la interfaz Wi-Fi.
  - La desconexión de la `MainActivity` (al pulsar Home, bloquear el teléfono o cambiar de app) simplemente desvincula (`detach()`) el `SurfaceProvider` del visor sin cerrar el emisor NDI ni la sesión de cámara. Quitar la app de la lista de aplicaciones recientes (`onTaskRemoved` o cierre del proceso) destruye ordenadamente el servicio y libera los recursos nativos.

### 3.2. Pipeline de Cámara y Normalización de Cuadros (`CameraX` y `FrameScaler.java`)
- **CameraX UseCases:**
  - `Preview`: Conectado al `PreviewView` de la interfaz para previsualización local fluida.
  - `ImageAnalysis`: Configurado en formato `OUTPUT_IMAGE_FORMAT_RGBA_8888`, `STRATEGY_KEEP_ONLY_LATEST`, y `setOutputImageRotationEnabled(true)`.
  - `UseCaseGroup` con `ViewPort`: Se agrupan `Preview` e `ImageAnalysis` bajo un mismo `ViewPort` (`Rational(9, 16)`) para garantizar que la vista en el teléfono y los cuadros analizados compartan exactamente el mismo sensor crop.
- **Gestión de Memoria y Escalador Directo (`FrameScaler.java`):**
  - Para evitar la recolección de basura (GC pauses) a 30 fps, no se instancian `Bitmaps` ni arrays en cada cuadro.
  - Se implementó un esquema de doble búfer directo (`ByteBuffer.allocateDirect(outWidth * outHeight * 4)` en `pixelsA` y `pixelsB`) que alterna entre cuadros.
  - **Fast-path sin padding:** Si el `stride` del sensor coincide exactamente con `width * 4`, se utiliza una copia en bloque (`input.copyPixelsFromBuffer`) evitando iterar fila por fila en la CPU del teléfono.

### 3.3. Emisión NDI High Bandwidth vía JNA (`NdiSender.java`)
- **Problema de origen:** El SDK de NDI oficial para Android distribuye bibliotecas nativas compiladas en C (`libndi.so`). El proyecto utiliza JNA (*Java Native Access*) para comunicarse con la biblioteca C sin requerir compilación NDK externa.
- **Correcciones críticas de estabilidad aplicadas:**
  1. **`clockVideo = 1` en `NDIlib_send_create_t`:** Anteriormente configurado en `0`. Sin el reloj de NDI, el emisor dependía de la cadencia de la cámara en Java, provocando que variaciones de jitter en Wi-Fi desbordaran los buffers del receptor en OBS (`obs-ndi` / DistroAV) o TouchDesigner, congelando la imagen indefinidamente. Con `clockVideo = 1`, el SDK de NDI toma el control estricto de la temporización de salida (`fpsN / fpsD`).
  2. **Ciclo de vida nativo estricto (`close()` antes de `start()`):** Cada reinicio del emisor destruye completamente la instancia previa (`NDIlib_send_destroy` + `NDIlib_destroy`) antes de re-inicializar el SDK (`NDIlib_initialize`). Esto fuerza al SDK nativo en C a desechar sockets huérfanos, re-enumerar interfaces del kernel y recrear su anunciador mDNS manteniendo intacto el nombre de red (`abismoCam_0001`).
  3. **Configuración Unicast / Multicast (`ndi-config.v1.json`):** Escritura atómica en `files/ndi/ndi-config.v1.json` mediante `NDI_CONFIG_DIR`, permitiendo que la red utilice transporte multicast (TTL 1, rango 239.255.0.0/16) cuando el switch/router lo soporta con IGMP Snooping.
  4. **Throttle de consultas JNI:** Se limitó la consulta de clientes conectados (`NDIlib_send_get_no_connections`) a una vez cada 3 segundos para evitar contención de bloqueos dentro del hilo de video.

### 3.4. Resiliencia de Red sin Salida a Internet (`TransmissionService.java`)
- **Problema que resuelve:** En instalaciones cerradas de arte, el router AX55 no está conectado a internet. Android por defecto descarta o desprioriza redes Wi-Fi o adaptadores Ethernet USB que no tengan conectividad a internet externa (`NET_CAPABILITY_VALIDATED`).
- **Solución implementada:**
  - Se configuró un `ConnectivityManager.NetworkCallback` escuchando `TRANSPORT_WIFI` y `TRANSPORT_ETHERNET` **sin exigir `NET_CAPABILITY_INTERNET`**.
  - Se vincula explícitamente el socket del proceso al adaptador activo mediante `connectivity.bindProcessToNetwork(preferredNetwork)`.
  - Ante caídas de red, reinicio del router o cambio de IP local, el callback detecta la nueva firma IP (`ipv4Signature`), destruye el sender colgado y levanta uno nuevo sin alterar el identificador configurado ni requerir interacción del operador.

### 3.5. Subsistema de Disparo OSC con Redundancia y Prioridad DSCP (`Osc.java` y `OscRepeater.java`)
- **Problema que resuelve:** La aplicación debe avisar a múltiples computadoras el inicio del conteo y la captura. UDP es un protocolo no confiable sin acuse de recibo (ACK). En una red Wi-Fi donde el teléfono está subiendo 50-80 Mbps de video NDI, los paquetes pequeños de control pueden sufrir descarte de cola (*queue drop*) en el punto de acceso.
- **Solución implementada:**
  1. **Ráfaga redundante de 10 paquetes (`COUNT = 10`):** En cada evento (inicio de conteo `1` o foto lista `0`), se programa una ráfaga de 10 datagramas UDP idénticos separados por un intervalo configurable (10, 25, 50 o 100 ms). Los servidores receptores (TouchDesigner/Resolume) están configurados con lógica de flanco (*state-change*), actuando ante el primer paquete recibido y descartando los siguientes 9 paquetes redundantes.
  2. **Marcado DSCP de Prioridad Expedited Forwarding (`0xB8`):** En `Osc.java`, el `DatagramSocket` establece `setTrafficClass(0xB8)`. Este byte aplica DSCP 46 (Prioridad de Voz / Expedited Forwarding), lo que le indica a la capa WMM (*Wi-Fi Multimedia*) del estándar 802.11 que coloque los paquetes OSC en la cola de máxima prioridad de la antena, saltando por delante de las ráfagas de video NDI.

### 3.6. Interfaz de Pantalla Completa (Edge-to-Edge) y Rotación 180° (`MainActivity.java`)
- **Geometría Edge-to-Edge:**
  - Se eliminó el encajonamiento en barras negras de versiones previas. `PreviewView` y `frozenFrame` se ubican en el fondo ocupando el 100% de la pantalla (`MATCH_PARENT`).
  - La interfaz de controles (`cameraControls`) flota como un overlay sobre la imagen con degradés oscuros sutiles (`topShade` y `bottomShade`), permitiendo lectura clara de tipografías y botones blancos sobre fondos claros.
- **Soporte de Rotación 180° por Software:**
  - *Problema:* El manifiesto de Android tiene la app fijada en `android:screenOrientation="portrait"` para evitar rotaciones accidentales. Por este motivo, el sistema operativo nunca rota la actividad al poner el teléfono boca abajo, y `PreviewView` ignora la rotación 2D convencional del contenedor Android porque dibuja directamente en hardware mediante matrices internas.
  - *Solución:* Cuando se activa **Rotar 180° (montaje invertido)** en configuración:
    1. Se le pasa `targetRotation = Surface.ROTATION_180` al `Preview.Builder`, `ImageAnalysis.Builder` y `ViewPort.Builder` de CameraX. Esto instruye a CameraX a rotar nativamente por hardware el flujo de video 180°.
    2. La capa de controles (`cameraControls`) y el panel de ajustes giran 180° (`setRotation(180f)`), invirtiendo además el padding de las barras de navegación y estado (`insets`).
    3. La captura de foto (`photo`), el cuadro congelado (`frozenFrame`) y la miniatura (`lastPhoto`) se mantienen en sincronía directa, presentándose al derecho sin desfasaje visual.

---

## 4. Preguntas y Vectores de Análisis Sugeridos para la Auditoría en Codex

Al pasar el repositorio y este documento a Codex, se sugiere solicitar una evaluación focalizada en los siguientes puntos críticos:

1. **Gestión de Memoria Nativa y Fugas JNI:**
   - ¿Existe algún riesgo de fuga de memoria o descriptor de socket en `NdiSender.java` y `TransmissionService.java` durante reinicios sucesivos de red o recreaciones de sesión de cámara?
2. **Concurrencia y Sincronización entre Hilos:**
   - ¿Existen condiciones de carrera (*race conditions*) entre el hilo de análisis de cámara de CameraX, el executor de persistencia de fotos en disco (`photoExecutor`) y el hilo principal de Android (`Looper.getMainLooper()`)?
3. **Resiliencia ante Excepciones y Ciclo de Vida de Android:**
   - ¿El servicio en primer plano y el `ConnectivityManager.NetworkCallback` manejan adecuadamente todos los escenarios extremos de corte de red, suspensión de proceso o reinicio de dispositivo?
4. **Optimización del Pipeline de Imagen:**
   - ¿El escalador de cuadros en `FrameScaler.java` y el manejo de `ByteBuffer` directos pueden optimizarse aún más en términos de latencia y uso de CPU en procesadores Exynos / Snapdragon gama media?
5. **Robustez de la Ráfaga OSC:**
   - ¿La estrategia de ráfaga de 10 paquetes con DSCP `0xB8` y el descarte de repeticiones en el receptor cumplen con las mejores prácticas de control en tiempo real en redes mixtas?

---

*Repositorio de referencia:* `https://github.com/matiasrc/AbismoCam.git`  
*Rama auditada:* `matias/estabilidad-ndi`  
*Commit:* `c62d875` (v0.9.1)
