# Validación de AppIstan 0.1.0

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

La página estática se revisa en Chrome en tamaños de escritorio y móvil. La entrega se empaqueta con el APK firmado y metadatos de versión y SHA-256. El despliegue en Cloudflare Pages queda pendiente.
