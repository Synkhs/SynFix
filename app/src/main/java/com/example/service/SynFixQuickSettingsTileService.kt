package com.example.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.example.player.PlaybackRuntimeCoordinator
import com.example.player.SynFixMusicService

/**
 * #79 Quick Settings Tile:
 * Instant system control-center tile to toggle SynFix Mix playback or resume current track.
 */
class SynFixQuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val isPlaying = PlaybackRuntimeCoordinator.isServicePlaying
        val currentTitle = PlaybackRuntimeCoordinator.currentTrackTitle
        tile.state = if (isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = currentTitle.ifBlank { if (isPlaying) "Reproduciendo" else "SynFix Mix" }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, SynFixMusicService::class.java).apply {
            action = SynFixMusicService.ACTION_TILE_PLAY_MIX
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        }
        qsTile?.let { tile ->
            val nextPlaying = !PlaybackRuntimeCoordinator.isServicePlaying
            tile.state = if (nextPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }
}
