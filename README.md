# AppIstan

Instalador por lotes para Android 8 o superior, escrito en Kotlin con Jetpack Compose y Material Design 3. Selecciona aplicaciones, consulta versiones compatibles, descarga APK individuales o divididos y presenta las confirmaciones de instalación de Android. Las aplicaciones ya instaladas solo se actualizan cuando hay una versión superior.

## Compilar

Requisitos: JDK 17 o superior, Android SDK 36, Build Tools 36.0.0 y conexión a Google Maven/Maven Central. El proyecto incluye Gradle Wrapper. La preparación local de esta sesión se encuentra en `.tools/`, excluida de Git.

```powershell
./scripts/Build.ps1
```

El APK de desarrollo queda en `app/build/outputs/apk/debug/app-debug.apk`, con identificador `com.appistan.installer.debug`. La entrega pública utiliza `com.appistan.installer`, de modo que ambas instalaciones pueden coexistir.

## Probar en BlueStacks

Usar una instancia Pie de Android 9 o superior y Node.js 22.13 o superior para las comprobaciones ADB. Activar **Configuración → Avanzado → Android Debug Bridge**. La instancia detectada durante el desarrollo utiliza `127.0.0.1:5555` y Android API 28. El puerto se puede cambiar:

```powershell
./scripts/Test-BlueStacks.ps1 -Port 5555
```

La prueba instala AppIstan y una aplicación de prueba propia (`com.appistan.fixture`), actualiza esa fixture de versión 1 a 2 mediante la confirmación nativa y comprueba que conserva sus datos. Incluye comprobaciones de catálogo y rechazo de un APK alterado. La prueba habilita el permiso mediante la pantalla normal de Ajustes de Android, el mismo mecanismo que utiliza el producto.

La prueba permite restaurar la versión 1 de la fixture de desarrollo con ADB para repetir el ciclo; conserva sus datos de prueba y no desinstala otras aplicaciones.

El arnés ADB prepara una cola controlada mediante un proveedor exclusivo de `src/debug`, protegido por el permiso de sistema `DUMP` que posee el shell ADB. Solo acepta la fixture del proyecto y se niega a reemplazar una cola con aplicaciones reales o pruebas activas. La actualización utiliza los controles normales de Android. El proveedor no forma parte del APK público. Se utiliza este mecanismo porque esta instalación de BlueStacks deshabilita `run-as` y cierra su proceso nativo al instalar el APK de instrumentación de Android.

El script utiliza el ADB del SDK en el puerto de servidor local 5038 para no reemplazar ni interferir con el servidor ADB incluido en BlueStacks.

## Fuentes y firmas

El catálogo contiene 50 aplicaciones y consultas explícitas de paquetes; no pide visibilidad general de todas las apps. Aptoide se consulta con SDK, ABI y densidad del dispositivo, excluyendo las etiquetas alpha/beta. Algunas aplicaciones incluyen una alternativa de descarga desde el sitio del desarrollador.

`app/src/main/assets/trust.json` contiene huellas SHA-256 corroboradas mediante los archivos Digital Asset Links de los dominios oficiales. Sus fuentes y fechas quedan registradas. Para actualizarlo:

```powershell
./scripts/Update-Trust.ps1
```

Las huellas nuevas deben revisarse antes de crear un commit. La etiqueta de confianza de Aptoide y el MD5 de sus archivos no se usan como prueba de autoría. Se verifican las firmas criptográficas de todos los APK con `apksig`, el identificador y versión del APK base, y la coincidencia de firmantes del conjunto. Android vuelve a validar el conjunto y la compatibilidad de la actualización al instalar.

Una aplicación nueva necesita una huella corroborada; una actualización también puede utilizar el certificado de su instalación existente. Si faltan firmas o una descarga compatible, la app muestra el motivo. No se debe desinstalar una aplicación para resolver un conflicto de firmas, porque eso eliminaría sus datos.

## Cola y permisos

El primer paso de cada lote revisa y, si hay una versión compatible y verificable, intenta actualizar Google Play Services. Si está ausente o falla, informa del resultado y continúa. Esto no instala el ecosistema Google en dispositivos que no lo incluyen.

DownloadManager mantiene hasta dos transferencias solicitadas por AppIstan. Room persiste la cola y los identificadores de descarga. Los archivos se copian al almacenamiento interno antes de verificarlos e instalarlos. Hay una sola sesión de instalación activa; las confirmaciones se abren cuando AppIstan está en primer plano. Al cerrar el proceso se recupera el estado desde Room y Android. Si se perdió una confirmación pendiente, Reintentar crea una nueva sesión.

Los permisos son Internet, estado de red, instalación desde esta fuente y notificaciones. No necesita root ni iniciar sesión en Google. Android puede exigir confirmaciones o bloquear una instalación por sus propias restricciones. Las apps descargadas pueden necesitar sus propias cuentas o servicios para funcionar.

## Versiones y entregas

`version.properties` es la única fuente de `versionName` y `versionCode`. Usamos Major.Minor.Patch y un código entero creciente. Flujo de publicación:

```powershell
./scripts/Set-Version.ps1 -Part Patch
# Actualizar CHANGELOG.md y crear el commit correspondiente.
./scripts/Release.ps1
```

La primera clave se crea una sola vez con `./scripts/New-ReleaseKey.ps1`. Se conserva en `keystore/`, excluida de Git. La contraseña local está protegida por Windows DPAPI; la entrega también admite `APPISTAN_KEYSTORE_PASSWORD` en el entorno. Respaldar la clave y la contraseña con un método independiente antes de distribuir la app: perderlas impide actualizar las instalaciones públicas con la misma identidad.

Cada entrega incluye un APK firmado, SHA-256, `release.json` con versión, tamaño y commit, y una etiqueta Git `vX.Y.Z`. El script exige un árbol de trabajo limpio y no reutiliza una etiqueta existente. Las salidas quedan en `releases/X.Y.Z/` y no se agregan a Git.

## Límites actuales

- La última versión depende del catálogo de la fuente y de la compatibilidad del equipo. La disponibilidad puede cambiar.
- Las entradas sin certificados corroborados no admiten una instalación nueva. No se han validado las 50 aplicaciones en dispositivos reales.
- Las distribuciones que requieren archivos OBB se muestran como no disponibles; los juegos pueden descargar contenido propio al abrirse.
- La elección de variantes depende de los metadatos de Aptoide; un conjunto incompleto o incompatible se rechaza.
- En BlueStacks se verifican el catálogo, la actualización con confirmación de Android, el rechazo de un APK alterado y la omisión de una versión anterior, conservando un marcador único de datos. Esto no demuestra que todas las apps bancarias, juegos o servicios funcionen en un teléfono real.
- La página de descarga está preparada en `site/`; cada entrega genera `site/dist/` con el APK y sus metadatos. El despliegue en Cloudflare Pages se realiza posteriormente; consulta `site/README.md`.
