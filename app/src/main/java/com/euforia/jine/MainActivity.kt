package com.euforia.jine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.euforia.jine.core.audio.AudioSessionViewModel
import com.euforia.jine.ui.screens.ChatViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val chat = ViewModelProvider(this)[ChatViewModel::class.java]
        val audioProvider = ViewModelProvider(this, AudioSessionViewModel.factory(applicationContext))
        val audio = audioProvider[AudioSessionViewModel::class.java]
        val audioHost = audio.bindHost(lifecycle)
        setContent { JineApp(chat, audioHost = audioHost) }
    }
}
