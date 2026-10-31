# Página de descarga

Página estática de AppIstan, inspirada en la sencillez y tipografía redondeada de Metrolist. Contiene un único botón de descarga y no enlaza a GitHub. La fuente Nunito se sirve localmente e incluye su licencia.

Cada entrega generada mediante `scripts/Release.ps1` prepara `site/dist/`: HTML, estilos, tipografía, captura de la app, APK firmado, alias de descarga estable y metadatos. El botón utiliza la última entrega publicada en `release.json`; si JavaScript o los metadatos fallan, mantiene el enlace estable al APK.

Para reconstruir con la entrega local actual:

```powershell
./scripts/Build-Site.ps1
```

Para el despliegue posterior, crear un proyecto de Cloudflare Pages con carga directa y subir **el contenido de `site/dist/`**. Después de cada nueva entrega, volver a desplegar el contenido actualizado. El enlace de descarga conserva su dirección y los metadatos identifican la nueva versión. No subir `keystore/`, `.tools/` ni el repositorio completo.

El paquete comprueba que el APK coincide con su SHA-256 y no supera el límite de 25 MiB por archivo de Pages. El proyecto incluye cabeceras para servir el APK como descarga y evitar que el alias o los metadatos queden desactualizados en caché.
