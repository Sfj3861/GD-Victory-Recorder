# GD Victory Recorder

Aplicación Android para grabar partidas y completaciones de Geometry Dash.

## Funciones de esta primera versión
- Captura de pantalla autorizada por Android.
- Inicio manual, marca manual de victoria y parada manual de respaldo.
- Al marcar la victoria, la grabación se detiene 10 segundos después.
- Editor obligatorio tras la parada para seleccionar el instante inicial y exportar el MP4 recortado.
- GitHub Actions compila un APK de depuración en cada push a `main`.

## Limitación
La detección visual automática del 100 % todavía no está implementada: en esta versión se usa el botón «Marcar victoria». El APK generado por Actions es de depuración, no está firmado para Google Play y debe probarse en un dispositivo Android real.

## Descargar APK
Ve a **Actions** → **Build Android APK** → última ejecución → artefacto **GD-Victory-Recorder-debug**.
