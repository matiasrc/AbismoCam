# Actualización de Estabilidad NDI y Red (`abismoCam` 0.9.0-fix)

Este paquete contiene los archivos modificados sobre la versión `0.9.0` para resolver el congelamiento intermitente del streaming NDI en los receptores (OBS / TouchDesigner) cuando el teléfono transmite por Wi-Fi.

## 1. Cómo aplicar estos archivos en el proyecto existente
Si descomprimís este archivo ZIP directamente en la carpeta raíz del repositorio (`Repo-abismo-cam`), se reemplazarán automáticamente en sus rutas exactas:

- `app/src/main/java/ar/com/biopus/abismocam/NdiSender.java`
- `app/src/main/java/ar/com/biopus/abismocam/TransmissionService.java`
- `app/src/main/java/ar/com/biopus/abismocam/FrameScaler.java`
- `app/src/main/java/ar/com/biopus/abismocam/MainActivity.java`
- `app/src/main/java/ar/com/biopus/abismocam/OscRepeater.java`
- `app/src/main/java/ar/com/biopus/abismocam/Osc.java`
- `app/src/main/java/ar/com/biopus/abismocam/Config.java`
- `app/src/main/java/ar/com/biopus/abismocam/CameraViewport.java`
- `app/src/test/java/ar/com/biopus/abismocam/OscRepeaterTest.java`
- `entregables/AbismoCam-debug.apk` (APK ya compilado y firmado)
- `entregables/AbismoCam-debug.apk.sha256`

---

## 2. Detalle técnico de los cambios en el código

### A. `app/src/main/java/ar/com/biopus/abismocam/NdiSender.java`
* **`clockVideo = 1` en `CreateSettings`:** Antes estaba en `0`, por lo que el envío no era regulado por el reloj interno del SDK de NDI y dependía únicamente de un filtro manual (`0.9 * periodo`). Ante variaciones de latencia en Wi-Fi, el plugin `obs-ndi` (DistroAV) en OBS desbordaba o estancaba su buffer de recepción y congelaba la textura de video aunque la conexión NDI siguiera activa. Con `clockVideo = 1`, el SDK de NDI cadencia la salida exactamente a `fpsN / fpsD`.
* **Reinicio completo del SDK (`close()` antes de `start()`):** Cada vez que se inicia o reinicia el emisor (`start(...)`), primero se invoca `close()` (`NDIlib_send_destroy` + `NDIlib_destroy`) antes de volver a ejecutar `NdiTransport.configure(...)`, `NDIlib_initialize()` y `NDIlib_send_create(...)`. Esto obliga al SDK nativo en C (`libndi.so`) a descartar sockets huérfanos, re-enumerar las interfaces de red del sistema operativo y reiniciar desde cero su anunciador mDNS conservando el mismo nombre de fuente (`abismoCam_0001`).

### B. `app/src/main/java/ar/com/biopus/abismocam/TransmissionService.java`
* **Monitor de red en tiempo real (`ConnectivityManager.NetworkCallback`) para `TRANSPORT_ETHERNET` y `TRANSPORT_WIFI`:**
  * Escucha `onAvailable`, `onLost` y `onLinkPropertiesChanged` eliminando el requisito `NET_CAPABILITY_INTERNET` (indispensable cuando el router del show opera en red local aislada sin salida a internet).
  * Vincula explícitamente el proceso a la red local activa (`connectivity.bindProcessToNetwork(...)`, priorizando Ethernet sobre Wi-Fi) para asegurar que los sockets nativos de `libndi.so` y `NsdManager` salgan por la interfaz recreada.
  * Cuando el router se reinicia, se desconecta/reconecta el adaptador Ethernet o cambia la IP local (`ipv4Signature`), destruye la instancia colgada (`ndi.close()`), renueva el `MulticastLock` y `NsdManager`, y levanta una instancia nueva del NDI Sender manteniendo intacto `config.sourceName()` (`abismoCam_0001`), permitiendo que TouchDesigner y OBS reconecten solos sin intervención manual.
* **`WIFI_MODE_FULL_LOW_LATENCY` en Android 10+ (API 29+):** Se reemplazó `WIFI_MODE_FULL_HIGH_PERF` (deprecado e ignorado desde Android 10) por `WIFI_MODE_FULL_LOW_LATENCY` en dispositivos Android 10 o superior (como el Samsung A51 y Motorola G85) y se desactivó el conteo de referencias (`setReferenceCounted(false)`), evitando que el chip Wi-Fi entre en micro-ahorro de energía (DTIM) entre fotogramas.
* **Reducción de llamadas nativas a `ndi.connections()`:** La consulta `NDIlib_send_get_no_connections` en el hilo de cámara pasó de ejecutarse cada 1 segundo a cada 3 segundos (`lastConnectionsCheckMs`), reduciendo contención de locks internos del SDK durante la transmisión.
* **Auto-reconexión ante errores transitorios:** Antes, cualquier `RuntimeException` en `analyze()` llamaba a `stopSender()` y dejaba NDI apagado permanentemente. Ahora tolera fallos transitorios aislados y, si se acumulan errores consecutivos con `wanted == true`, reinicia el emisor automáticamente tras 500 ms (`stopSender()` + `startSender()`).

### C. `app/src/main/java/ar/com/biopus/abismocam/FrameScaler.java`
* **Doble buffer directo (`pixelsA` / `pixelsB`) y fast-path sin padding:** Cuando `stride == width * 4`, copia directamente el buffer sin iterar fila por fila, reduciendo uso de CPU y temperatura en el Samsung A51. Además alterna entre dos `ByteBuffer` directos para evitar sobrescritura de memoria.

### D. `app/src/main/java/ar/com/biopus/abismocam/MainActivity.java`
* **Deduplicación de actualizaciones de UI (`refreshTransmission` y `setNdiMessage`):** Como `publish(false)` se notifica en cada cuadro para mantener actualizado `ndiFramesSent`, ahora solo se invoca `setText(...)` en los `TextView` (`cameraStatus`, `ndiStatus`, `ndiIndicator`, `streamButton`) cuando el texto realmente cambió, evitando 30 pasadas de layout/redibujado por segundo en el hilo principal.
* **Corrección de NullPointerException en Android 14/15 (ej. Moto G85):** Se inicializa el `contentDescription` de `ndiIndicator` y se protegen las comparaciones contra `null`. Además, la solicitud de permisos de notificaciones se ejecuta de manera segura tras conectar el servicio para no interrumpir el arranque en primer plano.

### E. `OscRepeater.java` y `Osc.java` (10 mensajes OSC y prioridad WMM)
* **Ráfaga de 10 mensajes OSC por estado (`COUNT = 10`):** En `OscRepeater.java`, cada transición (`1` al comenzar la cuenta y `0` al finalizarla, o 10 envíos de `1` seguidos de 10 envíos de `0` con cuenta de 0 s) envía ahora **10 datagramas UDP** en lugar de 5.
* **Prioridad DSCP de Baja Latencia (`TrafficClass 0xB8` / Expedited Forwarding):** En `Osc.java`, el `DatagramSocket` marca los paquetes UDP con prioridad de voz/tiempo real (`0xB8`) para que el chip Wi-Fi (WMM) no los postergue detrás de las ráfagas de video NDI.

### F. `Config.java`, `CameraViewport.java`, `TransmissionService.java` y `MainActivity.java` (Resoluciones 9:16)
* **Resoluciones actualizadas a formato vertical 9:16:** Se reemplazaron las opciones de resolución por **`360 × 640` (Baja)**, **`540 × 960` (Media, predeterminada)** y **`720 × 1280` (Alta)** tanto en el selector de ajustes como en el `ViewPort` de CameraX (`Rational(9, 16)` con `RATIO_16_9_FALLBACK_AUTO_STRATEGY`) y en la vista previa (`CameraViewport`).
