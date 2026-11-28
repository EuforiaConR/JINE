# Audio de JINE

JINE reproduce música de fondo con [AndroidX Media3 ExoPlayer 1.11.1](https://developer.android.com/jetpack/androidx/releases/media3#1.11.1) y efectos con `SoundPool`.

| Asset | Cuándo suena |
| --- | --- |
| `audio/music/mainloop_normal.ogg` | Automáticamente al entrar en primer plano con foco. Se repite en bucle y conserva su posición entre pausas. |
| `audio/sfx/poko_click.wav` | Una vez cuando el chat acepta un sticker o una opción narrativa. |
| `audio/sfx/jine_send_stamp.wav` | Una vez al terminar la aparición de un mensaje nuevo de P-chan. Con escala de animación cero, el final es inmediato. |
| `audio/sfx/jine_recieve.wav` | Una vez al comenzar la aparición de cada mensaje nuevo de Ame. |

## Sesión y recursos

- `AudioSessionViewModel` retiene `JineAudioManager`, un ExoPlayer y un SoundPool entre recreaciones de Activity. Usa el contexto de aplicación y libera los recursos cuando se limpia el ViewModel. La posición y los volúmenes viven en memoria. Una sesión nueva comienza desde el inicio de la pista con master y SFX en `1f` y BGM en `0.5f`.
- `Media3MusicPlayer` prepara `asset:///audio/music/mainloop_normal.ogg` una vez con `REPEAT_MODE_ONE`. `pause()` conserva la posición y `play()` continúa desde ella. El OGG se lee directamente de los assets. Un error de música se registra con la etiqueta `JineAudio` y deshabilita el reproductor de esa sesión. Los SFX siguen sujetos al foco y a su propia disponibilidad.
- `SoundPoolEffectPlayer` precarga los tres WAV mediante descriptores. Cada efecto queda disponible tras la confirmación de Android. Los intentos anteriores se descartan. El pool admite ocho voces de prioridad uniforme y mantiene un registro acotado de streams para poder detenerlas.
- `ChatSoundCoordinator` consume interacciones aceptadas y eventos de aparición de todo el historial. El sonido de envío sigue el fade real. Scroll, recibos, idioma y cambios de panel no generan efectos. En debug y release se usa el mismo propietario de audio.

## Foco y volumen

- `JineAudioManager` solicita un único foco `AUDIOFOCUS_GAIN` en primer plano mediante `AudioManager`. ExoPlayer usa atributos de música de juego con su gestión automática de foco desactivada. Una interacción del chat nunca solicita foco. [Política de audio focus de Android](https://developer.android.com/media/optimize/audio-focus)
- Al salir de `RESUMED`, el manager pausa la música, detiene las voces SFX, descarta los efectos de envío pendientes y abandona el foco. Una pérdida transitoria, incluida la petición de ducking, pausa ambas salidas y conserva la solicitud para un eventual `GAIN`. Ante denegación o pérdida permanente, se espera al siguiente resume o a un cambio explícito de un canal de silencio a audible. Los callbacks de solicitudes y pantallas obsoletas se ignoran. La sesión funciona en primer plano, sin servicio de reproducción ni persistencia.
- Los efectos también requieren que el chat esté montado. Al recuperar el permiso para reproducirlos, el manager toma `ChatViewModel.uiState.value.messages` como línea base silenciosa. Así, los mensajes ya publicados y los fades cancelados quedan en silencio. El chat y la narrativa siguen avanzando según su propio estado.
- `AudioSessionViewModel.setVolumes` recibe `ChatAudioVolumes(master, sfx, bgm)`, con valores entre `0f` y `1f`. El volumen efectivo es `master * sfx` para voces nuevas y `master * bgm` para la música. Silenciar BGM pausa el tema en su posición actual. Silenciar SFX detiene las voces y descarta los efectos pendientes. Con ambos canales silenciados se abandona el foco.

## Comprobación en el dispositivo

1. Abre la variante debug. Debe arrancar el tema normal y el historial inicial debe quedar en silencio. Si no suena, revisa Logcat con la etiqueta `JineAudio`.
2. Envía OK, LOVE y varios stickers seguidos. Comprueba que la música continúa, que suena el efecto de envío al acabar cada fade y que cada mensaje de Ame suena una sola vez.
3. Abre **Debug · Demo narrativa**, prueba cada decisión y regresa al selector. El control de demo queda en silencio y la música conserva su posición durante el diálogo.
4. Pulsa Inicio, bloquea la pantalla o cambia a otra app con audio. Comprueba que JINE se silencia. Al volver, revisa que la música continúe desde donde se pausó y que los mensajes anteriores queden en silencio.
5. Recrea la Activity con un diálogo activo. Comprueba que la música sigue desde su posición anterior y que se puede continuar la conversación. Escucha la vuelta del final al comienzo para evaluar la continuidad del bucle.
6. Cierra JINE definitivamente y vuelve a abrirlo. La sesión nueva debe empezar el tema desde el principio.
