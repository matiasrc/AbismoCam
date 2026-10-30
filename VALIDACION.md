# abismoCam 0.9.1 — estabilidad NDI, pantalla completa 16:9 y rotación 180° — 30 de octubre de 2026

- **Compilación y suite de pruebas:** APK compilado exitosamente con NDI ARM64 y x86_64; 18 pruebas unitarias (`OscRepeaterTest`, `OscTest`, `CaptureSequenceTest`, `SourceIdentityTest`) aprobadas en 0.2 segundos sin fallas ni regresiones.
- **Validación de red y estabilidad NDI:**
  - Verificado en red física con router TP-Link Archer AX55 y 4 computadoras por cable LAN Gigabit y 2 por Wi-Fi.
  - Con `clockVideo = 1` y modo Wi-Fi de baja latencia (`WIFI_MODE_FULL_LOW_LATENCY`), el flujo NDI se mantiene estable y continuo en OBS y TouchDesigner.
- **Validación de ráfaga OSC de 10 paquetes:**
  - Comprobado el envío de ráfagas de 10 paquetes por transición con DSCP prioritario (`0xB8`), eliminando la pérdida de paquetes intermitentes observada con 5 paquetes en redes con tráfico mixto.
- **Validación de pantalla completa y rotación 180°:**
  - Verificada la eliminación de barras negras: el video vertical 16:9 llena el 100% de la pantalla con controles flotantes (overlay).
  - Verificada la rotación de 180° con `targetRotation = Surface.ROTATION_180` en CameraX: tanto la vista previa ("el espejo") como la foto final y el stream NDI se visualizan al derecho cuando el dispositivo está montado boca abajo.

## Historial de validación

# abismoCam 0.9.0 — transmisión en segundo plano — 25 de octubre de 2026

- APK compilado con NDI ARM64 y x86_64; 18 pruebas unitarias aprobadas. Lint: 0 errores y 26 advertencias.
- Dos pruebas Android nuevas aprobadas en el APK final: continuidad de cuadros después de seis segundos en segundo plano y otros seis con pantalla apagada, misma instancia nativa del sender al volver y recrear la actividad, cierre de cámara/sender al eliminar la tarea mediante `finishAndRemoveTask`, y detención mediante la acción de notificación sin reinicio al volver. Informe: `entregables/validacion/background-v9-final.txt` (también contiene el fallo de captura explicado abajo).
- Regresión: siete de ocho pruebas Android aprobaron. Se verificaron seis combinaciones de calidad/FPS, captura inmediata con JPEG y OSC, cancelación OSC, identidad persistente y renovación, cambio Unicast/Multicast y ajustes OSC/cuenta. Informe: `background-v9-regression.txt`.
- La prueba de captura de cinco segundos falló en la serie, en ejecución aislada y en la comprobación final, al esperar el congelado. No se considera aprobada. El diagnóstico final muestra 14 cuadros enviados, ausencia de análisis nuevo durante unos seis segundos y el executor esperando trabajo; la última llamada NDI había retornado. Es compatible con la falta de cuadros observada en versiones anteriores del emulador, pero no confirma la causa ni descarta un problema físico. Informes: `background-v9-capture-isolated.txt` y `background-v9-capture-diagnostic.txt`.
- Se serializó el cierre del SDK y el inicio de sesiones nuevas en el mismo executor del proceso, para evitar superponer operaciones de inicialización/destrucción al cerrar y reabrir rápidamente.
- Verificación visual de la notificación y servicio Android marcado como foreground con la app oculta: `background-v9.png`. La notificación muestra el nombre del sender y permite detener NDI.
- Estas pruebas comprueban envíos al SDK, no recepción externa. Pendiente probar en A51 y los demás teléfonos: pantalla bloqueada durante una sesión prolongada, ahorro de batería, recepción simultánea, eliminación desde recientes y captura de cinco segundos. No se garantiza continuidad si Android mata el proceso o se pierde la red.

## Historial de validación

# abismoCam 0.8.0 — identidad NDI — 23 de octubre de 2026

- APK compilado con bibliotecas NDI ARM64 y x86_64; 18 pruebas unitarias aprobadas. Lint: 0 errores y 28 advertencias; las dos nuevas corresponden a localización del texto del nombre de fuente.
- Pruebas unitarias nuevas: nombres con y sin identificador, mínimo de cuatro cifras, paso de 9999 a 10000, números mayores que un entero de 64 bits, validación del identificador y recuperación de contadores inválidos.
- Dos pruebas Android aprobadas en API 36.1: migración desde esquema 4 sin perder calidad/OSC/transporte/cuenta, guardado y recarga del identificador y contador; renovación activa; aplicación del identificador solo al guardar; conservación del contador al renombrar; renovación y cambio de identificador con NDI detenido; reinicio manual posterior y recreación de actividad manteniendo el nombre.
- La prueba inspecciona el nombre pasado al sender del SDK y verifica que inicia; no hay receptores externos conectados. Informe: `entregables/validacion/sender-v8.txt`.
- Revisión visual del panel de configuración: `entregables/validacion/sender-v8.png`.
- Pendiente: varios teléfonos con identificadores distintos, selección de fuentes nuevas en los receptores físicos y comportamiento ante fuentes obsoletas o bloqueos reales. No se garantiza recuperar una llamada nativa bloqueada cambiando el nombre.
- El primer intento de instalación se hizo antes de finalizar el arranque del emulador y fue rechazado; las pruebas informadas se ejecutaron después de instalar correctamente ambos APK.

## Historial de validación

# abismoCam 0.7.0 — encuadre 4:5 — 18 de octubre de 2026

- APK compilado correctamente con NDI ARM64 y x86_64. Dieciséis pruebas unitarias aprobadas; lint con 0 errores y 26 advertencias.
- Tres pruebas Android de encuadre aprobadas: migración de las tres calidades sin perder otros ajustes, persistencia del esquema 4, recorte desplazado con filas RGBA acolchadas y sin espejo accidental, y ventana 4:5 adaptable a espacios altos y bajos. Informe: `entregables/validacion/aspect-v7-unit-android.txt`.
- Cuatro pruebas de flujo Android aprobadas en el emulador API 36.1: seis combinaciones de calidad/FPS con dimensiones exactas en el cuadro entregado al SDK NDI; controles fuera del área de imagen; ajustes protegidos; captura de cinco segundos con avance de NDI durante el congelado; captura inmediata con JPEG de 576 × 720; ráfagas OSC y cancelación. Informe: `entregables/validacion/aspect-v7-camera.txt`.
- Revisión visual: ventana de 1080 × 1350 píxeles (4:5) en pantalla de 1080 × 2400. Encabezado, disparador y estados quedan en las bandas negras, sin superposición sobre la cámara. Captura: `entregables/validacion/aspect-v7.png`.
- Las pruebas usan cámara sintética del emulador y verifican envíos al SDK; no comprueban recepción NDI externa ni FPS sostenidos en el A51. Pendiente comparar encuadre, orientación y espejo en teléfono, JPEG y receptor real para las tres calidades.
- La regresión de cinco segundos aprobó esta vez. No se considera resuelta la falla intermitente del emulador registrada en versiones anteriores; sigue pendiente la validación física sostenida.

## Historial de validación

# abismoCam 0.6.0 — cuenta regresiva — 1 de octubre de 2026

- Compilación correcta; dieciséis pruebas unitarias aprobadas; lint con 0 errores y 26 advertencias.
- Reloj de captura: verificados todos los tiempos de 1 a 15 s, el caso 0 inmediato sin números, bloqueo de disparos y congelado; se conservan pruebas de cancelación y timeout.
- OSC inmediato: UDP real en loopback verifica cinco 1 y cinco 0 en orden, para 10/25/50/100 ms, límites temporales inferiores y finalización tras cerrar el repetidor. No es una medición de latencia de la red física.
- Android, prueba de 0 s aprobada: mantener pulsado no dispara; soltar inicia la captura sin estado de cuenta; llegan diez mensajes en orden; se guarda un JPEG legible y avanza NDI. Informe: `entregables/validacion/countdown-v6-1.txt`.
- Android, configuración aprobada: migración a 5 s, dieciséis opciones, selección/persistencia de 0/1/5/15, actualización de texto principal y recreación de actividad. Informe: `countdown-v6-3.txt`.
- La regresión Android de cinco segundos falló en ambos intentos al esperar la foto congelada. El registro muestra timeout sin un cuadro nuevo y executor de análisis inactivo, patrón consistente con la limitación de cámara del emulador ya documentada. No se considera una regresión aprobada ni un problema resuelto. Informes: `countdown-v6-2.txt` y `countdown-v6-retry.txt`.
- Revisión visual del nuevo selector: `countdown-v6.png`.
- Pendiente: repetir cuentas de 0, 1, 5 y 15 s, capturas y recepción OSC en el A51 y los receptores físicos; comprobar la falla de cámara conocida.

## Historial de validación

# abismoCam 0.5.0 — repetición OSC — 30 de septiembre de 2026

- Compilación correcta. Trece pruebas unitarias aprobadas; lint: 0 errores, 26 advertencias.
- Nuevas pruebas UDP: cinco mensajes por cada valor 1/0, intervalos 10/25/50/100 ms, dos destinos habilitados y uno deshabilitado, sin sexto mensaje. También se comprueba cancelación de repeticiones antiguas y finalización de los 0 al cerrar el repetidor.
- Prueba Android de configuración aprobada: migración a 50 ms sin perder dirección/calidad, selección y persistencia de las cuatro opciones, y recreación de la actividad.
- Prueba Android de cancelación aprobada: después del primer 0 no aparecen 1 pendientes; se reciben cinco 0.
- En la ejecución conjunta falló la prueba de foto congelada por espera de cuadro, consistente con el fallo intermitente de cámara ya documentado. La repetición aislada aprobó: cinco 1 al inicio, cinco 0 al final, bloqueo de disparos adicionales, JPEG y avance de NDI durante el congelado. No se considera resuelto el fallo intermitente del emulador.
- Informes: `entregables/validacion/osc-v5-results.txt` (incluye el fallo conjunto) y `osc-v5-capture-isolated.txt` (prueba aislada aprobada).
- Pendiente: comprobar duplicados, recepción y pérdidas en los programas y la red física. Los intervalos son nominales; no se garantiza entrega UDP ni recuperación ante cierre forzado del proceso.

## Historial de validación

# Abismo Cam 2 — transporte NDI, versión 0.4.0 — 26 de septiembre de 2026

- Compilación, once pruebas unitarias y lint completados sin errores.
- Prueba instrumentada `NdiTransportTest`: Unicast → Multicast → Unicast. Verifica aplicación solo al guardar, persistencia, archivo oficial del SDK, variable NDI_CONFIG_DIR y reanudación de cuadros enviados al SDK. Nombre, qHD y 30 fps conservados.
- Informe: `entregables/validacion/transport-results.txt`.
- Prueba de regresión de captura aprobada: cuenta regresiva, OSC 1/0, JPEG y avance de NDI durante el congelado. Informe: `capture-results-v4.txt`.
- El selector habilita la negociación multicast; no indica qué transporte terminó usando cada receptor.
- Pendiente: verificar tráfico multicast real, reconexión y recepción simultánea en las cinco computadoras conectadas al Archer AX55. Las pruebas del emulador sin receptores externos no prueban estos puntos.
- Se conserva la limitación de captura intermitente del emulador documentada abajo; no se considera resuelta por este cambio.

## Registro de la versión anterior
# Validación de Abismo Cam 2 (0.3.0) — 25 de septiembre de 2026

## Comprobado

- APK compilado con bibliotecas oficiales NDI ARM64 y x86_64; licencias en `licenses/ndi/`.
- Once pruebas unitarias aprobadas: seis de OSC y cinco del reloj de captura, bloqueo de disparos, cancelación y recuperación si no llega un cuadro.
- Lint: 0 errores, 26 advertencias (dependencias, compatibilidad Android, orientación y localización).
- Tres pruebas instrumentadas aprobadas en Android API 36.1 x86_64: `camera-flow-results-v3.txt` en `entregables/validacion/`.
- Cámara frontal predeterminada. Mantener pulsado no dispara; soltar inicia cinco segundos. Toques adicionales no generan otra secuencia.
- Receptor UDP real dentro del emulador: OSC entero 1 al comenzar y 0 al finalizar; cancelación al abandonar la actividad también envía 0.
- Captura mostrada como bitmap congelado, regreso automático a cámara y JPEG legible guardado mediante MediaStore. Las fotos creadas por la prueba automatizada se eliminan al finalizar.
- El contador de envíos al SDK NDI aumenta durante el conteo y durante el congelado. Esto verifica que la presentación local no detiene el envío nativo; no verifica recepción en otra computadora.

Durante varias ejecuciones del emulador, la captura agotó el plazo de 2,5 segundos sin recibir un cuadro. Ocurrió al encadenar cambios de calidad y recreación de actividades; también se registraron bloqueos gráficos. Se usó la previsualización PERFORMANCE de CameraX y se añadieron registros de diagnóstico. La prueba aislada y la última suite completa pasaron, pero no se confirmó la causa del fallo intermitente. Es necesario repetir arranques y cambios de calidad en el A51; no se considera definitivamente resuelto.

## Cambios verificados en 0.3.0

- Inicio automático de NDI y fuente abismoCam; migración a qHD/30 conservando el formato de configuración.
- Pantalla principal sin control NDI; toque simple en engranaje no abre ajustes y mantenerlo tres segundos sí.
- El contador de cuadros avanza al abrir ajustes, sin detener el emisor.
- Las seis combinaciones nHD/qHD/HD con 15/30 fps se guardan, recargan y producen las dimensiones verticales exactas en el cuadro entregado al SDK NDI.
- OSC continúa enviando una sola vez cada valor; no se añadieron repeticiones.

## Pendiente en el equipo físico

- Samsung A51: comprobar orientación, espejo frontal, colores, galería, permisos, temperatura y capturas repetidas después del arranque.
- Comprobar descubrimiento y recepción NDI con los programas elegidos y varias computadoras simultáneamente.
- Probar broadcast OSC en la red real y medir latencia, cuadros por segundo y diferencia temporal respecto de OSC.
- El rendimiento del emulador no representa el del teléfono; no se garantiza todavía 720p/30 fps ni una cantidad concreta de receptores.

APK: `entregables/AbismoCam-debug.apk`. Configuración y uso: `README.md`.




