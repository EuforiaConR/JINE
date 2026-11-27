package com.euforia.jine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.euforia.jine.ui.screens.ChatViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val chat = ViewModelProvider(this)[ChatViewModel::class.java]
        setContent { JineApp(chat) }
    }
}
