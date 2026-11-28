# Stickers de JINE

JINE mantiene un catálogo por remitente. `stickers/pchan.json` contiene las ocho opciones del selector y `stickers/ame.json` reúne los ocho stickers recibidos de Ame. Cada entrada tiene ID, texturas por idioma, clave de descripción accesible, emoción y orden. Los órdenes y las emociones de Ame son metadatos iniciales de organización.

## Catálogos y mensajes

- `StickerRepository` usa el mismo parser para ambos paquetes. Verifica el ID de paquete esperado, los campos requeridos, la unicidad de IDs y órdenes dentro del paquete, y la presencia de la variante española. Abre las texturas declaradas y admite únicamente rutas PNG dentro del directorio de ese paquete. Las ocho entradas incluidas de Ame tienen variantes `es` y `en`. Si falta una variante opcional de idioma, `Sticker.getTexture` usa `es`.
- `StickerCatalogs` indexa cada paquete por separado. El historial resuelve `MessageContent.Sticker(packId, stickerId)` solo cuando el remitente coincide con el paquete. Si falla un catálogo, el otro sigue disponible. Los mensajes que no pueden resolverse muestran el aviso localizado. El selector y la validación de envío usan únicamente P-chan.
- `ChatMessageItem` muestra los stickers de Ame por sí solos, a la izquierda, con **72 dp** y **40 dp** de margen adicional sobre el padding del historial. El margen es provisional y puede afinarse al probar en Android. La descripción es accesible y la textura conserva su transparencia y el filtrado de `PixelImage`. Los stickers de P-chan siguen a la derecha con su recibo cuando corresponde.
- El historial usa el fade habitual. Cada sticker nuevo de Ame solicita un solo `jine_recieve.wav` mientras el chat tenga permiso de audio. Al desplazarse o cambiar de idioma se conserva el ID del mensaje. El historial inicial y los mensajes acumulados fuera de una sesión de presentación activa quedan en silencio.

## Demo en Android Studio

1. Ejecuta la variante **debug** y abre **Debug · Stickers de Ame** cuando el chat esté disponible. La demo parte de una historia independiente con un texto de Ame y un sticker leído de P-chan. El panel del selector está oculto dentro de la demo.
2. Pulsa **Añadir sticker de Ame** hasta ver los ocho en orden. Usa **English** o **Español** y desplázate para comparar imágenes y descripciones. Cada sticker nuevo usa el fade y la recepción corta del historial.
3. Pulsa **Volver al chat**. El historial real conserva sus mensajes y su posición de scroll. La música comparte la misma sesión y sigue durante la demo. Al reabrirla, el historial de muestra empieza de nuevo.

La demo vive en `src/debug`. La fase 2.1b incorporará el envío mediante JSON narrativo. La fase 2.1c incorporará las respuestas libres con texto y stickers.
