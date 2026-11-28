package com.euforia.jine.core.assets

object JineAssets {
    object Ui {
        const val WINDOW_BASE = "textures/ui/windowbase_active.png"
        const val WINDOW_ICON = "textures/ui/windowbase_icon.png"
        const val APP_ICON = "textures/ui/app_jine_icon.png"
        const val BUTTON_CLOSE = "textures/ui/button_close.png"
        const val BUTTON_MAXIMIZE = "textures/ui/button_maximize.png"
        const val BUTTON_MINIMIZE = "textures/ui/button_minimize.png"
        const val SCROLL_BAR = "textures/ui/scroll_bar.png"
        const val ONE_PIXEL = "textures/ui/one_pixel.png"
    }

    object Dialogue {
        const val BALLOON = "textures/dialogue/balloon_dialogue.png"
        const val TAIL = "textures/dialogue/tail_dialogue.png"
    }

    object Dialogues {
        const val DIALOGUES_DIRECTORY = "dialogues"
        const val STREAM_DEMO = "$DIALOGUES_DIRECTORY/demo/stream.json"
    }

    object Audio {
        object Music {
            const val MAINLOOP_NORMAL = "audio/music/mainloop_normal.ogg"
        }

        object Sfx {
            private const val DIRECTORY = "audio/sfx"
            const val CLICK = "$DIRECTORY/poko_click.wav"
            const val SEND_STAMP = "$DIRECTORY/jine_send_stamp.wav"
            const val RECEIVE = "$DIRECTORY/jine_recieve.wav"
        }
    }

    object Backgrounds {
        const val JINE = "textures/backgrounds/jine_bg.png"
    }

    object Avatars {
        const val AME = "textures/avatars/icon_jine_ame.png"
    }

    object Languages {
        const val SPANISH = "lang/es.json"
        const val ENGLISH = "lang/en.json"
    }

    object Stickers {
        const val TEXTURES_DIRECTORY = "textures/stickers"
        const val PCHAN_DIRECTORY = "$TEXTURES_DIRECTORY/pchan"
        const val AME_DIRECTORY = "$TEXTURES_DIRECTORY/ame"
        const val PCHAN_CATALOG = "stickers/pchan.json"
        const val AME_CATALOG = "stickers/ame.json"
    }
}
