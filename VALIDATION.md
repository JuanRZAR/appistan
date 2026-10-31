# Validación de AppIstan

## Versión 0.4.0

- 32 pruebas unitarias: las 26 anteriores, 5 de limpieza de archivos y una de cobertura, procedencia e integridad de los 28 iconos.
- BlueStacks: un APK huérfano controlado en una carpeta del formato anterior se eliminó al iniciar la versión nueva.
- Prueba real con Facebook Lite: la primera descarga dejó `34.apk`; al cancelar en Android no quedaron APK. Reintentar creó únicamente `35.apk`, sin conservar el anterior. Tras aceptar y completar la instalación tampoco quedaron APK descargados.
- La aplicación de prueba se desinstaló al terminar y se comprobó que la lista de aplicaciones del emulador coincidía con la anterior a la prueba.
- Las pruebas de archivos comprueban el borrado de APK base, splits y copias privadas, la conservación de otras descargas activas y archivos ajenos, y el rechazo de rutas fuera de las carpetas propias.
- Los iconos se descargaron de sus fichas de Google Play y se incluyen localmente en WebP. La interfaz se revisó en BlueStacks; falta validación en un teléfono físico.

## Versión 0.3.0

- 26 pruebas unitarias: las 15 anteriores, 6 de preparación del lote y orden de instalación, y 5 del tratamiento de Play Services.
- La barrera de instalación contempla aplicaciones en espera, consulta, descarga o verificación; también se comprueban reintentos, errores, cancelaciones y una sola instalación activa.
- BlueStacks: actualización del paquete público conservando las cuatro aplicaciones del lote anterior. El rechazo antiguo de Play Services se recuperó como omisión informativa, manteniendo la versión del sistema.
- Prueba desde la interfaz con Google Calendar (APK base y tres splits) y Facebook Lite: ambos conjuntos descargados antes de la primera confirmación nativa. Al cancelar Calendar, Android abrió automáticamente la confirmación de Lite. Se cancelaron ambas sin instalar aplicaciones de prueba.
- La comprobación utiliza Android 9 con traducción x86/ARM. No se ha validado este nuevo flujo en un teléfono físico.

## Versión 0.2.0

- 15 pruebas unitarias: las 10 comprobaciones anteriores y 5 del catálogo, búsqueda, filtro Comunes, compatibilidad y codificación de la API.
- Fuentes comprobadas mediante metadatos y respuestas HTTPS del APK base y todos sus splits: se conservan 27 aplicaciones y se retiran 23. El registro está en `docs/catalog-availability-0.2.0.json`.
- Comprobación adicional de variantes ARM64 para WhatsApp, WhatsApp Business, YouTube, Google Maps y Waze.
- Interfaz comprobada por ADB en BlueStacks: 22 aplicaciones compatibles con Android 9 y exactamente Facebook, WhatsApp, TikTok y Messenger en Comunes.
- Actualización del mismo paquete público y con la misma clave; no se instalaron variantes de desarrollo, fixtures ni aplicaciones de terceros durante estas comprobaciones.
- La disponibilidad del proveedor puede cambiar después de la comprobación. Se conserva la verificación criptográfica de cada APK antes de instalarlo.

## Versión 0.1.0

Entorno: Windows, BlueStacks Pie64 con Android 9 (API 28). Las pruebas usan el ADB del SDK con servidor aislado en el puerto 5038. No se utilizó root ni una cuenta de Google.

## Comprobaciones automatizadas

- 10 pruebas unitarias: comparación de versiones, ABI, firmantes, metadatos, APK divididos, HTTPS, versiones beta y archivos OBB.
- 4 escenarios ADB con la fixture propia: catálogo, actualización de versión 1 a 2 con confirmación nativa, rechazo de un APK alterado y omisión de una versión anterior.
- La fixture conserva un UUID almacenado antes de actualizar: se compara el mismo valor después de la actualización y de los intentos rechazados.

## Lotes reales desde la interfaz

| Aplicación | Resultado |
|---|---|
| Facebook Lite | Descargada, firma verificada e instalada. Versión 513.0.0.0.56, código 973960550. |
| Google Calendar | Descargada, firmas verificadas e instalada. Versión 2026.41.0-1001919035-release, código 2018354827. Android confirma APK base y tres splits: arm64_v8a, es y xxhdpi. |
| Google Play Services | Se descargó y verificó el conjunto de 87.465.830 bytes y se presentó la confirmación nativa. BlueStacks rechazó la actualización: PackageManager informó que esa versión no está validada para su plataforma. El lote continuó y completó la siguiente aplicación. |

Los resultados verifican el flujo de selección, consulta de versiones, descarga, verificación e instalación. No se afirma que las 50 entradas estén disponibles ni que todas funcionen en un teléfono físico. La restricción de actualización de Play Services observada en BlueStacks se informa al usuario; no se evita mediante privilegios especiales.

## Página de descarga

La parte 2 migra el sitio a Astro 7, Bun y Tailwind CSS 4. Compila sin errores, advertencias ni avisos del comprobador de Astro. Cuatro pruebas Bun comprueban la comparación numérica de versiones estables, la sustitución de las copias generadas, el rechazo de un APK modificado conservando la descarga anterior y el rechazo de rutas fuera de la entrega.

La revisión en Chrome utiliza anchos de 320, 390, 768 y 1440 píxeles: sin desbordamiento horizontal ni imágenes rotas, con un único botón de descarga, foco visible y preguntas frecuentes operativas. El HTML compilado no contiene scripts y conserva la versión y el enlace al APK.

Se pulsó el botón en el navegador y se descargó `AppIstan-0.4.0.apk`: 1.524.649 bytes y SHA-256 `ed15c8aafec50a8c174b9b4dca2e705f03b58b592e8c64735ff32805e0dbbc7a`. La página permaneció en su URL local. También se verificó la respuesta del servidor de desarrollo. GitHub y Cloudflare Pages quedan para la parte 3.

La preparación de GitHub Pages añade tres pruebas, para un total de 7: enlace directo a un archivo de Release, ausencia de copias del APK en el sitio y rechazo de un alias distinto o una URL de página de versiones. La compilación con `APPISTAN_PAGES=true` se comprueba bajo `/appistan/`, con fuente, imágenes y favicon locales. El workflow YAML se valida localmente.

Primer despliegue público comprobado: GitHub Actions terminó correctamente y `https://juanrzar.github.io/appistan/` respondió HTTP 200. Su botón apuntó directamente a la última Release; el APK 0.4.0 descargado respondió como archivo adjunto, con los mismos 1.524.649 bytes y SHA-256 de la entrega local. La Release existente se verificó sin modificar sus archivos.

La publicación automática añade nueve pruebas, para un total de 16: validación del repositorio, creación y verificación de la Release, reanudación de transferencias, reintento de Pages sin volver a subir APK, compatibilidad con la entrega 0.4.0 de tres archivos, rechazo de archivos distintos bajo una versión publicada, validación de etiquetas, comparación numérica de versiones y rechazo de APK corruptos antes de contactar con GitHub.
