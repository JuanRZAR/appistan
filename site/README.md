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
- `src/assets/`: captura, fuente Nunito y los seis iconos oficiales utilizados en la portada, tomados de los recursos Android.
- `src/styles/global.css`: Tailwind, Nunito y colores del sitio.
- `public/`: favicon, licencia de Nunito y cabeceras para el alojamiento estático.
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

## GitHub Pages — parte 3

Repositorio: `https://github.com/JuanRZAR/appistan`. La dirección prevista del sitio es `https://juanrzar.github.io/appistan/`.

`.github/workflows/pages.yml` instala Node.js y Bun, obtiene la última Release estable con GitHub CLI, verifica los APK, ejecuta las pruebas y compila Astro. Publica únicamente `site/dist/` en Pages. Las claves, APK y dependencias permanecen excluidos de Git; el APK público se aloja como archivo adjunto de una Release.

La publicación se integra en `scripts/Release.ps1`: después de compilar y firmar, sube automáticamente los archivos a una Release y solicita el despliegue de Pages. No es necesario crear la Release ni adjuntar archivos desde la web. El proyecto debe estar guardado en un commit de `main`, con `origin` apuntando al repositorio de GitHub y acceso de publicación mediante GitHub CLI o la autenticación existente de Git.

Para publicar o reintentar una entrega ya generada, desde la raíz:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./scripts/Publish-Release.ps1
```

El publicador habilita Pages con origen GitHub Actions si aún no está configurado y solicita el workflow. El enlace público se muestra cuando el trabajo **Publish AppIstan to GitHub Pages** termina correctamente. La firma sigue siendo local; no se suben claves al repositorio ni a los secretos de Actions.

En este modo, `APPISTAN_PAGES=true` configura las rutas bajo `/appistan/`. `APPISTAN_RELEASE_DIR` identifica la carpeta descargada por GitHub CLI y `APPISTAN_DOWNLOAD_URL` establece el enlace directo a `https://github.com/JuanRZAR/appistan/releases/latest/download/AppIstan-latest.apk`. Se comprueba que ese archivo sea idéntico al APK versionado antes de generar los metadatos públicos. Los APK no se copian al sitio de Pages.

El botón inicia la descarga del archivo; no abre la página de versiones. No necesita JavaScript ni una consulta a la API desde el navegador. La página obtiene la versión y tamaño durante la compilación. El publicador solicita explícitamente el workflow después de publicar o verificar la Release, incluso al reintentar; así no depende de que GitHub propague un evento de Release creado por un token de Actions. Los cambios del sitio en `main` también ejecutan el despliegue.

Para las entregas siguientes, incrementar la versión, guardar el cambio en Git y ejecutar `scripts/Release.ps1`. La publicación incluye el APK versionado, `AppIstan-latest.apk`, `release.json` y el archivo SHA-256. Si una transferencia falla, la entrega permanece como borrador y se puede reintentar sin generar otro APK. Una entrega ya publicada se verifica sin reemplazar sus archivos. El desarrollo local conserva sus rutas y descargas propias, sin necesitar GitHub.
