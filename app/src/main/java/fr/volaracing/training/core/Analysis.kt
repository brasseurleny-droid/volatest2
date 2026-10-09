package fr.volaracing.training.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

data class GpsRow(
    val tNs: Long, val utcMs: Long, val lat: Double, val lon: Double, val alt: Double,
    val speed: Double, val bearing: Double, val hAcc: Float, val sAcc: Float, val sats: Int,
)

data class TrackPt(
    val tNs: Long, val lat: Double, val lon: Double,
    val x: Double, val y: Double, val speed: Double, val dist: Double,
)

object Track {
    /** Ligne GPS interpolee a l'instant t (null si t sort de la plage). */
    fun interpolate(rows: List<GpsRow>, t: Long): GpsRow? {
        if (rows.size < 2 || t < rows.first().tNs || t > rows.last().tNs) return null
        var lo = 0
        var hi = rows.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (rows[mid].tNs <= t) lo = mid else hi = mid
        }
        val a = rows[lo]
        val b = rows[hi]
        val span = b.tNs - a.tNs
        val f = if (span <= 0L) 0.0 else (t - a.tNs).toDouble() / span
        fun m(x: Double, y: Double) = x + (y - x) * f
        return a.copy(
            tNs = t,
            utcMs = (a.utcMs + (b.utcMs - a.utcMs) * f).toLong(),
            lat = m(a.lat, b.lat), lon = m(a.lon, b.lon), alt = m(a.alt, b.alt),
            speed = m(a.speed, b.speed),
        )
    }

    /** Trace entre startNs et endNs, bornes interpolees, distance cumulee en metres. */
    fun build(rows: List<GpsRow>, startNs: Long, endNs: Long, frame: LocalFrame): List<TrackPt> {
        val pts = ArrayList<GpsRow>()
        interpolate(rows, startNs)?.let { pts.add(it) }
        rows.filterTo(pts) { it.tNs > startNs && it.tNs < endNs }
        interpolate(rows, endNs)?.let { pts.add(it) }
        var d = 0.0
        var prev: Pt? = null
        return pts.map { r ->
            val p = frame.toLocal(r.lat, r.lon)
            prev?.let { d += hypot(p.x - it.x, p.y - it.y) }
            prev = p
            TrackPt(r.tNs, r.lat, r.lon, p.x, p.y, r.speed, d)
        }
    }
}

data class Stats(
    val durationS: Double, val distM: Double, val avgMs: Double,
    val maxMs: Double, val timeAtMaxS: Double,
)

object StatsCalc {
    /** Vitesse lissee par moyenne glissante sur +/- windowS/2. */
    fun smoothed(track: List<TrackPt>, windowS: Double): DoubleArray {
        val half = (windowS / 2.0 * 1e9).toLong()
        return DoubleArray(track.size) { i ->
            var sum = 0.0
            var n = 0
            for (j in track.indices) {
                if (abs(track[j].tNs - track[i].tNs) <= half) { sum += track[j].speed; n++ }
            }
            if (n == 0) track[i].speed else sum / n
        }
    }

    fun compute(track: List<TrackPt>, windowS: Double): Stats {
        if (track.size < 2) return Stats(0.0, 0.0, 0.0, 0.0, 0.0)
        val dur = (track.last().tNs - track.first().tNs) / 1e9
        val dist = track.last().dist
        val sm = smoothed(track, windowS)
        val vmax = sm.max()
        var atMax = 0.0
        for (i in 1 until track.size) {
            if (sm[i] >= 0.95 * vmax) atMax += (track[i].tNs - track[i - 1].tNs) / 1e9
        }
        return Stats(dur, dist, if (dur > 0) dist / dur else 0.0, vmax, atMax)
    }
}

object Compare {
    /** Secondes ecoulees depuis le debut de la trace quand la distance cumulee atteint d. */
    fun timeAtDistance(track: List<TrackPt>, d: Double): Double? {
        if (track.size < 2 || d < 0.0 || d > track.last().dist) return null
        val t0 = track.first().tNs
        for (i in 1 until track.size) {
            val a = track[i - 1]
            val b = track[i]
            if (b.dist >= d) {
                val span = b.dist - a.dist
                val f = if (span <= 1e-9) 1.0 else (d - a.dist) / span
                return ((a.tNs - t0) + (b.tNs - a.tNs) * f) / 1e9
            }
        }
        return null
    }

    /**
     * Ecart cumule (secondes) de B par rapport a A en fonction de la distance :
     * positif = B a perdu du temps. Pas en metres. Les distances sont mesurees sur
     * chaque trace, l'alignement est donc approximatif si les trajectoires different.
     */
    fun deltaCurve(a: List<TrackPt>, b: List<TrackPt>, step: Double = 10.0): List<Pair<Double, Double>> {
        if (a.size < 2 || b.size < 2) return emptyList()
        val max = minOf(a.last().dist, b.last().dist)
        val out = ArrayList<Pair<Double, Double>>()
        var d = 0.0
        while (d <= max) {
            val ta = timeAtDistance(a, d)
            val tb = timeAtDistance(b, d)
            if (ta != null && tb != null) out.add(Pair(d, tb - ta))
            d += step
        }
        if (out.isNotEmpty() && out.last().first < max) {
            val ta = timeAtDistance(a, max)
            val tb = timeAtDistance(b, max)
            if (ta != null && tb != null) out.add(Pair(max, tb - ta))
        }
        return out
    }
}

object Fmt {
    fun time(ns: Long): String {
        val cs = ns / 10_000_000L
        return String.format(Locale.US, "%d:%02d.%02d", cs / 6000, (cs / 100) % 60, cs % 100)
    }

    fun spoken(ns: Long): String {
        val cs = ns / 10_000_000L
        val m = cs / 6000
        val s = (cs / 100) % 60
        val d = (cs % 100) / 10
        val minutes = if (m > 0) "$m minute${if (m > 1) "s" else ""} " else ""
        return "$minutes$s virgule $d secondes"
    }

    fun kmh(ms: Double) = ms * 3.6
}
