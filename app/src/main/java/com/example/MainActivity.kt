package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.MainScreen
import com.example.ui.theme.SynFixTheme
import com.example.ui.viewmodel.MusicViewModel

class MainActivity : FragmentActivity() {
    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearance by viewModel.appearanceSettings.collectAsStateWithLifecycle()
            val albumSeedColor by viewModel.albumSeedColor.collectAsStateWithLifecycle()

            SynFixTheme(
                colorMode = appearance.colorMode,
                brightnessMode = appearance.brightnessMode,
                seedColor = androidx.compose.ui.graphics.Color(appearance.seedColorArgb),
                albumSeedColor = albumSeedColor
            ) {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setLifecycleResumed(true)
    }

    override fun onPause() {
        viewModel.setLifecycleResumed(false)
        super.onPause()
    }
}
