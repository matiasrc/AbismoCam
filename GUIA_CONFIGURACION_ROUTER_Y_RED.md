# Guía Paso a Paso: Configuración del Router TP-Link Archer AX55 (AX3000) y Dispositivos de Red

Esta guía detalla paso a paso cómo configurar el router **TP-Link Archer AX55 (AX3000)** y cómo conectar cada equipo del montaje (**1 celular emisor NDI/OSC**, **3 computadoras por cable Ethernet** y **4 computadoras receptoras OSC por Wi-Fi**) para evitar cortes y congelamientos en el streaming de NDI.

*(Se incluyen los nombres de los menús en **Español** y en **Inglés** entre paréntesis, según el idioma en que esté configurado el firmware del router).*

---

## Esquema de Red Objetivo

```
                       [ Router TP-Link Archer AX55 ]
                        /             |             \
   (Wi-Fi 5 GHz Exclusivo)    (Wi-Fi 2.4 GHz)     (Cable LAN Gigabit)
              /                       |                       \
  [ Celular Samsung A51 ]    [ 4 PCs Receptoras ]    [ PC 1, PC 2 y PC 3 ]
  (Video NDI + Disparo OSC)   (Solo reciben OSC)     (Reciben/Procesan NDI)
                                                     *Con su Wi-Fi APAGADO*
```

* **¿Por qué dividir el Wi-Fi así?**
  El video NDI de alta calidad requiere flujo continuo y sin interrupciones de aire (*airtime*). Las 4 computadoras que reciben el disparo por OSC solo reciben unos pocos bytes cuando alguien presiona el botón. Al pasar esas 4 computadoras a la banda de **2.4 GHz**, la antena de **5 GHz** del router queda **100% dedicada y exclusiva para el celular**, eliminando colisiones de paquetes en el aire.

---

## Paso 1: Ingresar al Panel de Administración del Router

1. Sentate en cualquiera de las 3 computadoras que están conectadas por **cable de red (LAN)** al router (PC 1, PC 2 o PC 3).
2. Abrí el navegador web (Chrome o Edge) y escribí en la barra de direcciones:
   * **`http://192.168.0.1`** o **`http://tplinkwifi.net`** y presioná **Enter**.
3. Ingresá la contraseña de administrador del router para iniciar sesión.
4. En la barra superior de la pantalla, hacé clic en la pestaña **Avanzado** (**Advanced**). Todas las configuraciones siguientes se hacen dentro de esa pestaña.

---

## Paso 2: Separar las Bandas Wi-Fi (2.4 GHz y 5 GHz) y Fijar el Canal

Por defecto, el router viene con **Conexión Inteligente (Smart Connect)** activada, que une las dos bandas bajo un mismo nombre. Si eso queda activo, el celular puede saltar de 5 GHz a 2.4 GHz por un segundo cuando hay interferencia, lo que corta o congela inmediatamente el stream NDI en OBS.

1. En el menú lateral izquierdo, entrá a **Inalámbrico** (**Wireless**) → **Configuración Inalámbrica** (**Wireless Settings**).
2. Arriba de todo vas a ver la opción **Conexión Inteligente** (**Smart Connect**): **DESMARCALA (Apagala)**.
   * Al desmarcarla, la página mostrará dos secciones independientes: **2.4 GHz** y **5 GHz**.

### A. Configuración de la sección 2.4 GHz (Para las 4 PCs de OSC por Wi-Fi)
* **Habilitar radio inalámbrica (Enable):** Marcado (Activado).
* **Nombre de red (Network Name / SSID):** Ponele un nombre identificable, por ejemplo: **`Abismo_OSC_24G`**.
* **Seguridad (Security):** `WPA2-PSK [AES]` (o `WPA2/WPA3-Personal`) con tu contraseña.
* **Modo (Mode):** Predeterminado (`802.11b/g/n/ax mixed`).
* **Ancho de canal (Channel Width):** **`20 MHz`** (es el ancho más estable en 2.4 GHz).
* **Canal (Channel):** Elegí **`1`**, **`6`** u **`11`** (no lo dejes en *Auto*).
* **Potencia de transmisión (Transmit Power):** **`Alta` (`High`)**.

### B. Configuración de la sección 5 GHz (EXCLUSIVA para el Celular con `abismoCam`)
* **Habilitar radio inalámbrica (Enable):** Marcado (Activado).
* **Nombre de red (Network Name / SSID):** Ponele un nombre distinto al de 2.4 GHz, por ejemplo: **`Abismo_CAM_5G`**.
* **Seguridad (Security):** `WPA2-PSK [AES]` con tu contraseña.
* **Modo (Mode):** Predeterminado (`802.11a/n/ac/ax mixed`).
* **Ancho de canal (Channel Width):** Seleccioná **`40 MHz`** (o `20/40 MHz`).
  * **¡MUY IMPORTANTE!** **Desmarcá `80 MHz` y `160 MHz`**. Los anchos de 80/160 MHz obligan al router a usar canales **DFS (Dynamic Frequency Selection)**. Cuando un router usa canales DFS, la normativa lo obliga a escanear radares periódicamente; durante ese escaneo corta el Wi-Fi entre 1 y 10 segundos y congela NDI.
* **Canal (Channel):** Cambialo de *Auto* a **`36`** (o bien **`40`**, **`44`** o **`48`**).
  * **¡MUY IMPORTANTE!** Usá únicamente **36, 40, 44 o 48**. Son canales bajos **NO-DFS** que jamás se apagan por radar ni cambian solos.
* **Potencia de transmisión (Transmit Power):** **`Alta` (`High`)**.

3. Hacé clic en el botón **Guardar** (**Save**) abajo a la derecha. El Wi-Fi del router se reiniciará durante unos 10 segundos.

---

## Paso 3: Desactivar Ahorro de Energía Inalámbrico del Router (TWT, OFDMA y Airtime Fairness)

El estándar Wi-Fi 6 incluye funciones pensadas para ahorrar batería en celulares durmiendo la comunicación entre paquetes. Para streaming de video NDI en tiempo real, esas funciones provocan micro-cortes:

1. En el menú lateral izquierdo del router, justo debajo de donde estabas, entrá a **Inalámbrico** (**Wireless**) → **Configuración Adicional** (**Additional Settings**).
2. Verificá y dejá estas opciones exactamente así:
   * **TWT (Target Wake Time):** **Desactivado** (**Off** / desmarcado). *(Crítico: si está activo, negocia pausas de sueño con el teléfono).*
   * **OFDMA / MU-MIMO:** **Desactivado** (**Off** / desmarcado). *(Como el celular va a estar solo en la banda de 5 GHz, OFDMA/MU-MIMO no aporta beneficio y puede introducir latencia en el chip del Samsung A51).*
   * **Equidad de tiempo de aire (Airtime Fairness):** **Desactivado** (**Off** / desmarcado).
   * **WMM (Wi-Fi Multimedia):** **Activado** (**On** / marcado — es obligatorio para mantener velocidad en Wi-Fi).
   * **Aislamiento AP (AP Isolation):** **Desactivado** (**Off** / desmarcado — si se activa, los dispositivos Wi-Fi no pueden verse entre sí).
3. Hacé clic en **Guardar** (**Save**).

---

## Paso 4: Verificar IGMP Snooping / Multicast en la LAN

1. En el menú lateral izquierdo, entrá a **Red** (**Network**) → **IPTV / VLAN**.
2. Asegurate de que **Proxy IGMP (IGMP Proxy)** e **IPTV** estén en su estado estándar o con **IGMP Snooping** habilitado si se llegara a usar Multicast, pero recordá que **en este montaje recomendamos usar `Unicast`** tanto en `abismoCam` como en las PCs para no inundar el Wi-Fi con tráfico de video entre las PCs.

---

## Paso 5: Asignar IPs Fijas a Todos los Equipos (Reserva DHCP)

Si se apaga la electricidad de la sala y al día siguiente se vuelve a encender todo, el router podría darle otra IP distinta a alguna de las computadoras y dejarían de llegar los mensajes OSC. Para que cada equipo tenga **siempre la misma IP**:

1. Conectá primero todos los equipos al router (el celular, las 3 PCs de cable y las 4 PCs de OSC).
2. En el menú lateral izquierdo del router, entrá a **Red** (**Network**) → **Servidor DHCP** (**DHCP Server**).
3. Bajá hasta la sección **Reserva de Direcciones** (**Address Reservation**).
4. Hacé clic en **Agregar** (**Add**) y luego en **Ver dispositivos conectados** (**View Connected Devices**).
5. Seleccioná de la lista el dispositivo (vas a ver su nombre, su dirección MAC y su IP actual, por ejemplo `192.168.0.101`) y hacé clic en **Guardar** (**Save**).
6. Repetí este punto para:
   * El **Celular Samsung A51**
   * **PC 1, PC 2 y PC 3** (las conectadas por cable)
   * Las **4 computadoras receptoras de OSC por Wi-Fi**
7. Anotá en un papel o archivo de texto qué IP fija quedó asignada a cada computadora para cargarlas en la lista de **Destinos OSC (Computadoras individuales)** dentro de `abismoCam`.

---

## Paso 6: Conexión Correcta en Cada Equipo

Una vez aplicados los pasos anteriores en el router, hacé este chequeo físico en cada equipo:

### 1. En el Celular (Samsung A51)
1. Entrá a *Ajustes → Conexiones → Wi-Fi*.
2. Si aparece conectada la red vieja o la red `Abismo_OSC_24G`, tocala y elegí **"Olvidar red" (Forget network)**. *(Esto es fundamental: si el celular tiene guardadas las dos redes, Android puede cambiar solo a 2.4 GHz sin avisar).*
3. Conectalo **únicamente** a **`Abismo_CAM_5G`** y asegurate de que tenga tildado *Reconectar automáticamente*.
4. Dentro de las opciones de esa red Wi-Fi en el teléfono (tocando el engranaje al lado de `Abismo_CAM_5G` → *Ver más* → *Tipo de dirección MAC*), seleccioná **"MAC del teléfono" (Phone MAC)** en lugar de *MAC aleatoria*, para que el router siempre lo reconozca con la misma IP reservada en el Paso 5.

### 2. En las 4 Computadoras que reciben OSC por Wi-Fi
1. Conectalas por Wi-Fi a **`Abismo_OSC_24G`**.
2. Si tenían guardada la red `Abismo_CAM_5G`, hacé clic derecho sobre ella en Windows y elegí **"Olvidar" (Forget)**, para que ninguna computadora ocupe aire en la banda de 5 GHz del celular.

### 3. En las 3 Computadoras conectadas por Cable LAN (PC 1, PC 2 y PC 3)
1. Verificá que el cable de red Ethernet esté firme en los puertos LAN naranjas/negros del router (no en el puerto azul WAN).
2. En Windows, hacé clic en el ícono de Red (abajo a la derecha, junto al reloj) y **APAGÁ el botón de Wi-Fi**.
   * *¿Por qué?* Si una PC tiene el cable Ethernet enchufado y además el Wi-Fi prendido al mismo router, tiene dos direcciones IP en la misma subred. NDI intenta enviar y recibir por ambas placas a la vez y termina congelando la imagen.
