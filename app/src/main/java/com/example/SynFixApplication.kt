package com.example

import android.app.Application
import androidx.work.Configuration
import com.example.data.db.MusicDatabase
import com.example.data.prefs.SynFixPreferences
import com.example.data.repository.MusicRepository
import com.example.data.tags.AudioTagEditor
import com.example.data.worker.CamelotKeyWorker
import com.example.data.worker.ReplayGainWorker
import com.example.player.AudioEffectsEngine
import com.example.player.AudioVisualizerController
import com.example.player.LocalPartySyncEngine
import com.example.player.SubBassHapticEngine

class SynFixApplication : Application(), Configuration.Provider {
    val database: MusicDatabase by lazy { MusicDatabase.getDatabase(this) }
    val preferences: SynFixPreferences by lazy { SynFixPreferences(this) }
    val repository: MusicRepository by lazy { MusicRepository(this, database.musicDao()) }
    val tagEditor: AudioTagEditor by lazy { AudioTagEditor(this) }
    val audioEffectsEngine: AudioEffectsEngine by lazy { AudioEffectsEngine(this) }
    val visualizerController: AudioVisualizerController by lazy { AudioVisualizerController(this) }
    val subBassHapticEngine: SubBassHapticEngine by lazy { SubBassHapticEngine(this) }
    val partySyncEngine: LocalPartySyncEngine by lazy { LocalPartySyncEngine(this) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        runCatching { ReplayGainWorker.scheduleChargingScan(this) }
        runCatching { CamelotKeyWorker.scheduleChargingScan(this) }
        runCatching { subBassHapticEngine.startObserving(visualizerController.subBassEnergy) }
    }

    override fun onTerminate() {
        partySyncEngine.destroy()
        subBassHapticEngine.destroy()
        visualizerController.destroy()
        audioEffectsEngine.destroy()
        super.onTerminate()
    }
}
