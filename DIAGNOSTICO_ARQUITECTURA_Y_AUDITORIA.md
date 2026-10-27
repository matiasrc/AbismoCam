# Diagnóstico de Cortes NDI, Arquitectura del Montaje y Auditoría de la App Android (`abismoCam`)

Este documento reúne el diagnóstico completo del problema de congelamiento de imagen en NDI dentro del montaje de **El abismo de la visibilidad**, la arquitectura recomendada para el flujo de video/OSC (con OBS, TouchDesigner, Windows y el router TP-Link Archer AX55) y la auditoría con los cambios aplicados al código de la aplicación Android `abismoCam`.

---

## 1. Descripción del Escenario y del Síntoma

### Topología del montaje
* **Emisor móvil:** 1 celular Android (**Samsung A51**) ejecutando la aplicación `abismoCam`, conectado por **Wi-Fi** al router **TP-Link Archer AX55 (AX3000)**.
* **Receptores de video (Cableados):** **PC 1** y **PC 2** (Windows) conectadas por **cable de red (LAN)** al mismo router. Reciben el stream NDI del celular en **OBS** (para forzar la resolución), lo pasan a **TouchDesigner** para procesar la imagen, y luego envían su resultado por **NDI** a una tercera computadora (**PC 3 Central**), también conectada por cable LAN al router.
* **Receptores de disparo OSC:** Cuando alguien toca el obturador en el celular, `abismoCam` envía un mensaje OSC (`/camara/disparo` con valor `1` y luego `0`) a la **PC 3** y a **otras 4 computadoras conectadas por Wi-Fi** al mismo router.

### Síntoma observado
* La cámara en la pantalla del celular sigue funcionando normalmente.
* En **una de las computadoras receptoras (PC 1 o PC 2) la imagen NDI del celular se congela**, mientras que **en la otra computadora sigue funcionando**.
* La computadora congelada **sigue reconociendo al sender NDI como conectado**, pero con la imagen clavada en el último fotograma (y sigue enviando su propio NDI hacia la PC 3).
* A veces la segunda computadora sigue andando y a veces momentos después le ocurre lo mismo y quedan las dos congeladas. Rara vez se recupera sola; por lo general queda congelada.

---

## 2. Diagnóstico: ¿Por qué se congela una PC sí y otra no? (Las 4 Causas Reales)

El hecho de que **la cámara en el celular siga andando y una PC se congele mientras la otra continúa recibiendo video en vivo** demuestra que el proceso de cámara del celular no se detuvo: lo que se cuelga es **el flujo de red individual hacia ese receptor y el buffer de entrada de OBS**.

Las 4 causas combinadas que provocan este fallo en el montaje son:

### Causa 1: Comportamiento del plugin NDI de OBS (`obs-ndi` / DistroAV) ante jitter de Wi-Fi y `clockVideo = 0`
* En el código original de `abismoCam` (`NdiSender.java`), la estructura de creación del emisor tenía `clockVideo = 0` y `timecode = Long.MAX_VALUE` (`NDIlib_send_timecode_synthesize`), regulando el envío con un condicional manual en Java (`0.9 * periodo`).
* Cuando una fuente NDI sobre Wi-Fi envía cuadros sin *clocking* nativo del SDK y ocurre un micro-atraso de paquetes en el aire (propio del Wi-Fi), el plugin NDI de **OBS** (cuando está configurado con sincronización de red `Sync: Network`) desincroniza o satura su cola interna de fotogramas.
* Ante esa desincronización, **OBS mantiene el socket TCP/UDP abierto** (por eso la fuente sigue figurando "conectada"), pero **deja de refrescar la textura de video y queda clavado en el último cuadro**.

### Causa 2: Las 4 computadoras por Wi-Fi compitiendo por aire con el celular + paquetes Broadcast
* El celular está enviando video pesado en tiempo real por Wi-Fi mientras **otras 4 computadoras están conectadas por Wi-Fi al mismo router**.
* Si las 4 computadoras están en la misma banda de 5 GHz que el celular, compiten por tiempo de antena (*airtime*).
* Además, si el envío de OSC está configurado en modo **Broadcast** (`192.168.0.255`), el estándar Wi-Fi 802.11 obliga al punto de acceso a transmitir los paquetes broadcast a la **velocidad de modulación más baja posible (1 Mbps o 6 Mbps)** para garantizar que todos los dispositivos Wi-Fi los escuchen. Eso frena drásticamente el canal Wi-Fi durante la ráfaga y ahoga la subida de video NDI del celular.

### Causa 3: El celular envía el video dos veces por aire (Unicast doble hacia PC 1 y PC 2)
* En modo **Unicast**, el celular tiene que abrir dos conexiones de video independientes y subir el stream **por duplicado a través del Wi-Fi**: una copia para la PC 1 y otra copia para la PC 2 (entre 50 y 80 Mbps constantes de subida desde la antena del teléfono).
* Como son dos conexiones TCP/UDP separadas, apenas hay una fluctuación de señal o congestión en el aire, la ventana de transmisión hacia **una** de las dos PCs sufre pérdida/retransmisión primero y congela el OBS de esa máquina, mientras el socket hacia la otra PC sobrevive un rato más (o cae segundos después).

### Causa 4: Ahorro de energía Wi-Fi en Android 10+ y doble placa de red en Windows
* En el código original de `TransmissionService.java` se usaba `WifiManager.WIFI_MODE_FULL_HIGH_PERF`. Esa constante fue **deprecada en Android 10 (API 29)** y el sistema operativo del **Samsung A51** la ignora por completo, permitiendo que el chip Wi-Fi entre en micro-ahorros de energía (DTIM) entre ráfagas.
* Por otro lado, si las PC 1, PC 2 o PC 3 están conectadas por cable LAN pero tienen el Wi-Fi de Windows encendido al mismo tiempo, NDI mezcla las rutas de red de ambas placas y congela la recepción.

---

## 3. Arquitectura y Configuración Paso a Paso para que NO Falle

### Paso A: Configuración de OBS y Flujo de Video en PC 1, PC 2 y PC 3 (Windows)

1. **Ajustar las propiedades de la Fuente NDI dentro de OBS (En PC 1 y PC 2) — OBLIGATORIO:**
   * Hacer doble clic sobre la **Fuente NDI** en OBS para abrir sus propiedades y configurar:
     * **Sincronización (`Sync`):** Cambiar de *Network* a **`Internal` (`Interna`)**. *(Esto impide que OBS congele la imagen si los timestamps del Wi-Fi sufren jitter).*
     * **Latencia (`Latency Mode`):** **`Normal (Safe)`** *(no usar `Lowest` en recepciones por Wi-Fi).*
     * **Permitir aceleración por hardware (`Allow Hardware Acceleration`):** **Activado (Tildado)**.
     * **Ancho de banda (`Bandwidth`):** **`Highest`**.

2. **Redistribución por Cable para aliviar al Celular al 50% (Muy Recomendado):**
   * Como no se dispone de adaptador de red físico para el celular, la mejor forma de blindar el Wi-Fi del teléfono es que **el celular le envíe NDI a UNA SOLA computadora (por ejemplo, solo a PC 1)**.
   * Luego, **PC 1 reenvía esa cámara por NDI hacia PC 2** (desde OBS o desde un `NDI Out TOP` directo en TouchDesigner).
   * **Ventaja:** El celular reduce a la **mitad** su consumo de Wi-Fi y CPU (envía 1 solo stream en vez de 2), y entre PC 1 y PC 2 el video viaja por **cable Gigabit Ethernet (1000 Mbps)** sin pérdidas ni congelamientos asimétricos.

3. **Apagar el Wi-Fi en las 3 PCs conectadas por cable (PC 1, PC 2 y PC 3):**
   * En las 3 máquinas que van por cable LAN al router, desactivar el Wi-Fi de Windows para que tengan una única dirección IP activa (la del cable Ethernet).

4. **Configurar NDI Access Manager y Red en Windows (PC 1, PC 2 y PC 3):**
   * Abrir **NDI Access Manager** → pestaña **Advanced**:
     * Verificar que **Multicast Sending** esté **DESACTIVADO** en las PCs (así el tráfico NDI que PC 1 y PC 2 mandan a PC 3 va directo por cable de puerto a puerto y no inunda el Wi-Fi del router).
     * **Receive Mode:** `Single TCP` o `UDP (With Fallback)`.
   * En *Configuración de Windows → Red e Internet → Ethernet*, verificar que la red figure como **Red Privada** (no *Pública*, para que el Firewall de Windows no corte conexiones UDP/TCP tras unos minutos).
   * En *Administrador de Dispositivos → Adaptadores de red → Placa Ethernet → Propiedades → Opciones avanzadas*: desactivar **Energy Efficient Ethernet (EEE)** / **Ahorro de energía**.

---

### Paso B: Configuración del Router TP-Link Archer AX55
*(Ver el detalle completo menú por menú en el documento adjunto `GUIA_CONFIGURACION_ROUTER_Y_RED.md`).*

1. **Separar las bandas Wi-Fi (Apagar Smart Connect):**
   * Red **5 GHz (`Abismo_CAM_5G`)**: **Exclusiva para el celular**. Canal fijo **36, 40, 44 o 48** (nunca *Auto* ni canales DFS mayores a 48). Ancho de canal **40 MHz** (desactivar 80/160 MHz).
   * Red **2.4 GHz (`Abismo_OSC_24G`)**: Conectar acá las **4 computadoras Wi-Fi que reciben el disparo OSC**.
2. **Desactivar TWT, OFDMA y Airtime Fairness** en *Avanzado → Inalámbrico → Configuración Adicional*.
3. **Reservar IPs fijas (Address Reservation)** en *Avanzado → Red → Servidor DHCP* para el celular y las 7 computadoras.

---

### Paso C: Ajustes en el Celular Samsung A51 y en la App `abismoCam`

1. **Ajustes del sistema Android en el Samsung A51:**
   * *Ajustes → Aplicaciones → abismoCam → Batería*: Seleccionar **"No restringido" (`Unrestricted`)**.
   * *Ajustes → Conexiones → Wi-Fi → (3 puntos arriba) → Intelligent Wi-Fi*:
     * Desactivar **"Cambiar a datos móviles" (`Switch to mobile data`)**.
     * Desactivar **"Modo de ahorro de energía Wi-Fi" (`Wi-Fi power saving mode`)**.
   * Activar **Modo Avión** y encender únicamente el **Wi-Fi** conectado a la red de 5 GHz (`Abismo_CAM_5G`).

2. **Ajustes dentro de `abismoCam` (manteniendo apretado el engranaje 3 segundos):**
   * **Transporte NDI:** **`Unicast`**.
   * **Calidad y FPS:** **Media (`576 × 720`)** a **30 fps** (o **15 fps** si se busca menor carga térmica en sesiones de varias horas).
   * **Destinos OSC:** Usar el modo **"Computadoras individuales"** cargando las IPs fijas de la PC 3 y las 4 PCs de OSC (en lugar de *Broadcast*), para evitar que los paquetes broadcast degraden la velocidad de modulación de la antena Wi-Fi.

---

## 4. Auditoría de la Aplicación Android y Cambios Realizados en el Código

Se auditó todo el código fuente del repositorio y se implementaron **6 mejoras clave** en los siguientes 4 archivos (ya compiladas dentro del nuevo `entregables/AbismoCam-debug.apk`):

### 1. Activación del reloj nativo de video NDI (`clockVideo = 1`)
* **Archivo:** `app/src/main/java/ar/com/biopus/abismocam/NdiSender.java`
* **Problema:** `CreateSettings` tenía `clockVideo = 0`, enviando cuadros sin regulación temporal nativa del SDK, lo que provocaba el bloqueo del buffer de recepción en OBS (`obs-ndi`).
* **Solución:** Se cambió a `public byte clockVideo = 1;` en `CreateSettings` para que el SDK de NDI cadencie los cuadros de forma exacta según los FPS configurados.

### 2. Reconexión automática ante reinicio del router o desconexión de cable Ethernet/Wi-Fi (`ConnectivityManager.NetworkCallback`)
* **Archivos:** `app/src/main/java/ar/com/biopus/abismocam/TransmissionService.java` y `app/src/main/java/ar/com/biopus/abismocam/NdiSender.java`
* **Problema:** Si el router se reiniciaba o el cable Ethernet se desconectaba y volvía a conectar, Android recreaba la interfaz de red (`eth0`/`wlan0`), pero el SDK nativo (`libndi.so`) quedaba atado al socket muerto mostrando `"NDI activo · 0 receptor(es)"` y dejando de anunciarse por mDNS.
* **Solución:** Se implementó un `ConnectivityManager.NetworkCallback` para `TRANSPORT_ETHERNET` y `TRANSPORT_WIFI` (sin exigir `NET_CAPABILITY_INTERNET` para funcionar en redes de show sin internet, y vinculando el proceso a la interfaz local activa mediante `bindProcessToNetwork`). Ante cualquier evento `onLost`, `onAvailable` o cambio de IP en `onLinkPropertiesChanged`, el servicio destruye por completo la instancia previa (`ndi.close()` -> `NDIlib_send_destroy` + `NDIlib_destroy`), renueva el `MulticastLock` y `NsdManager`, y levanta desde cero el NDI Sender manteniendo exactamente el mismo nombre de fuente (`abismoCam_0001`), logrando que TouchDesigner y OBS reconecten solos.

### 3. Lock de Wi-Fi de Baja Latencia para Android 10+ (`WIFI_MODE_FULL_LOW_LATENCY`)
* **Archivo:** `app/src/main/java/ar/com/biopus/abismocam/TransmissionService.java`
* **Problema:** `WIFI_MODE_FULL_HIGH_PERF` está deprecado desde Android 10 (API 29) y no tiene efecto en Android modernos.
* **Solución:** En Android 10 o superior (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q`) ahora utiliza `WifiManager.WIFI_MODE_FULL_LOW_LATENCY` con `setReferenceCounted(false)`, manteniendo el radio Wi-Fi activo sin pausas DTIM entre cuadros.

### 4. Menor contención nativa en el hilo de cámara
* **Archivo:** `app/src/main/java/ar/com/biopus/abismocam/TransmissionService.java`
* **Problema:** Cada 1 segundo se invocaba `ndi.connections()` (`NDIlib_send_get_no_connections`) en el mismo hilo de envío de cuadros.
* **Solución:** La consulta `ndi.connections()` se espació a cada 3 segundos (`lastConnectionsCheckMs`), reduciendo bloqueos de sincronización dentro de `libndi.so`.

### 5. Auto-recuperación del emisor NDI ante errores transitorios
* **Archivo:** `app/src/main/java/ar/com/biopus/abismocam/TransmissionService.java`
* **Problema:** Cualquier `RuntimeException` puntual dentro de `analyze()` llamaba a `stopSender()` y apagaba la transmisión NDI de forma definitiva.
* **Solución:** Ahora ignora excepciones aisladas de un cuadro y, si se producen errores consecutivos mientras la transmisión está activa (`wanted == true`), detiene y **reinicia automáticamente el emisor NDI** tras 500 ms (`stopSender()` + `startSender()`).

### 6. Optimización de memoria y de interfaz gráfica (CPU, Temperatura y estabilidad en Android 14/15)
* **Archivos:** `app/src/main/java/ar/com/biopus/abismocam/FrameScaler.java` y `app/src/main/java/ar/com/biopus/abismocam/MainActivity.java`
* **Problema:** `FrameScaler` copiaba fila por fila aún cuando no había *padding*, y `MainActivity.refreshTransmission()` ejecutaba `TextView.setText(...)` 30 veces por segundo aunque el texto no hubiera cambiado.
* **Solución:** `FrameScaler` ahora hace copia directa de bloque cuando `stride == width * 4` y alterna entre dos buffers directos (`pixelsA` y `pixelsB`). `MainActivity` ahora verifica `!contentEquals(...)` con protección contra `null` antes de llamar a `setText(...)`, eliminando 90 redibujados innecesarios de UI por segundo y evitando cierres inesperados al iniciar en Android 14/15.
