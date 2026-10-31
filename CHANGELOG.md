# Historial de cambios

## [Unreleased]

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
