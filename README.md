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

El catálogo depurado contiene 27 aplicaciones con firma corroborada y una descarga HTTPS comprobada; se retiraron 23 entradas sin una fuente utilizable. Solo se muestran las compatibles con el nivel de Android y las arquitecturas verificadas del dispositivo. Se usan consultas explícitas de paquetes, sin visibilidad general de todas las apps. Aptoide se consulta con SDK, ABI y densidad del dispositivo, excluyendo las etiquetas alpha/beta. Algunas aplicaciones incluyen una alternativa de descarga desde el sitio del desarrollador.

El filtro **Comunes** muestra Facebook, WhatsApp, TikTok y Messenger, en ese orden. Es adicional a las categorías: esas aplicaciones permanecen también en Social y Todas. La búsqueda se combina con el filtro seleccionado. El contador refleja el catálogo visible en cada dispositivo.

El catálogo y la cola muestran los iconos publicados en las fichas oficiales de Google Play, incluidos en el APK para mostrarlos sin conexión. `app/src/main/assets/icons.json` registra la ficha de origen, la imagen y su SHA-256 para las 27 aplicaciones y Play Services. Se pueden actualizar con `node scripts/Update-Icons.cjs` usando Node.js 22.13 o superior.

La comprobación de fuentes de esta versión está registrada en `docs/catalog-availability-0.2.0.json`. Incluye respuestas de metadatos y cabeceras de descarga del APK base y sus splits; no equivale a una instalación de las 27 aplicaciones.

`app/src/main/assets/trust.json` contiene huellas SHA-256 corroboradas mediante los archivos Digital Asset Links de los dominios oficiales. Sus fuentes y fechas quedan registradas. Para actualizarlo:

```powershell
./scripts/Update-Trust.ps1
```

Las huellas nuevas deben revisarse antes de crear un commit. La etiqueta de confianza de Aptoide y el MD5 de sus archivos no se usan como prueba de autoría. Se verifican las firmas criptográficas de todos los APK con `apksig`, el identificador y versión del APK base, y la coincidencia de firmantes del conjunto. Android vuelve a validar el conjunto y la compatibilidad de la actualización al instalar.

Una aplicación nueva necesita una huella corroborada; una actualización también puede utilizar el certificado de su instalación existente. Las entradas sin firma corroborada se excluyen del catálogo. Si una fuente deja de ofrecer una descarga después de esta comprobación, la cola informa del fallo. No se debe desinstalar una aplicación para resolver un conflicto de firmas, porque eso eliminaría sus datos.

## Cola y permisos

Cada lote comienza revisando Google Play Services. Después descarga y verifica todas las aplicaciones antes de abrir la primera confirmación de instalación. Las instalaciones se presentan una tras otra, en el orden del lote; las entradas omitidas, canceladas o con error no bloquean las demás.

En entornos con traducción entre x86 y ARM, como BlueStacks, se conserva Play Services del sistema para evitar la actualización que la plataforma rechaza. En otros dispositivos se intenta actualizarlo si hay una versión compatible y verificable. Un rechazo permanente se recuerda para la versión instalada y la compilación del sistema; se puede volver a comprobar cuando alguna cambia. Si Play Services está ausente, el lote continúa sin instalar el ecosistema Google.

DownloadManager mantiene hasta dos transferencias solicitadas por AppIstan. Room persiste la cola y los identificadores de descarga. Los archivos se copian al almacenamiento interno antes de verificarlos y se conservan hasta instalar o cancelar cada aplicación; se necesita espacio para preparar el lote completo. Hay una sola sesión de instalación activa; las confirmaciones se abren cuando AppIstan está en primer plano. Al cerrar el proceso se recupera el estado desde Room y Android. Si se perdió una confirmación pendiente, Reintentar crea una nueva sesión.

Al terminar, fallar o cancelar una instalación se retira su descarga y se borran directamente los APK base, splits y copias privadas de AppIstan. Reintentar exige limpiar los archivos anteriores antes de volver a descargar. Al abrir la app se vuelve a comprobar la limpieza de las filas terminadas y se eliminan carpetas temporales huérfanas; las carpetas aleatorias de versiones anteriores se limpian cuando no hay un lote activo. Esta limpieza se limita al almacenamiento temporal propio de AppIstan.

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
- Las entradas sin certificados corroborados o sin fuente utilizable se retiran del catálogo. No se han instalado las 27 aplicaciones en dispositivos reales.
- Las distribuciones que requieren archivos OBB se muestran como no disponibles; los juegos pueden descargar contenido propio al abrirse.
- La elección de variantes depende de los metadatos de Aptoide; un conjunto incompleto o incompatible se rechaza.
- En BlueStacks se verifican el catálogo, la actualización con confirmación de Android, el rechazo de un APK alterado y la omisión de una versión anterior, conservando un marcador único de datos. Esto no demuestra que todas las apps bancarias, juegos o servicios funcionen en un teléfono real.
- La página de descarga está preparada en `site/`; cada entrega genera `site/dist/` con el APK y sus metadatos. El despliegue en Cloudflare Pages se realiza posteriormente; consulta `site/README.md`.
