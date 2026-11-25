package com.example.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerConnection(context: Context) {
    private val _mediaController = MutableStateFlow<MediaController?>(null)
    val mediaController: StateFlow<MediaController?> = _mediaController.asStateFlow()

    init {
        val sessionToken = SessionToken(context, ComponentName(context, SynFixMusicService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            runCatching {
                _mediaController.value = controllerFuture.get()
            }
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        _mediaController.value?.release()
        _mediaController.value = null
    }
}
