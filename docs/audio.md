# Efectos de sonido de JINE

La fase 1.5.1 reproduce tres archivos WAV empaquetados en `app/src/main/assets/audio/sfx/`.

| Archivo | Cuándo suena |
| --- | --- |
| `poko_click.wav` | Una vez cuando el chat acepta un sticker o una opción narrativa. |
| `jine_send_stamp.wav` | Una vez al terminar la aparición de un mensaje nuevo de P-chan. Con escala de animación cero, el final es inmediato. |
| `jine_recieve.wav` | Una vez al comenzar la aparición de cada mensaje nuevo de Ame. |

## Carga, volumen y ciclo de vida

- `MainActivity` crea un SoundPool por Activity y lo comparte entre las variantes debug y release. Precarga los tres WAV mediante descriptores de assets. Cada efecto queda disponible cuando Android confirma su decodificación. Los intentos anteriores a esa confirmación se descartan. Los errores se registran con la etiqueta `JineAudio`.
- `ChatAudioVolumes` recibe valores de master y SFX entre 0 y 1, ambos inicialmente en 1. Su producto es el volumen de cada nueva voz. Llevar el producto a cero detiene las voces y cancela el audio pendiente. Los ajustes son internos y viven en memoria. `setVolumes` recibe el historial actual para usarlo como línea base silenciosa al quitar el mute.
- El pool permite ocho voces de prioridad uniforme. El registro de streams está acotado y detiene el más antiguo al sustituirlo, para que ninguna voz quede fuera del alcance de la pausa. Los sample IDs se conservan aparte de los stream IDs.
- El chat permite efectos mientras está adjunto y la Activity está en `RESUMED`. Al pausar o retirar la pantalla, descarta los efectos de envío pendientes y detiene las voces. El pool queda precargado hasta destruir la Activity. Al reanudar, el historial vigente del ViewModel se toma como línea base silenciosa.
- El coordinador recibe el fin del fade desde el registro de apariciones. El sonido de envío sigue la duración real de la animación, incluida la escala del sistema. Scroll, recibos, idioma y cambios de panel no originan efectos. La disponibilidad del audio es independiente del envío y del ejecutor narrativo.

Esta fase incluye SFX. La música de fondo está reservada para la fase 1.5.2, junto con su volumen y la política compartida de audio focus.

## Comprobación en el dispositivo

1. Abre debug y espera a que cargue la app. El historial inicial debe quedar en silencio.
2. Envía OK y LOVE. Comprueba el click de cada envío, el efecto al terminar cada fade y un sonido de recepción por cada mensaje de Ame. El indicador de leído no debe añadir audio.
3. Abre **Debug · Demo narrativa**. El propio control queda en silencio. Elige una opción y comprueba el click y el sonido al terminar de aparecer el texto de P-chan.
4. Minimiza durante un fade o mientras suena una recepción. Al regresar, solo los mensajes nuevos deben sonar. Repite con la escala de animación en cero para comprobar que el efecto de envío sigue el final inmediato.
5. Envía varios stickers seguidos y recorre el historial. Cambia el idioma o recrea la Activity. Comprueba que los mensajes anteriores quedan en silencio.
