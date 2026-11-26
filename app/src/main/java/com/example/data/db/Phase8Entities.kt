package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.BluetoothMacProfile
import com.example.domain.model.TrophyItem

/**
 * #68 Offline Trophy Cabinet entity.
 */
@Entity(tableName = "trophies")
data class TrophyEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val unlocked: Boolean = false,
    val unlockedAt: Long = 0L,
    val progress: Int = 0,
    val target: Int = 1
) {
    fun toDomain(): TrophyItem = TrophyItem(
        id = id,
        title = title,
        description = description,
        unlocked = unlocked,
        unlockedAt = unlockedAt,
        progress = progress,
        target = target
    )
}

/**
 * #82 Per-MAC Bluetooth Auto-Profile entity.
 */
@Entity(tableName = "bluetooth_mac_profiles")
data class BluetoothMacProfileEntity(
    @PrimaryKey val macAddress: String,
    val deviceName: String,
    val eqPresetId: String = "bass_boost",
    val initialVolumePercent: Int = 65
) {
    fun toDomain(): BluetoothMacProfile = BluetoothMacProfile(
        macAddress = macAddress,
        deviceName = deviceName,
        eqPresetId = eqPresetId,
        initialVolumePercent = initialVolumePercent
    )
}

/**
 * #66 Artist Link Chain relational index in Room (collaborators parsed from feat./ft./&).
 */
@Entity(
    tableName = "track_collaborations",
    primaryKeys = ["songId", "artistNormalized"],
    indices = [Index(value = ["artistNormalized"]), Index(value = ["songId"])]
)
data class TrackCollaborationEntity(
    val songId: Long,
    val artistNormalized: String,
    val artistDisplay: String
)
