package com.euforia.jine.core.audio

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

internal class AudioSessionViewModel(private val audio: JineAudioManager) : ViewModel() {
    fun bindHost(lifecycle: Lifecycle): JineAudioManager.Host {
        val host = audio.attachHost()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = host.resume()
            override fun onPause(owner: LifecycleOwner) = host.pause()

            override fun onDestroy(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
                host.detach()
            }
        })
        return host
    }

    fun setVolumes(volumes: ChatAudioVolumes) = audio.setVolumes(volumes)

    override fun onCleared() = audio.release()

    companion object {
        fun factory(context: Context) = viewModelFactory {
            val application = context.applicationContext
            initializer {
                AudioSessionViewModel(
                    JineAudioManager(
                        Media3MusicPlayer.create(application),
                        ChatSoundCoordinator(SoundPoolEffectPlayer.create(application)),
                        AndroidAudioFocusController(application)
                    )
                )
            }
        }
    }
}
