# GD Victory Recorder

Aplicación Android para grabar partidas y completaciones de Geometry Dash.

## Funciones
- Captura de pantalla autorizada por Android.
- Inicio manual, marca manual de victoria y parada manual de respaldo.
- Al marcar la victoria, la grabación se detiene 10 segundos después.
- Editor obligatorio tras la parada para seleccionar el instante inicial y exportar el MP4 recortado.
- Selector de FPS: 24, 30, 60, 90, 120, 144 y hasta 240 FPS.
- Selector de calidad: Ahorro (hasta 960×540 y 2,5 Mbps), Equilibrada (hasta 1280×720 y 5 Mbps) y Alta (resolución completa y 10 Mbps).
- El modo Ahorro reduce la resolución de captura antes de codificar; la app no carga fotogramas completos en memoria para procesarlos.
- GitHub Actions intenta compilar un APK de depuración en cada push a `main`.

## Limitaciones
La detección visual automática del 100 % no está implementada: se usa el botón «Marcar victoria». Los FPS seleccionados son una frecuencia solicitada al codificador, no una garantía de FPS efectivos. El resultado depende de la frecuencia de actualización de la pantalla, Android, el codificador y la resolución. En muchos dispositivos 144 o 240 FPS no estarán disponibles para grabación de pantalla y el inicio puede fallar o producir menos fotogramas. El APK de Actions es de depuración, no está firmado para Google Play y debe probarse en un dispositivo Android real.

## Descargar APK
La versión de prueba se publicará en [GitHub Releases](https://github.com/Sfj3861/GD-Victory-Recorder/releases). Al estar marcada como preliminar, debe probarse antes de confiar en ella para grabaciones importantes.

Si una compilación aún no aparece en Releases, puedes consultar [Actions](https://github.com/Sfj3861/GD-Victory-Recorder/actions) y descargar el artefacto **GD-Victory-Recorder-debug** de la ejecución correcta más reciente.
