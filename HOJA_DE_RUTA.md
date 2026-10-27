# abismoCam — historial y hoja de ruta

Última actualización: **25 de octubre de 2026**. Versión actual: **0.9.0** (código de versión Android: 10).

Aplicación Android de Lisandro Peralta para el proyecto **El abismo de la visibilidad**. Transmite la cámara por NDI, envía señales OSC y guarda fotografías con una cuenta regresiva local.

## Cómo se reconstruyó este historial

Se revisaron la conversación de desarrollo, el código actual, Git, README.md, VALIDACION.md y los informes de pruebas. El primer commit disponible (`e2fb2e8`, 25/09/2026) ya contiene la versión **0.3.0**: no hay commits separados que permitan fechar o numerar con certeza las primeras etapas. Por eso se describen como etapas previas, sin atribuirles números de versión.

En la revisión inicial del 26/09, las versiones 0.4.0 y 0.4.1 estaban implementadas en la carpeta de trabajo, sin un commit propio en el historial entonces revisado. Este documento no implica que los cambios más recientes ya estén publicados en GitHub.

## Historial de cambios

### Primera etapa — MVP de cámara, NDI y OSC

Fecha y número de versión inicial no verificables con el historial disponible.

- Se definió una aplicación personal para Android, con Samsung A51 como teléfono objetivo y varias computadoras receptoras con distintos programas.
- Se implementó captura de cámara y transmisión de video mediante el SDK oficial NDI para Android. Se incorporaron bibliotecas ARM64 para el teléfono y x86_64 para el emulador, junto con los avisos de licencia.
- Se estableció transmisión de video sin audio del micrófono. La app no graba archivos de video.
- Se implementaron dos modos alternativos de envío OSC: lista de destinos individuales con IP y puerto, o broadcast de subred. La lista se conserva al cambiar de modo.
- Se incorporaron configuración persistente, habilitación y edición de destinos, dirección OSC configurable y detección de dirección de broadcast.
- El comportamiento solicitado originalmente para el botón era enviar `1` al presionar y `0` al soltar. Más adelante se reemplazó por el comportamiento ligado a la cuenta regresiva.
- Se prepararon la compilación, el APK instalable y un monitor OSC para Windows (`tools/osc-monitor.ps1`).

### Segunda etapa — interfaz de cámara y fotografía con cuenta regresiva

Etapa anterior a 0.3.0; sin fecha ni versión independiente verificable.

- Se rediseñó la pantalla principal con una interfaz inspirada en una cámara de redes sociales: previsualización amplia, disparador circular inferior y engranaje de ajustes pequeño arriba.
- Se eligió cámara frontal por defecto, con previsualización espejada. El video NDI conserva la imagen original, sin espejo ni elementos de interfaz.
- Se cambió el disparo: **soltar** el botón inicia la cuenta `5, 4, 3, 2, 1`. Mantenerlo presionado no inicia la cuenta.
- Se vinculó OSC a la secuencia: entero `1` al comenzar la cuenta y entero `0` al finalizar. Se envía un mensaje por transición; no un flujo continuo durante el conteo.
- Se bloquearon disparos adicionales durante la secuencia.
- Al finalizar se muestra un flash blanco breve y la foto congelada durante un segundo; después vuelve automáticamente la imagen en vivo.
- Se añadió guardado JPEG en `Pictures/AbismoCam`, miniatura y apertura de la última foto. La fotografía frontal se guarda espejada.
- Se mantuvo el envío de cámara en vivo por NDI durante la cuenta, el flash, el congelado y el guardado. Los efectos son locales al teléfono.
- Se añadió cancelación al abandonar la app, con intento de envío OSC `0`, y bloqueo de acceso a ajustes durante la secuencia.

La foto utiliza el siguiente cuadro disponible al terminar la cuenta. OSC y NDI viajan por separado: el mensaje no identifica un fotograma concreto ni garantiza sincronización exacta entre receptores.

### 0.3.0 — controles protegidos y calidad seleccionable

Fecha documentada: **25/09/2026**. Incluida en el primer commit de Git.

- Se cambió el nombre visible de la aplicación a **Abismo Cam 2**.
- Se fijó el nombre del emisor NDI en **`abismoCam`**.
- Se activó NDI automáticamente al abrir la aplicación con permiso de cámara.
- Se retiró el control de activar/desactivar NDI de la pantalla principal para evitar cambios accidentales. Quedó dentro de ajustes; el indicador principal es informativo.
- Se protegió el acceso a ajustes con una pulsación de **tres segundos** sobre el engranaje.
- Se añadieron resoluciones **nHD 640 × 360**, **qHD 960 × 540** y **HD 1280 × 720**, y selección independiente de **15 o 30 fps**.
- Se estableció **qHD a 30 fps** como configuración inicial de esa actualización, conservando los destinos OSC. Las selecciones posteriores quedan guardadas.
- Se ajustó el cuadro a la resolución elegida mediante recorte central y escalado cuando la cámara entrega otro tamaño. En vertical las dimensiones se invierten: 360 × 640, 540 × 960 o 720 × 1280.
- Se mantuvo NDI funcionando al entrar y salir de ajustes. Guardar cambios de cámara, resolución o FPS reinicia brevemente la transmisión.
- Se trasladaron los archivos temporales de compilación fuera de OneDrive, manteniendo código y entregables en el proyecto.
- Se propuso repetir los valores OSC durante un segundo para reducir pérdidas, pero **se dejó expresamente de lado**: no se implementó esa repetición.

Validación registrada: once pruebas unitarias, tres pruebas instrumentadas y lint sin errores. Se comprobaron el flujo de captura, OSC UDP en el emulador, JPEG y avance de envíos al SDK NDI durante el congelado. Se probaron las seis combinaciones de resolución y FPS; esto no garantiza esos FPS efectivos en el teléfono.

### Repositorio y entregables — 25/09/2026

- Se preparó el repositorio para GitHub Desktop. El historial local comienza en `e2fb2e8`.
- Se corrigió la identidad de correo de Git para usar la dirección privada `noreply` de GitHub y resolver el bloqueo de publicación por privacidad del correo.
- Se quitó `entregables/` de `.gitignore` a pedido del usuario. El commit `9ee7641` incorpora APK, checksum e informes de validación.
- Las bibliotecas nativas `.so`, los archivos locales del SDK y los temporales de compilación siguen excluidos según `.gitignore`; clonar el código requiere volver a incorporar las bibliotecas NDI para compilar un APK con ellas.

### 0.4.0 — selector de transporte NDI

Fecha documentada: **26/09/2026**.

- Se añadió selección **Unicast / Multicast** dentro de los ajustes protegidos. Unicast es el valor inicial.
- El modo se aplica al guardar y queda persistido. Cambiar el selector sin guardar no modifica el emisor.
- Se incorporó configuración del SDK mediante `NDI_CONFIG_DIR` y su archivo privado, con multicast de envío habilitable, TTL 1 y rango `239.255.0.0/16`.
- Al cambiar de transporte se cierra el emisor y se reinicializa el SDK antes de recrear la fuente. Se conserva el nombre `abismoCam`.
- Se agregó indicación del modo configurado y aviso de posible interrupción temporal en los receptores al guardar el cambio.
- Si NDI estaba detenido manualmente, el cambio de configuración no lo inicia por sí solo.
- OSC conserva sus modos individuales/broadcast y no cambia con el transporte NDI.

**Multicast habilitado no significa multicast obligatorio.** La implementación permite negociación; algunos receptores pueden usar unicast. No se implementó una opción que fuerce multicast para todos.

Validación automatizada: prueba de cambio Unicast → Multicast → Unicast, persistencia, configuración del SDK y reanudación de envíos; prueba de regresión de captura aprobada. Informes: `entregables/validacion/transport-results.txt` y `capture-results-v4.txt`.

#### Comprobación de red aportada durante la conversación

- Se planteó un teléfono por Wi-Fi conectado a un TP-Link AX3000 Archer AX55 y unas cinco computadoras por Ethernet. Durante la comprobación, la computadora receptora estaba por Wi-Fi.
- La captura inicial mostraba tráfico TCP desde el teléfono hacia la IP individual de la computadora.
- Después de cerrar y abrir las aplicaciones, Wireshark mostró tráfico UDP del teléfono `192.168.0.228` hacia **`239.255.243.132`**, puerto destino `14972`.
- Esa dirección de destino confirma tráfico multicast en la captura mostrada. Que el protocolo sea UDP, por sí solo, no bastaría para confirmarlo.
- Esta evidencia no demuestra todavía recepción simultánea estable en cinco equipos ni reconexión automática de todos al cambiar de modo sin reiniciar las aplicaciones. Las IP son las de esa prueba, no valores fijos de la app.

### 0.4.1 — nombre, créditos y distribución de información

Fecha de compilación y comprobación: **26/09/2026**.

- Se cambió el nombre visible de la aplicación a **`abismoCam`**, igual al emisor NDI.
- En Acerca de se reemplazó el crédito “Biopus” por **Lisandro Peralta** y se actualizó la versión mostrada.
- Se centró el encabezado **EL ABISMO**, compensando la diferencia de ancho entre el indicador NDI y el engranaje.
- Se trasladó resolución/FPS al bloque inferior: debajo de FOTO y por encima del estado NDI, con tamaño de texto equivalente al de OSC.
- Se conservó el identificador Android de la aplicación para actualizar la instalación existente.
- Se generó el APK actualizado y su checksum SHA-256.

Validación: compilación y once pruebas unitarias correctas; lint con 0 errores y 26 advertencias. Se verificaron en el emulador el centrado del título, la ubicación de los textos y los créditos de Acerca de. No se repitió toda la suite instrumentada para estos cambios cosméticos.

### 0.5.0 — repetición configurable de OSC (30/09/2026)

- Cada transición, tanto `1` como `0`, genera cinco envíos con el mismo destino, dirección y valor.
- Intervalo predeterminado: **50 ms**, con primer envío inmediato y último programado a los 200 ms.
- Selector en ajustes: **10, 25, 50 o 100 ms**. Se guarda sin reiniciar NDI; instalaciones anteriores adoptan 50 ms conservando sus otros ajustes.
- Se aplica tanto a destinos individuales como a broadcast.
- Cancelar la cuenta reemplaza los `1` pendientes por cinco `0`. Un cierre normal de la actividad deja completar esa ráfaga mientras el proceso siga vivo.
- Los receptores deben tolerar duplicados y actuar ante cambios de estado. No hay confirmación de recepción, orden garantizado ni sincronización exacta con NDI.
- Se añadieron pruebas de UDP para los dos valores y los cuatro intervalos, cancelación de repeticiones pendientes y cierre. Se adaptaron las pruebas del flujo Android y se añadió una prueba del selector y su persistencia.

### 0.6.0 — cuenta regresiva configurable (01/10/2026)

- Se añadió un selector de **0 a 15 segundos**, en incrementos de uno, con **5 segundos por defecto**. La selección queda guardada; las instalaciones anteriores conservan sus otros ajustes.
- El tiempo indicado en la pantalla principal y la descripción accesible del disparador reflejan la selección. Guardar el tiempo no reinicia NDI.
- Con 0 s, soltar solicita inmediatamente el siguiente cuadro disponible, sin números en pantalla. Se mantienen el flash, el congelado de un segundo, el guardado JPEG y el bloqueo de disparos adicionales.
- Para 0 s se programan **cinco 1 y luego cinco 0**. Con intervalo OSC de 50 ms, el primer 0 se envía a los 250 ms y el último a los 450 ms. Para 10/25/100 ms esos pares son 50/90, 125/225 y 500/900 ms. La foto no espera esos envíos.
- Para 1–15 s, OSC sigue enviando cinco 1 al comienzo y cinco 0 al final de la cuenta. Los destinos individuales y broadcast usan el mismo comportamiento.
- Se ampliaron las pruebas del reloj para todas las duraciones, del pulso OSC inmediato para los cuatro intervalos y del ajuste persistente. Las pruebas Android incluyen captura sin cuenta y regresión del flujo de cinco segundos; los resultados se detallan en VALIDACION.md.
- Resultado: 16 pruebas unitarias y pruebas Android de 0 s/configuración aprobadas. La regresión de captura de cinco segundos falló en dos intentos por falta de un cuadro nuevo, consistente con el fallo de cámara del emulador ya registrado; queda pendiente de validación física.

### 0.7.0 — encuadre vertical 4:5 (18/10/2026)

- Se sustituyeron las tres resoluciones 9:16 por **384 × 480**, **576 × 720** y **768 × 960**, denominadas Baja, Media y Alta. Media a 30 fps es el valor inicial; continúa la elección independiente de 15/30 fps.
- Vista previa, NDI y fotografía usan el mismo encuadre de sensor 4:5, con recorte central y escalado sin deformación. NDI permanece sin espejo; vista y foto frontal, espejadas.
- La pantalla reserva bandas negras para el encabezado/ajustes y el disparador/información. La ventana 4:5 se adapta al espacio disponible y muestra el encuadre completo. Se centró también el texto FOTO.
- Las bandas no se transmiten ni se guardan. La cuenta, el flash y la foto congelada se presentan dentro de la ventana; NDI continúa en vivo.
- Se migran las preferencias al esquema 4, conservando el nivel de calidad anterior y el resto de ajustes. No se modifica la secuencia OSC de cinco mensajes por valor ni sus intervalos.
- Se agregaron verificaciones de migración, recorte con padding de filas y tamaño de ventana. Las pruebas de cámara comprueban las seis combinaciones de calidad/FPS y las dimensiones del JPEG. Resultados y límites: VALIDACION.md. Validación de esta entrega: 16 pruebas unitarias y 7 pruebas Android aprobadas, lint sin errores y revisión visual de las bandas negras. Falta comprobar el encuadre y el rendimiento con receptores físicos.

### 0.8.0 — identidad NDI por teléfono (23/10/2026)

- Se añadió un identificador configurable para distinguir teléfonos: `abismoCam_Li_0001`. El identificador se aplica al guardar y conserva el número.
- El botón **Renovar fuente NDI** incrementa el contador inmediatamente y recrea el sender si estaba activo; mantiene detenido el que estaba apagado. Usa el identificador guardado, sin aplicar cambios pendientes de otros ajustes.
- Identificador y contador se guardan entre sesiones. El contador utiliza al menos cuatro cifras y continúa con cinco o más cuando sea necesario, sin reiniciarse al llegar a 9999.
- Migración al esquema 5: instalaciones anteriores adoptan `abismoCam_0001` sin perder calidad, cámara, transporte, cuenta ni destinos OSC. Cada teléfono debe configurar un identificador diferente; no hay detección automática de duplicados.
- La renovación ayuda a identificar una fuente nueva, pero no borra fuentes obsoletas de otros programas ni garantiza resolver bloqueos. Los receptores pueden requerir selección manual del nuevo nombre.
- Validación: 18 pruebas unitarias y dos pruebas Android aprobadas; lint sin errores y revisión visual del panel. Falta validar varios teléfonos y receptores físicos. Detalles: VALIDACION.md.

### 0.9.0 — transmisión independiente de la pantalla (25/10/2026)

- Cámara, análisis de cuadros, recorte 4:5 y sender NDI se trasladaron a un servicio en primer plano con ciclo de vida propio.
- Ir al inicio, cambiar de aplicación, pulsar Atrás o bloquear el teléfono mantiene la transmisión. Regresar conecta la vista previa al mismo sender; no renueva su identidad.
- Quitar la aplicación de recientes cierra cámara/NDI y libera los bloqueos de CPU, Wi-Fi y descubrimiento. El servicio no se reinicia automáticamente ni se inicia al arrancar el teléfono.
- Se añadió una notificación con nombre de fuente, acceso a la app y acción **Detener NDI**, y solicitud de permiso de notificaciones en Android 13+.
- El estado NDI detenido se conserva al volver del segundo plano. La sesión de cámara y la notificación siguen activas hasta cerrar desde recientes.
- Se conserva la cancelación de la cuenta al pasar a segundo plano con su ráfaga OSC de 0. El video no se cancela con ella.
- Se añadieron pruebas de continuidad, bloqueo/desbloqueo, regreso, recreación de la actividad, eliminación de la tarea y detención desde notificación: ambas aprobaron. Pasaron 18 pruebas unitarias y siete de ocho regresiones Android; la captura de cinco segundos sigue fallando por falta de un cuadro nuevo en el emulador. Resultados y límites: VALIDACION.md.

## Estado actual y límites conocidos

- APK para instalación manual: `entregables/AbismoCam-debug.apk`. Es una compilación de desarrollo, no una publicación en Google Play.
- Cámara frontal, formato vertical 4:5, Media 576 × 720 a 30 fps por defecto; valores configurables. Sin micrófono ni grabación de video.
- Envío NDI de imagen original; espejo y efectos de captura solo en la presentación local, con fotografía frontal espejada.
- NDI continúa en segundo plano y con pantalla bloqueada mediante un servicio en primer plano. Quitar la app de recientes lo detiene. Android o el fabricante aún pueden interrumpir el proceso.
- OSC UDP no confirma recepción. “Enviado” no equivale a recibido por todas las computadoras.
- Se observó una falla intermitente en el emulador: después de cambios de calidad y recreación de actividades, algunas capturas agotaron el plazo de espera de un cuadro. Pruebas posteriores pasaron, pero la causa no quedó confirmada ni el problema definitivamente resuelto.
- La prueba multicast aportada por el usuario complementa la validación del emulador. Sigue pendiente comprobar rendimiento sostenido y compatibilidad en el montaje completo.

## Hoja de ruta pendiente

### Validaciones pendientes del funcionamiento acordado

- [ ] Comparar el encuadre 4:5 de la pantalla, las fotos y un receptor NDI físico, incluyendo espejo frontal y las tres calidades.
- [ ] Validar transmisión sostenida con pantalla bloqueada, cambio de apps y eliminación desde recientes en cada teléfono, incluyendo ahorro de batería.
- [ ] Probar en el Samsung A51 arranques, permisos, orientación, espejo, colores, guardado en galería y capturas repetidas.
- [ ] Repetir cambios de calidad y capturas para comprobar si aparece la falla intermitente observada en el emulador.
- [ ] Verificar nombres distintos por teléfono, renovación y selección de la fuente nueva en los receptores físicos.
- [ ] Validar recepción NDI simultánea en unas cinco computadoras y los programas que se utilizarán, incluido TouchDesigner.
- [ ] Comprobar Unicast → Multicast → Unicast en la red real, midiendo interrupción y necesidad de reconectar o reiniciar cada receptor.
- [ ] Confirmar el transporte real de los receptores con capturas de red; el selector de la app solo muestra lo configurado.
- [ ] Probar OSC individual y broadcast en el montaje completo y verificar los valores 1/0 de cada secuencia.
- [ ] Medir FPS efectivos, latencia, estabilidad y temperatura durante una sesión sostenida, con teléfono por Wi-Fi y computadoras por Ethernet.
- [ ] Comparar las opciones de resolución/FPS y transporte para elegir la configuración de uso de la instalación.

Estas tareas son verificaciones pendientes; no implican nuevas funciones ni tienen una fecha de entrega acordada.

### Propuestas postergadas o sin implementación acordada

- **Repetición de OSC durante un segundo:** reemplazada por la decisión implementada en 0.5.0: cinco mensajes con intervalo configurable. No queda pendiente la propuesta original de un segundo.
- **Forzar multicast sin permitir unicast:** consultado, pero no implementado. Requiere confirmar una vía compatible con el SDK y con los receptores antes de prometer esa función.

## Referencias del proyecto

- [README.md](README.md): instalación, configuración y compilación actualizadas.
- [VALIDACION.md](VALIDACION.md): pruebas y limitaciones documentadas por versión.
- [Informes de validación](entregables/validacion/): resultados y capturas conservados en el proyecto. Algunos informes se actualizan al recompilar; no son un archivo inmutable de cada versión.
- [Configuración de versión Android](app/build.gradle): versión actual del código.

Para próximas actualizaciones, añadir una entrada con versión, fecha, cambios y pruebas realizadas, y trasladar a esa entrada los pendientes que se completen.
