package com.example.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.MainActivity
import com.example.R
import com.example.player.SynFixMusicService

object WidgetPrefKeys {
    val TITLE = stringPreferencesKey("widget_title")
    val ARTIST = stringPreferencesKey("widget_artist")
    val IS_PLAYING = booleanPreferencesKey("widget_is_playing")
    val PROGRESS = floatPreferencesKey("widget_progress")
}

/**
 * #78 Glance M3 Expressive AppWidget:
 * Native home-screen widget styled with GlanceTheme dynamic M3 colors,
 * live progress indicator, and Play/Pause + Next actions.
 */
class SynFixGlanceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val title = prefs[WidgetPrefKeys.TITLE] ?: "SynFix"
            val artist = prefs[WidgetPrefKeys.ARTIST] ?: "Toca para explorar tu música"
            val isPlaying = prefs[WidgetPrefKeys.IS_PLAYING] ?: false
            val progress = prefs[WidgetPrefKeys.PROGRESS] ?: 0f

            GlanceTheme {
                WidgetContent(
                    title = title,
                    artist = artist,
                    isPlaying = isPlaying,
                    progress = progress
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        title: String,
        artist: String,
        isPlaying: Boolean,
        progress: Float
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(24.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.synfix_logo_foreground),
                    contentDescription = "SynFix",
                    modifier = GlanceModifier
                        .size(44.dp)
                        .background(GlanceTheme.colors.primaryContainer)
                        .cornerRadius(14.dp)
                        .padding(4.dp)
                )

                Spacer(modifier = GlanceModifier.width(12.dp))

                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = title,
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = artist,
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )
                }

                Box(
                    modifier = GlanceModifier
                        .size(48.dp)
                        .background(GlanceTheme.colors.primary)
                        .cornerRadius(24.dp)
                        .clickable(actionRunCallback<TogglePlayAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPlaying) "❚❚" else "▶",
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.width(8.dp))

                Box(
                    modifier = GlanceModifier
                        .size(40.dp)
                        .background(GlanceTheme.colors.secondaryContainer)
                        .cornerRadius(20.dp)
                        .clickable(actionRunCallback<SkipNextAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⏭",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSecondaryContainer,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surface
            )
        }
    }

    companion object {
        suspend fun pushUpdate(
            context: Context,
            title: String,
            artist: String,
            isPlaying: Boolean,
            progress: Float
        ) {
            runCatching {
                val manager = GlanceAppWidgetManager(context)
                val ids = manager.getGlanceIds(SynFixGlanceWidget::class.java)
                ids.forEach { glanceId ->
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[WidgetPrefKeys.TITLE] = title
                            this[WidgetPrefKeys.ARTIST] = artist
                            this[WidgetPrefKeys.IS_PLAYING] = isPlaying
                            this[WidgetPrefKeys.PROGRESS] = progress.coerceIn(0f, 1f)
                        }
                    }
                    SynFixGlanceWidget().update(context, glanceId)
                }
            }
        }
    }
}

class TogglePlayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, SynFixMusicService::class.java).apply {
            action = SynFixMusicService.ACTION_WIDGET_TOGGLE_PLAY
        }
        startServiceCompat(context, intent)
    }
}

class SkipNextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, SynFixMusicService::class.java).apply {
            action = SynFixMusicService.ACTION_WIDGET_NEXT
        }
        startServiceCompat(context, intent)
    }
}

private fun startServiceCompat(context: Context, intent: Intent) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }
}

class SynFixGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SynFixGlanceWidget()
}
