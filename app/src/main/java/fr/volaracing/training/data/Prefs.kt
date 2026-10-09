package fr.volaracing.training.data

import android.content.Context

object Act {
    const val VTT = "VTT"
    const val SKI = "SKI"
    val all = listOf(VTT, SKI)
    fun label(a: String) = if (a == SKI) "Ski" else "VTT"
}

/** Profil de reglages d'une activite. */
data class Profile(
    val gateWidth: Double,
    val minSpeedKmh: Double,
    val windowS: Double,
    val accThreshold: Double,
)

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("vola", Context.MODE_PRIVATE)

    var warningAccepted: Boolean
        get() = sp.getBoolean("warning", false)
        set(v) = sp.edit().putBoolean("warning", v).apply()

    var lastActivity: String
        get() = sp.getString("activity", Act.VTT) ?: Act.VTT
        set(v) = sp.edit().putString("activity", v).apply()

    // Valeurs par defaut : hypotheses a valider (voir README).
    fun profile(act: String): Profile {
        val ski = act == Act.SKI
        return Profile(
            gateWidth = sp.getFloat("${act}_gate", if (ski) 30f else 20f).toDouble(),
            minSpeedKmh = sp.getFloat("${act}_minspeed", if (ski) 15f else 8f).toDouble(),
            windowS = sp.getFloat("${act}_window", 1f).toDouble(),
            accThreshold = sp.getFloat("${act}_acc", 25f).toDouble(),
        )
    }

    fun saveProfile(act: String, p: Profile) {
        sp.edit()
            .putFloat("${act}_gate", p.gateWidth.toFloat())
            .putFloat("${act}_minspeed", p.minSpeedKmh.toFloat())
            .putFloat("${act}_window", p.windowS.toFloat())
            .putFloat("${act}_acc", p.accThreshold.toFloat())
            .apply()
    }
}
