package com.example.data.audio

import android.content.Context
import com.example.domain.model.AutoEqProfile
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * #54 AutoEQ Headphone Calibration:
 * Lazily parses bundled assets/autoeq_profiles.json on Dispatchers.IO and caches in memory.
 */
class AutoEqRepository(private val context: Context) {

    @Volatile
    private var cachedProfiles: ImmutableList<AutoEqProfile>? = null

    suspend fun getProfiles(): ImmutableList<AutoEqProfile> {
        cachedProfiles?.let { return it }
        return withContext(Dispatchers.IO) {
            cachedProfiles?.let { return@withContext it }
            val loaded = runCatching {
                val raw = context.assets.open("autoeq_profiles.json").bufferedReader().use { it.readText() }
                parseJson(raw)
            }.getOrElse { persistentListOf() }
            cachedProfiles = loaded
            loaded
        }
    }

    fun parseJson(raw: String): ImmutableList<AutoEqProfile> {
        val arr = JSONArray(raw)
        val out = ArrayList<AutoEqProfile>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val bandsJson = obj.optJSONArray("bands") ?: continue
            val bands = ArrayList<Float>(10)
            for (b in 0 until 10) {
                bands.add(bandsJson.optDouble(b, 0.0).toFloat().coerceIn(-12f, 12f))
            }
            out.add(
                AutoEqProfile(
                    id = obj.optString("id"),
                    model = obj.optString("model"),
                    category = obj.optString("category"),
                    preampDb = obj.optDouble("preamp", 0.0).toFloat().coerceIn(-12f, 12f),
                    bandsDb = bands.toImmutableList()
                )
            )
        }
        return out.toImmutableList()
    }
}
