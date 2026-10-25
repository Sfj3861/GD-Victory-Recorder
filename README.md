# GD Victory Recorder

Aplicación Android para grabar partidas y completaciones de Geometry Dash.

## Funciones
- Captura de pantalla autorizada por Android.
- Inicio manual, marca manual de victoria y parada manual de respaldo.
- Al marcar la victoria, la grabación se detiene 10 segundos después.
- Editor obligatorio tras la parada para seleccionar el instante inicial y exportar el MP4 recortado.
- Selector de FPS: 24, 30 o 60.
- Selector de calidad: Ahorro (hasta 960×540 y 2,5 Mbps), Equilibrada (hasta 1280×720 y 5 Mbps) y Alta (resolución completa y 10 Mbps).
- El modo Ahorro reduce la resolución de captura antes de codificar; la app no carga fotogramas completos en memoria para procesarlos.
- GitHub Actions intenta compilar un APK de depuración en cada push a `main`.

## Limitaciones
La detección visual automática del 100 % no está implementada: se usa el botón «Marcar victoria». La calidad/FPS efectivos también dependen de las capacidades del dispositivo; si 60 FPS no se admite en un dispositivo concreto, puede fallar el inicio de grabación. El APK de Actions es de depuración, no está firmado para Google Play y debe probarse en un dispositivo Android real.

## Descargar APK
Ve a **Actions** → **Build Android APK** → última ejecución completada correctamente → artefacto **GD-Victory-Recorder-debug**.
