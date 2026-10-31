# Historial de cambios

## [Unreleased]

## [0.4.1] - 2026-10-31

- Publicación automática desde el proceso de entregas: creación de Release, subida y verificación de APK y metadatos, y actualización de GitHub Pages.
- Reintento de publicaciones interrumpidas sin recompilar, duplicar Releases ni reemplazar archivos publicados; firma y credenciales conservadas localmente.
- Configuración de GitHub Pages bajo `/appistan/`, workflow con Bun y descarga directa de la última Release estable.
- Comprobación de que el alias público del APK coincide con la entrega firmada y creación automática de ese alias en futuras entregas.
- Migración del sitio de descarga a Astro, Bun y Tailwind CSS 4, con componentes y recursos locales.
- Preparación de la entrega local estable más reciente por versión numérica, verificando tamaño y SHA-256 antes de servir el APK.
- Descarga directa sin JavaScript ni navegación a páginas de versiones; iconos oficiales, captura real y diseño adaptable.
- Pruebas de selección de versión, descarga estable y rechazo de archivos o rutas inválidos. Primer despliegue público del sitio y publicación automática de entregas.

## [0.4.0] - 2026-10-31

- Borrado explícito de APK descargados y copias privadas al instalar, fallar o cancelar, sin depender únicamente de DownloadManager.
- Limpieza obligatoria antes de reintentar una descarga y recuperación de archivos temporales huérfanos de versiones anteriores.
- Serialización de los resultados del instalador con cancelaciones y reintentos para evitar que una respuesta antigua modifique una descarga nueva.
- Iconos oficiales de las 27 aplicaciones y Play Services, incluidos en el APK y visibles en el catálogo y la cola.
- Pruebas de limpieza, protección de archivos ajenos y procedencia e integridad de los iconos.

## [0.3.0] - 2026-10-31

- Descarga y verificación del lote completo antes de solicitar la primera instalación.
- Confirmaciones de Android consecutivas, con una sola instalación activa y progreso separado de descargas.
- Conservación de Play Services del sistema en BlueStacks y otros entornos con traducción x86/ARM para evitar actualizaciones rechazadas por la plataforma.
- Recuperación del rechazo de Play Services registrado en versiones anteriores y recuerdo de rechazos permanentes hasta que cambie la versión instalada o el sistema.
- Pruebas de la separación entre descargas e instalaciones, orden, reintentos y tratamiento de Play Services.

## [0.2.0] - 2026-10-31

- Catálogo depurado: 27 aplicaciones con descargas comprobadas y 23 entradas sin fuente verificable retiradas.
- Filtro Comunes con Facebook, WhatsApp, TikTok y Messenger; conserva su presencia en Social y Todas.
- Contador dinámico y filtrado por versión de Android y arquitectura compatible.
- Corrección de la codificación de la consulta a Aptoide, que rechazaba el relleno Base64 en algunos dispositivos.
- Pruebas del nuevo filtro, la búsqueda combinada, las firmas del catálogo y la consulta de compatibilidad.

## [0.1.0] - 2026-10-31

- Primera versión de AppIstan en Kotlin y Material Design 3.
- Catálogo de 50 aplicaciones, búsqueda, categorías y selección múltiple.
- Revisión inicial de Google Play Services y resolución de versiones compatibles mediante Aptoide.
- Descargas persistentes, verificación de firmas y sesiones de instalación individuales o mediante APK divididos.
- Actualización de aplicaciones existentes sin solicitar desinstalación.
- Pruebas unitarias e integración mediante ADB en BlueStacks.
- Versionado semántico, clave de distribución y metadatos trazables de cada APK.
- Página estática de descarga preparada para Cloudflare Pages, con enlace a la última entrega publicada.
