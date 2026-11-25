package com.example.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

enum class ColorThemeMode(val label: String) {
    SYSTEM_DYNAMIC("Dinámico del sistema"),
    ALBUM_DYNAMIC("Dinámico de carátula"),
    CUSTOM_SEED("Paleta personalizada")
}

enum class BrightnessMode(val label: String) {
    SYSTEM("Sistema"),
    LIGHT("Claro"),
    DARK("Oscuro"),
    AMOLED("Negro Puro AMOLED")
}

enum class NowPlayingStyle(val label: String, val description: String) {
    EXPRESSIVE_CLASSIC("Expressive Clásico", "Carátula orgánica morphing y Seekbar ondulada"),
    IMMERSIVE_FULL_BLEED("Inmersivo Full-Bleed", "Portada de borde a borde con degradado dinámico"),
    VINYL_MINIMAL("Vinilo & Letras", "Disco giratorio con scratch háptico y letras en foco")
}

enum class LibraryViewLayout(val label: String) {
    LIST_COMPACT("Lista compacta"),
    LIST_COMFORTABLE("Lista cómoda"),
    GRID_2("Cuadrícula 2 col"),
    GRID_3("Cuadrícula 3 col")
}

enum class ArtworkShapeStyle(val label: String) {
    SOFT_SQUARE("Cuadrado suave"),
    EXPRESSIVE_ROUNDED("Redondeado Expressive"),
    ORGANIC("Forma orgánica")
}

enum class LibraryTab(val label: String) {
    SONGS("Canciones"),
    ALBUMS("Álbumes"),
    ARTISTS("Artistas"),
    PLAYLISTS("Playlists")
}

enum class RepeatModeState {
    OFF, ALL, ONE
}

@Immutable
data class AppearanceSettings(
    val colorMode: ColorThemeMode = ColorThemeMode.SYSTEM_DYNAMIC,
    val brightnessMode: BrightnessMode = BrightnessMode.SYSTEM,
    val seedColorArgb: Long = 0xFF7C4DFF,
    val nowPlayingStyle: NowPlayingStyle = NowPlayingStyle.EXPRESSIVE_CLASSIC,
    val libraryLayout: LibraryViewLayout = LibraryViewLayout.LIST_COMFORTABLE,
    val artworkShape: ArtworkShapeStyle = ArtworkShapeStyle.EXPRESSIVE_ROUNDED,
    val beatSyncedMorphEnabled: Boolean = true,
    val proceduralArtFallback: Boolean = true,
    val floatingBubbleEnabled: Boolean = false,
    val subBassHapticsEnabled: Boolean = false
)

@Immutable
data class PlaybackSettings(
    val gaplessEnabled: Boolean = true,
    val crossfadeSeconds: Int = 0,
    val beatMatchedCrossfade: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val channelBalance: Float = 0.0f, // -1.0f (L) .. +1.0f (R)
    val deadAirTrimmerEnabled: Boolean = false,
    val nightModeDrcEnabled: Boolean = false,
    val replayGainEnabled: Boolean = false,
    val midSideStemBalance: Float = 0.0f, // -1.0f (Karaoke/Inst) .. 0f (Normal) .. +1.0f (Vocal Boost)
    val venueReverbPreset: VenueReverbPreset = VenueReverbPreset.OFF,
    val focusFilterBand: FocusFilterBand = FocusFilterBand.NONE,
    val lofiTapeEnabled: Boolean = false,
    val slowedReverbEnabled: Boolean = false,
    val adaptiveAmbientEnabled: Boolean = false
)

@Immutable
data class ScannerSettings(
    val minDurationSeconds: Int = 15,
    val excludedFolders: ImmutableSet<String> = persistentSetOf(),
    val knownFolders: ImmutableList<FolderInfo> = persistentListOf()
)

@Immutable
data class FolderInfo(
    val path: String,
    val displayName: String,
    val trackCount: Int,
    val isExcluded: Boolean
)

@Immutable
data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingMs: Long = 0L,
    val stopAfterCurrentTrack: Boolean = false,
    val isFadingOut: Boolean = false
)
