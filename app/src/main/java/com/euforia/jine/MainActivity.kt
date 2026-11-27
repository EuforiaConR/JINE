package com.euforia.jine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import com.euforia.jine.core.audio.ChatSoundCoordinator
import com.euforia.jine.core.audio.SoundPoolEffectPlayer
import com.euforia.jine.ui.screens.ChatViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val chat = ViewModelProvider(this)[ChatViewModel::class.java]
        val sounds = ChatSoundCoordinator(SoundPoolEffectPlayer.create(applicationContext))
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                sounds.release()
            }
        })
        setContent { JineApp(chat, sounds = sounds) }
    }
}
