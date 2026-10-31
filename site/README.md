# Sitio de descarga de AppIstan — parte 2

Sitio estático con Astro 7, Bun y Tailwind CSS 4, inspirado en Metrolist y en los colores de AppIstan. Incluye Nunito local con su licencia, iconos oficiales y una captura real de la aplicación. La página compilada no necesita JavaScript para descargar el APK ni abrir las preguntas frecuentes.

## Ejecutar en esta computadora

Requisitos utilizados: Bun 1.4.2 y Node.js 24.15.0. Astro requiere Node.js 22.23.4 o superior. En PowerShell se utiliza `bun.cmd` para evitar el conflicto con el lanzador `bun.ps1` de esta instalación; no es necesario cambiar la política de ejecución.

Desde la raíz del proyecto:

```powershell
cd site
bun.cmd install --frozen-lockfile
bun.cmd run dev
```

Abrir la dirección Local que imprime Astro, normalmente `http://127.0.0.1:4321`. Si está ocupada, Astro selecciona otro puerto. Para detenerlo, pulsa Ctrl+C en la terminal donde lo iniciaste.

Para comprobar y revisar la versión compilada:

```powershell
bun.cmd run test
bun.cmd run build
bun.cmd run preview
```

Desde la raíz también se puede compilar con `./scripts/Build-Site.ps1`. Si PowerShell bloquea los scripts en esta computadora, ejecuta `powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./scripts/Build-Site.ps1`; el ajuste solo afecta a ese proceso. El proceso de entregas Android utiliza ese mismo script. Instalar primero las dependencias del sitio con Bun.

## Organización

- `src/pages/index.astro`: página principal.
- `src/layouts/BaseLayout.astro`: documento, cabecera, metadatos y pie.
- `src/components/`: portada, descarga, captura y preguntas frecuentes.
- `src/assets/`: captura y los seis iconos oficiales utilizados en la portada, tomados de los recursos Android.
- `src/styles/global.css`: Tailwind, Nunito y colores del sitio.
- `public/`: favicon, fuente y cabeceras para el alojamiento estático.
- `scripts/prepare-release.ts`: preparación y verificación de la descarga local.
- `dist/`: resultado estático generado, excluido de Git.

## Descargar siempre el APK

Antes de iniciar desarrollo o compilar, se selecciona la entrega estable más reciente de `../releases/` que tenga `release.json`. La comparación es numérica: 0.10.0 es posterior a 0.9.0. Si la entrega elegida es inválida o su APK no coincide con el tamaño y SHA-256 registrados, el proceso falla antes de modificar la descarga preparada.

El script genera `public/release.json`, la copia con nombre de versión y `public/downloads/AppIstan-latest.apk`. Estos archivos se excluyen de Git. La versión y tamaño visibles se generan desde los metadatos de la misma entrega; no se escriben manualmente en la página.

El único botón **Descargar APK** apunta directamente a `/downloads/AppIstan-latest.apk`, con el nombre de descarga de la versión correspondiente. No abre otra página, una lista de versiones ni GitHub. Esto funciona también sin JavaScript.

Para preparar una entrega específica mediante el script raíz:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./scripts/Build-Site.ps1 -ReleaseDirectory ./releases/0.4.0
```

## Parte 3 pendiente

La publicación en GitHub y Cloudflare Pages se hará posteriormente. Subiremos el código y `bun.lock`; las claves, dependencias, APK y carpetas generadas permanecen excluidos de Git.

Si alojamos los APK en GitHub Releases, se usará la descarga directa del archivo de la última versión. El botón nunca deberá abrir la página de GitHub con las versiones. La obtención de esos archivos durante el despliegue todavía no forma parte del sitio local.
