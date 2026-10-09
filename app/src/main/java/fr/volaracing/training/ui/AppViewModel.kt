package fr.volaracing.training.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.volaracing.training.core.LocalFrame
import fr.volaracing.training.core.Stats
import fr.volaracing.training.core.StatsCalc
import fr.volaracing.training.core.Track
import fr.volaracing.training.core.TrackPt
import fr.volaracing.training.data.AppDb
import fr.volaracing.training.data.Course
import fr.volaracing.training.data.Csv
import fr.volaracing.training.data.Exporter
import fr.volaracing.training.data.Passage
import fr.volaracing.training.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File

data class Loaded(val passage: Passage, val track: List<TrackPt>, val stats: Stats)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val dao = AppDb.get(app).dao()

    var activity by mutableStateOf(prefs.lastActivity)
        private set

    val courses: StateFlow<List<Course>> =
        dao.courses().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectActivity(a: String) { activity = a; prefs.lastActivity = a }

    fun passages(courseId: Long) = dao.passages(courseId)

    suspend fun course(id: Long): Course? = dao.course(id)

    suspend fun saveCourse(c: Course): Long =
        if (c.id == 0L) dao.insertCourse(c) else { dao.updateCourse(c); c.id }

    suspend fun deleteCourse(id: Long) = dao.deleteCourse(id)

    private suspend fun load(p: Passage): Loaded? = withContext(Dispatchers.IO) {
        val c = dao.course(p.courseId) ?: return@withContext null
        val a = c.aLat ?: return@withContext null
        val o = c.aLon ?: return@withContext null
        val rows = Csv.readGps(File(p.path, "gps.csv"))
        val track = Track.build(rows, p.startNs, p.endNs, LocalFrame(a, o))
        Loaded(p, track, StatsCalc.compute(track, prefs.profile(c.activity).windowS))
    }

    suspend fun loadPair(a: Long, b: Long): Pair<Loaded, Loaded>? {
        val pa = dao.passage(a) ?: return null
        val pb = dao.passage(b) ?: return null
        val la = load(pa) ?: return null
        val lb = load(pb) ?: return null
        return Pair(la, lb)
    }

    suspend fun exportSession(ctx: Context, p: Passage): Int = withContext(Dispatchers.IO) {
        Exporter.exportToDownloads(ctx, p.sessionId, File(p.path))
    }

    suspend fun shareIntent(ctx: Context, p: Passage) = withContext(Dispatchers.IO) {
        Exporter.shareIntent(ctx, p.sessionId, File(p.path))
    }
}
