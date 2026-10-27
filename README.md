# abismoCam · MVP Android

Cámara para **El abismo de la visibilidad**, Lisandro Peralta. Versión 0.9.0. Samsung A51 como dispositivo objetivo; Android 8 o posterior, ARM64. Interfaz en español, orientación vertical, cámara frontal por defecto, sin micrófono. Guarda fotografías, no graba video.

## Estado

El APK de `entregables` incorpora las bibliotecas oficiales `libndi.so` del SDK NDI para Android, en ARM64 para el teléfono y x86_64 para el emulador. La cámara, NDI High Bandwidth y OSC están implementados. Falta validar la recepción y el rendimiento en el Samsung A51 y la red de la instalación. Las licencias del SDK se conservan en `licenses/ndi/`.

El SDK utilizado se instaló en `C:/Users/lisan/AppData/Local/NDI-SDK-Android`. Las bibliotecas `.so` están excluidas de Git; si se copia el proyecto sin ellas, hay que incorporarlas nuevamente con las instrucciones de abajo.

## Uso

1. Instalar `entregables/AbismoCam-debug.apk` en el teléfono y permitir el acceso a la cámara.
2. Conectar teléfono y computadoras a la misma red local, preferentemente Wi-Fi de 5 GHz y computadoras por Ethernet.
3. Mantener el engranaje presionado **3 segundos** para abrir ajustes, elegir un modo OSC y guardar:
   - **Computadoras individuales**: nombre, IPv4 y puerto por receptor; se puede habilitar, editar o eliminar cada destino.
   - **Broadcast**: dirección de broadcast y puerto común. Detectar broadcast calcula la dirección de una interfaz local Wi-Fi/Ethernet. Revisarla si hay varias interfaces. No funciona a través de subredes o redes que lo bloqueen.
4. Configurar los receptores para escuchar OSC UDP en el puerto elegido, en su interfaz de red o `0.0.0.0`, no solamente en `127.0.0.1`.
5. Soltar el disparador circular inicia la cuenta configurada: **5 segundos por defecto**, elegible de **0 a 15 segundos** en pasos de uno. Envía `/camara/disparo` con tipo OSC `,i` y entero `1` al comenzar y `0` al terminar. Con **0 s** solicita la foto al soltar, sin mostrar números; OSC envía cinco `1` y después cinco `0`, como se detalla abajo. La ruta es configurable. Mantener el dedo apoyado no inicia nada; los toques adicionales se bloquean hasta volver a la cámara en vivo.
6. Al terminar: flash blanco breve, foto congelada durante un segundo y regreso automático al video. La foto se guarda como JPEG en **Pictures/AbismoCam** y aparece una miniatura para abrirla. La cámara frontal y su foto se muestran espejadas.
7. NDI comienza automáticamente al abrir la app con permiso de cámara. Seleccionar en los receptores el nombre indicado en ajustes, por ejemplo **abismoCam_Li_0001**. El indicador de la pantalla principal no responde a toques; activar o detener NDI solo está disponible dentro de ajustes. Cada receptor necesita soporte NDI High Bandwidth.

**NDI continúa enviando la cámara original en vivo durante el conteo, flash, congelado y guardado.** Esos efectos solo aparecen en el teléfono; NDI no lleva espejo ni superposiciones. La foto usa el siguiente cuadro disponible al terminar el conteo, a la resolución del video configurado. La ventana de cámara muestra el mismo encuadre 4:5 que sale por NDI y se guarda en la foto, con espejo solo en la vista frontal y su fotografía.

## Transmisión en segundo plano

Desde 0.9.0, la cámara y NDI se ejecutan en un servicio en primer plano, iniciado mientras la aplicación está visible y tiene permiso de cámara. **Ir al inicio, cambiar de aplicación, pulsar Atrás o bloquear la pantalla no detiene NDI.** Volver a la aplicación recupera la vista previa del mismo sender, sin cambiar el nombre ni incrementar el contador.

Para cerrar completamente, **quitar abismoCam de la lista de aplicaciones recientes**: se cierra la cámara, se destruye el sender y se retira la notificación. Forzar detención también corta la transmisión. No se inicia automáticamente al reiniciar el teléfono ni se solicita al sistema reiniciar el servicio después de que lo mate.

La notificación indica la fuente y permite **Detener NDI** o volver a la app. En Android 13 o posterior se solicita permiso para mostrarla. Detener NDI manualmente se conserva al volver del segundo plano; se puede iniciar otra vez desde ajustes. Mientras la sesión de cámara siga abierta, el servicio y su notificación permanecen activos incluso con NDI detenido; quitarla de recientes libera todo.

Si se pasa a segundo plano durante una cuenta, se cancela la foto pendiente y se intenta enviar la ráfaga OSC de 0, igual que antes. El video continúa. La transmisión usa bloqueos de CPU y Wi-Fi mientras vive el servicio para ayudar a mantenerla con la pantalla apagada. Esto consume batería; Android, las restricciones del fabricante, otra app que tome la cámara, una pérdida de red o un cierre forzado pueden interrumpirla. Hay que verificar el bloqueo sostenido y el ahorro de batería en el A51 y los demás teléfonos.

## Nombre de fuente por teléfono

En **Configuración → Cámara**, escribir el **Identificador del teléfono** (por ejemplo `Li`) y tocar **Guardar y volver a cámara**. El nombre resultante es `abismoCam_Li_0001`. Usar un identificador distinto en cada teléfono; la app no comprueba duplicados en la red. Se permiten hasta 24 letras ASCII, números, guiones y guiones bajos. El identificador vacío produce `abismoCam_0001`.

El botón **Renovar fuente NDI** incrementa el contador y lo guarda inmediatamente: `abismoCam_Li_0001` → `abismoCam_Li_0002`. Usa el identificador ya guardado; un texto editado todavía sin guardar no se aplica con ese botón. Si la fuente está activa o iniciándose, se cierra y se crea con el nuevo nombre. Si estaba detenida, permanece detenida.

Cambiar el identificador y guardar conserva el contador y recrea la fuente cuando estaba activa. Tanto el identificador como el número sobreviven al cierre de la app y al reinicio del teléfono. El número tiene un mínimo de cuatro cifras y continúa con `10000` después de `9999`, sin volver a cero. Al actualizar desde versiones anteriores se inicia en `abismoCam_0001`, conservando cámara, calidad, transporte y OSC. Borrar datos o desinstalar elimina estas preferencias locales.

Los receptores pueden necesitar seleccionar la fuente nueva. Renovar no elimina entradas antiguas de sus listas ni garantiza recuperar un bloqueo interno del SDK, la cámara o la red. El botón mantiene las operaciones de cierre y creación en orden; si una llamada nativa no retorna, la renovación también puede quedar esperando. La app sigue llamándose **abismoCam**.

## Encuadre y resolución 4:5

La cámara muestra una ventana vertical **4:5**, con bandas negras para separar la imagen de los controles. El encabezado y el engranaje quedan arriba; el disparador y la información de resolución, FPS, NDI y OSC quedan debajo. En pantallas más bajas, la ventana se reduce para mostrar el encuadre completo sin taparlo con botones. Las bandas, textos, cuenta y flash son locales: no aparecen en NDI ni en el JPEG.

| Calidad | Ancho × alto de NDI y foto |
| --- | --- |
| Baja | 384 × 480 |
| Media (predeterminada) | 576 × 720 |
| Alta | 768 × 960 |

Los FPS se eligen por separado: **15 o 30**, con **30 por defecto**. Las dimensiones ya están expresadas en orientación vertical; no se intercambian. Se recorta el sensor al centro para obtener 4:5 y se escala sin deformar. CameraX comparte el área de sensor entre la vista previa y el análisis utilizado por NDI y la foto. La cámara frontal conserva el espejo en pantalla y en el JPEG; NDI sigue sin espejo.

Al actualizar se conserva el nivel de calidad: nHD pasa a Baja, qHD a Media y HD a Alta. También se conservan FPS, cámara, transporte NDI, cuenta regresiva y configuración OSC. Una instalación nueva usa Media a 30 fps. Los FPS efectivos dependen del teléfono y la red.

## Cuenta regresiva configurable

Mantener el engranaje tres segundos, elegir **Cuenta regresiva** y tocar **Guardar y volver a cámara**. Hay 16 opciones, de 0 a 15 segundos. Las instalaciones anteriores adoptan 5 segundos; luego se recuerda el valor guardado. Cambiarlo actualiza el texto y el indicador de la pantalla principal sin reiniciar NDI ni modificar destinos OSC o calidad de video.

Con 0 segundos no hay espera de conteo: se solicita el siguiente cuadro disponible al soltar, se muestra el flash y la foto congelada un segundo, y se guarda el JPEG. No se espera a completar OSC para capturar; tampoco se detiene la transmisión NDI original.

Para que el receptor pueda detectar ambas transiciones con 0 s, se programan diez mensajes: cinco `1` y luego cinco `0`, separados por el intervalo OSC seleccionado. Con 50 ms: los `1` salen en 0, 50, 100, 150 y 200 ms; los `0` en 250, 300, 350, 400 y 450 ms.

| Intervalo OSC | Primer 0 (duración nominal del estado 1) | Último 0 (duración total) |
| --- | ---: | ---: |
| 10 ms | 50 ms | 90 ms |
| 25 ms | 125 ms | 225 ms |
| 50 ms (predeterminado) | 250 ms | 450 ms |
| 100 ms | 500 ms | 900 ms |

Son tiempos programados, no garantías de latencia ni recepción. Con cuentas de 1 a 15 s se conservan cinco `1` al inicio y cinco `0` al final. La cuenta, el flash y el congelado bloquean nuevos disparos. Si se abandona la app durante el modo inmediato, la ráfaga ya programada puede finalizar mientras el proceso siga vivo; un cierre forzado no garantiza su entrega.

## Transporte NDI

En ajustes (engranaje durante tres segundos), elegir **Unicast** o **Multicast** y tocar **Guardar y volver a cámara**. Unicast es el valor inicial; la selección queda guardada. Cambiar el selector sin guardar no modifica la transmisión. Al guardar un cambio se reinicia el emisor NDI; los receptores pueden mostrar negro o el último cuadro durante la reconexión. Se conserva el nombre configurado de la fuente. Si NDI estaba detenido manualmente, permanece detenido hasta iniciarlo desde ajustes.

Multicast habilita la negociación con cada receptor: no obliga a todos a usarlo. La red debe permitir multicast y administrar correctamente IGMP; un receptor con multicast deshabilitado puede usar unicast. Probar con el teléfono por Wi-Fi de 5 GHz y las computadoras por cable antes de la instalación. OSC es independiente y conserva sus modos individual/broadcast; ambos utilizan las repeticiones configuradas.

El SDK estándar recibe la configuración oficial mediante `NDI_CONFIG_DIR` y el archivo privado `files/ndi/ndi-config.v1.json`. Se escribe de forma atómica, con `ndi.multicast.send.enable`, TTL 1 y rango 239.255.0.0/16. El cambio cierra el emisor, termina la instancia del SDK y vuelve a inicializarlo antes de crear la fuente. No se modifican ajustes del router ni de las computadoras receptoras.

Referencias: [configuración SDK](https://docs.ndi.video/all/developing-with-ndi/sdk/configuration-files), [NDI_CONFIG_DIR y Android](https://docs.ndi.video/all/developing-with-ndi/sdk/platform-considerations).

## Verificar OSC desde Windows

Desde 0.5.0, cada valor (`1` y `0`) se envía **cinco veces**, con el primero inmediato y **50 ms** entre mensajes por defecto: instantes 0, 50, 100, 150 y 200 ms. En ajustes se elige **10, 25, 50 o 100 ms**, que abarcan 40, 100, 200 o 400 ms respectivamente. La selección se aplica al guardar y persiste sin reiniciar NDI; instalaciones anteriores adoptan 50 ms manteniendo sus otros ajustes.

Los receptores deben actuar solo cuando cambia el valor para evitar cinco acciones por transición. UDP sigue sin confirmar entrega ni garantizar orden. Los intervalos son nominales, sujetos a la planificación de Android y la red. Al cancelar la cuenta se cancelan los `1` pendientes y se programan cinco `0`; un cierre normal de la actividad deja completar esa última ráfaga mientras el proceso siga vivo. Un cierre forzado o una pérdida de red puede impedirlo.

En cada computadora, abrir PowerShell en esta carpeta:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\osc-monitor.ps1 -Port 9000
```

Usar `ipconfig` para encontrar la IPv4 de la computadora y agregarla a la app. Permitir recepción UDP para el puerto elegido en el firewall de la red privada si fuera necesario. El monitor imprime la hora, origen, dirección OSC y valor recibido; no cambia el firewall. Cerrar el monitor antes de probar un software que necesite el mismo puerto.

## Incorporar NDI

1. Obtener el **SDK oficial para Android**, con biblioteca **ARM64 / arm64-v8a**, desde https://ndi.video/for-developers/ndi-sdk/download/ y revisar sus términos. Una DLL de Windows o un `.so` de Linux no sirven.
2. Copiar `libndi.so` a `app/src/main/jniLibs/arm64-v8a/libndi.so`. Alternativamente:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\integrate-ndi.ps1 -LibraryPath 'C:\ruta\SDK Android\arm64-v8a\libndi.so'
```

3. Incorporar las dependencias nativas y avisos de licencia que indique el SDK. El script comprueba ELF ARM64, pero no reemplaza la comprobación de que el archivo sea para Android.
4. Recompilar e instalar el APK actualizado. La carga nativa ocurre al iniciar la transmisión. Revisar con un receptor que la imagen tenga orientación y colores correctos, y medir rendimiento con varios receptores.

Se usa `NsdManager` antes de inicializar NDI y un bloqueo multicast durante la transmisión. El adaptador usa las estructuras C `NDIlib_send_create_t` y `NDIlib_video_frame_v2_t`, cuadros RGBA y envío síncrono: el buffer de CameraX se libera después de terminar el envío. Las llamadas NDI están serializadas en el executor de cámara; OSC usa otro executor para no esperar a la compresión de video. Android ARM64 y x86_64 son las arquitecturas configuradas; el emulador requiere su propia biblioteca x86_64 si se desea probar NDI.

## Compilar

Abrir la carpeta en Android Studio o ejecutar:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\build.ps1 -Test
```

Dependencias fijadas: AGP 8.13.2, Gradle 8.13 (con checksum), Java 17 o posterior, Android SDK 36.1 / Build Tools 36.1.0, CameraX 1.4.2, JNA 5.17.0. La primera compilación requiere acceso a Google Maven, Maven Central y Gradle. El script detecta el Java de Android Studio y el SDK estándar del usuario; también respeta `JAVA_HOME` y `ANDROID_HOME` existentes. No hace falta NDK para este adaptador JNA.

Resultado: `entregables/AbismoCam-debug.apk`. Es un APK de desarrollo para instalar manualmente; no es una publicación en Google Play. Los reportes de pruebas y análisis estático se exportan a `entregables/validacion/`.

Las pruebas unitarias cubren OSC, sus repeticiones y el reloj de captura (18 casos). Con un dispositivo o emulador conectado, `gradlew.bat connectedDebugAndroidTest` ejecuta además las pruebas Android: toques, OSC UDP real, captura JPEG, cancelación, recorte 4:5 con filas RGBA acolchadas, migración de calidad, geometría de la ventana, selección de intervalos, transporte NDI y avance de cuadros enviados al SDK durante el congelado. Requieren la biblioteca NDI de la arquitectura correspondiente; no prueban un receptor NDI externo.

Los archivos temporales de compilación se guardan en `%USERPROFILE%/.abismocam/build/`, en una subcarpeta por proyecto, para evitar bloqueos de OneDrive. El código y los entregables permanecen en esta carpeta. En Android Studio puede ejecutarse la tarea raíz `exportDebugApk` para copiar el APK a `entregables`.

## Comportamiento y límites

- OSC individual y broadcast son modos alternativos; la lista de computadoras se conserva al cambiar de modo.
- Se guardan configuración y fotos localmente. No hay audio, servicios de nube ni analítica. Android 8 y 9 requieren permiso de almacenamiento para las fotos; Android 10 o posterior usa MediaStore sin ese permiso.
- Si se abandona la app durante el conteo, se cancela la captura y se intenta enviar `0`. Cancelar el toque antes de soltar no inicia un conteo. Los ajustes se bloquean durante la secuencia. No puede garantizarse el envío si Android mata el proceso, se corta la red o se pierde el paquete UDP.
- UDP no confirma entrega, orden ni sincronía entre computadoras. El estado “enviado” cuenta entregas al sistema operativo, no confirmaciones remotas.
- Video y OSC viajan por separado. Un mensaje OSC no identifica un cuadro NDI ni garantiza sincronización con él.
- La transmisión NDI continúa en ajustes, segundo plano y con la pantalla bloqueada. Guardar cambios de cámara, resolución o FPS la reinicia brevemente. Quitar la app de recientes la detiene; volver desde segundo plano conserva el sender y su estado. Una sesión nueva inicia NDI automáticamente. La pantalla permanece encendida mientras la app está visible.
- Sin pruebas físicas todavía no se garantiza alcanzar 30 fps en todas las resoluciones ni una cantidad concreta de receptores para el A51.

## Referencias y licencias

- SDK y condiciones: https://docs.ndi.video/all/developing-with-ndi/sdk/licensing
- Android / descubrimiento: https://docs.ndi.video/all/developing-with-ndi/sdk/platform-considerations
- OSC 1.0: https://opensoundcontrol.stanford.edu/spec-1_0.html
- CameraX: https://developer.android.com/media/camera/camerax/analyze
- JNA: https://github.com/java-native-access/jna (Apache-2.0 / LGPL-2.1-or-later, según sus términos).
- AndroidX y Gradle: Apache-2.0 y avisos de sus respectivas distribuciones.

NDI® is a registered trademark of Vizrt NDI AB. Este proyecto personal no está afiliado a Vizrt. Los componentes externos mantienen sus propias licencias.


