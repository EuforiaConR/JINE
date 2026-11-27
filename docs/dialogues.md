# Autoría de diálogos de JINE

Cada archivo UTF-8 sin BOM en `app/src/main/assets/dialogues/` contiene una conversación. Usa `schemaVersion` con valor entero `1`, un `id` estable, el nodo de entrada en `entry` y un objeto `nodes` que relaciona cada ID de nodo con su lista de acciones. La ruta del archivo y el ID de la conversación son independientes. El orden de las propiedades de los objetos es libre. El orden de los arrays indica cómo se ejecutan las acciones y cómo se muestran las opciones.

### Acciones de la versión 1

| `type` | Campos adicionales | Comportamiento |
| --- | --- | --- |
| `text` | `key` | Ame envía el texto de esa clave. |
| `wait` | `durationMs` | Espera explícita en milisegundos. Acepta enteros entre 0 y el máximo de `Long`. |
| `choices` | `options` | Muestra una o más alternativas y espera una elección sin límite de tiempo. Cada alternativa contiene `id`, `key` y `next`. |
| `jump` | `next` | Continúa en el nodo indicado. |
| `end` | | Termina el hilo y devuelve el control al chat libre. |

Cada nodo necesita exactamente una acción terminal al final. Usa `choices` para continuar según una decisión, `jump` para ir a otro nodo o `end` para terminar. Todos los nodos deben ser alcanzables desde `entry` y cada ruta debe terminar. Las ramas pueden converger. Las opciones de una misma pregunta deben tener IDs distintos. Se permite reutilizar un ID de opción en otra pregunta.

La opción elegida se convierte en un mensaje de P-chan y continúa por su destino tras los tiempos de lectura y preparación del chat. Con los valores actuales son 650 y 950 ms. `wait` solo representa las pausas adicionales escritas en el archivo.

### Ejemplo lineal

```json
{
  "schemaVersion": 1,
  "id": "greeting_example",
  "entry": "intro",
  "nodes": {
    "intro": [
      { "type": "text", "key": "dialogue.example.greeting" },
      { "type": "wait", "durationMs": 700 },
      { "type": "text", "key": "dialogue.example.good_day" },
      { "type": "end" }
    ]
  }
}
```

### Ejemplo con decisión y convergencia

```json
{
  "schemaVersion": 1,
  "id": "plan_example",
  "entry": "question",
  "nodes": {
    "question": [
      { "type": "text", "key": "dialogue.example.question" },
      {
        "type": "choices",
        "options": [
          { "id": "now", "key": "dialogue.example.choice.now", "next": "now" },
          { "id": "later", "key": "dialogue.example.choice.later", "next": "later" }
        ]
      }
    ],
    "now": [
      { "type": "text", "key": "dialogue.example.reply.now" },
      { "type": "jump", "next": "outro" }
    ],
    "later": [
      { "type": "text", "key": "dialogue.example.reply.later" },
      { "type": "jump", "next": "outro" }
    ],
    "outro": [
      { "type": "text", "key": "dialogue.example.bye" },
      { "type": "end" }
    ]
  }
}
```

### Añadir y verificar un diálogo

1. Guarda el archivo bajo `app/src/main/assets/dialogues/` con extensión `.json`. Usa [la demo del directo](../app/src/main/assets/dialogues/demo/stream.json) como ejemplo completo.
2. Registra cada `key` en [español](../app/src/main/assets/lang/es.json) e [inglés](../app/src/main/assets/lang/en.json), incluidas las claves de las ramas que no elijas durante una prueba. Comprueba ambos catálogos por separado para que el fallback al español no oculte claves ausentes.
3. En una prueba instrumentada, carga el archivo con `DialogueRepository.load(context, "dialogues/demo/tu_archivo.json")`. Pasa la definición al ejecutor para verificar acciones y tiempos, o a `ChatViewModel.requestDialogue` desde Main para probar la integración con el chat.
4. Para una prueba manual de la demo empaquetada, reconstruye la variante **debug**, abre JINE y toca **Debug · Demo narrativa**. Para probar otro archivo temporalmente con ese botón, cambia la ruta del cargador en `app/src/debug/` y reconstruye. Los cambios en assets requieren reconstruir la app.

Si la carga falla, revisa la ruta y la posición del diagnóstico. Errores habituales son una clave duplicada, un campo mal escrito, una espera escrita como string, una coma final o un `next` que no coincide exactamente con el ID de un nodo. El v1 define conversaciones individuales. Los desencadenantes, las historias, los flags, las estadísticas y la selección de archivos por el jugador quedan fuera del formato.
