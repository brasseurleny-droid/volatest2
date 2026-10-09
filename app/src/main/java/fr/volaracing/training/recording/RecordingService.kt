package fr.volaracing.training.recording

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.core.app.NotificationCompat
import fr.volaracing.training.MainActivity
import fr.volaracing.training.core.Fix
import fr.volaracing.training.core.Fmt
import fr.volaracing.training.core.Gate
import fr.volaracing.training.core.GateDetector
import fr.volaracing.training.core.GpsRow
import fr.volaracing.training.core.LocalFrame
import fr.volaracing.training.core.Pt
import fr.volaracing.training.core.StatsCalc
import fr.volaracing.training.core.Track
import fr.volaracing.training.data.AppDb
import fr.volaracing.training.data.Passage
import fr.volaracing.training.data.Prefs
import fr.volaracing.training.data.Profile
import fr.volaracing.training.data.Session
import fr.volaracing.training.data.SessionLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.TreeMap
import kotlin.math.sqrt

data class RecState(
    val active: Boolean = false,
    val courseId: Long = -1,
    val runStartNs: Long = 0,   // 0 = pas de course en cours
    val lastTimeNs: Long = 0,
    val passages: Int = 0,
    val marks: Int = 0,
    val speedKmh: Double = 0.0,
    val hAcc: Float = -1f,
    val fixes: Int = 0,
    val sats: Int = 0,
)

/**
 * Service de premier plan (type location) : GPS a frequence maximale, IMU et barometre,
 * detection des portes en direct, ecriture CSV. Tout le traitement des capteurs se fait
 * sur un seul HandlerThread dedie (jamais sur le thread principal).
 */
class RecordingService : Service() {

    companion object {
        const val ACTION_START = "fr.volaracing.training.START"
        const val ACTION_STOP = "fr.volaracing.training.STOP"
        const val ACTION_MARK = "fr.volaracing.training.MARK"
        const val EXTRA_COURSE = "course"
        private const val CHANNEL = "recording"
        private const val NOTIF_ID = 42
        val state = MutableStateFlow(RecState())
    }

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var lm: LocationManager? = null
    private var sm: SensorManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Etat accede uniquement depuis le handler thread une fois la session demarree.
    private var logger: SessionLogger? = null
    private var detector: GateDetector? = null
    private var frame: LocalFrame? = null
    private var profile: Profile? = null
    private var sessionId = -1L
    private var courseId = -1L
    private var dir: File? = null
    private var sats = 0
    private var passageCount = 0
    private var markCount = 0
    private val rows = ArrayList<GpsRow>()
    private val gBins = TreeMap<Long, Float>() // max de G par tranche de 100 ms

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (!state.value.active) begin(intent.getLongExtra(EXTRA_COURSE, -1))
            ACTION_MARK -> handler?.post { mark() }
            ACTION_STOP -> stopSelf()
            else -> if (!state.value.active) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun begin(cid: Long) {
        courseId = cid
        createChannel()
        startForeground(NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        state.value = RecState(active = true, courseId = cid)

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vola:rec").apply { acquire(6 * 3600 * 1000L) }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) { tts?.language = Locale.FRANCE; ttsReady = true }
        }
        val t = HandlerThread("vola-rec").also { it.start() }
        thread = t
        val h = Handler(t.looper)
        handler = h

        io.launch {
            val dao = AppDb.get(this@RecordingService).dao()
            val c = dao.course(cid)
            if (c?.aLat == null || c.aLon == null || c.bLat == null || c.bLon == null) {
                fail("Parcours incomplet : placez A et B")
                return@launch
            }
            val prof = Prefs(this@RecordingService).profile(c.activity)
            val sid = dao.insertSession(Session(courseId = cid, startUtcMs = System.currentTimeMillis()))
            val d = File(getExternalFilesDir(null), "sessions/$sid")
            val fr = LocalFrame(c.aLat, c.aLon)
            val b = fr.toLocal(c.bLat, c.bLon)
            val (ga, gb) = try {
                Gate.pair(Pt(0.0, 0.0), b, c.gateWidth)
            } catch (e: IllegalArgumentException) { fail("A et B sont confondus"); return@launch }
            h.post {
                profile = prof; sessionId = sid; dir = d; frame = fr
                logger = SessionLogger(d)
                detector = GateDetector(ga, gb, prof.minSpeedKmh / 3.6, prof.accThreshold.toFloat())
                registerListeners()
            }
        }
    }

    private fun fail(msg: String) {
        handler?.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private fun registerListeners() {
        val h = handler ?: return
        try {
            val l = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm = l
            l.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, locationListener, h.looper)
            @Suppress("DEPRECATION")
            l.registerGnssStatusCallback(gnssCb, h)
        } catch (e: SecurityException) {
            fail("Permission de localisation refusee")
            return
        }
        val s = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm = s
        s.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { s.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_FASTEST, h) }
        s.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { s.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_FASTEST, h) }
        s.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { s.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL, h) }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onLocation(location)
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private val gnssCb = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var n = 0
            for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) n++
            sats = n
            state.update { it.copy(sats = n) }
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val v = e.values
            when (e.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    logger?.imu(e.timestamp, "acc", v[0], v[1], v[2])
                    // Force G calculee sur l'acceleration brute (gravite incluse) : 1 G au repos.
                    val g = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) / 9.80665f
                    val bin = e.timestamp / 100_000_000L
                    val old = gBins[bin]
                    if (old == null || g > old) gBins[bin] = g
                }
                Sensor.TYPE_GYROSCOPE -> logger?.imu(e.timestamp, "gyr", v[0], v[1], v[2])
                Sensor.TYPE_PRESSURE -> logger?.imu(e.timestamp, "baro", v[0], 0f, 0f)
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun onLocation(loc: Location) {
        val t = loc.elapsedRealtimeNanos
        val row = GpsRow(
            t, loc.time, loc.latitude, loc.longitude,
            if (loc.hasAltitude()) loc.altitude else 0.0,
            if (loc.hasSpeed()) loc.speed.toDouble() else 0.0, // TODO: vitesse deduite des positions si absente
            if (loc.hasBearing()) loc.bearing.toDouble() else 0.0,
            if (loc.hasAccuracy()) loc.accuracy else 999f,
            if (loc.hasSpeedAccuracy()) loc.speedAccuracyMetersPerSecond else -1f,
            sats,
        )
        logger?.gps(row)
        rows.add(row)
        val fr = frame ?: return
        val det = detector ?: return
        val p = fr.toLocal(loc.latitude, loc.longitude)
        for (e in det.onFix(Fix(t, p.x, p.y, row.hAcc))) {
            when (e) {
                is GateDetector.Event.Started -> {
                    logger?.mark(e.tNs, "A")
                    state.update { it.copy(runStartNs = e.tNs) }
                    announce("Départ"); buzz(250)
                }
                is GateDetector.Event.Finished -> { logger?.mark(e.endNs, "B"); finish(e) }
            }
        }
        state.update { it.copy(speedKmh = row.speed * 3.6, hAcc = row.hAcc, fixes = it.fixes + 1) }
    }

    private fun finish(e: GateDetector.Event.Finished) {
        val fr = frame ?: return
        val prof = profile ?: return
        val track = Track.build(rows, e.startNs, e.endNs, fr)
        val st = StatsCalc.compute(track, prof.windowS)
        val g = gBins.subMap(e.startNs / 100_000_000L, true, e.endNs / 100_000_000L, true)
            .values.maxOrNull()?.toDouble() ?: 0.0
        val time = e.endNs - e.startNs
        passageCount++
        state.update { it.copy(runStartNs = 0, lastTimeNs = time, passages = passageCount) }
        announce("Arrivée. ${Fmt.spoken(time)}"); buzz(600)
        val p = Passage(
            sessionId = sessionId, courseId = courseId, timeNs = time, lowQuality = e.lowQuality,
            vMax = st.maxMs, gMax = g, startNs = e.startNs, endNs = e.endNs,
            path = dir?.absolutePath ?: "",
        )
        io.launch { AppDb.get(this@RecordingService).dao().insertPassage(p) }
    }

    private fun mark() {
        val t = android.os.SystemClock.elapsedRealtimeNanos()
        logger?.mark(t, "MARK")
        markCount++
        state.update { it.copy(marks = markCount) }
        buzz(80)
    }

    private fun announce(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vola")
    }

    @Suppress("DEPRECATION")
    private fun buzz(ms: Long) {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Enregistrement", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Vola Racing Training")
            .setContentText("Enregistrement en cours")
            .setContentIntent(open)
            .addAction(0, "Arrêter", stop)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        try { lm?.removeUpdates(locationListener) } catch (_: Exception) { }
        try { @Suppress("DEPRECATION") lm?.unregisterGnssStatusCallback(gnssCb) } catch (_: Exception) { }
        sm?.unregisterListener(sensorListener)
        // Ferme les fichiers apres les derniers evenements deja en file d'attente.
        handler?.post { logger?.close(); logger = null }
        thread?.quitSafely()
        tts?.shutdown()
        wakeLock?.let { if (it.isHeld) it.release() }
        state.value = RecState()
        super.onDestroy()
    }
}
