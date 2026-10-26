package br.com.renatopenner.baixatube

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    /* ------------------------- estado da tela ------------------------ */
    var engineReady by mutableStateOf(false)
        private set
    var engineStatus by mutableStateOf("Preparando o motor (yt-dlp + FFmpeg)…")
        private set
    var engineVersion by mutableStateOf("")
        private set
    var updatingEngine by mutableStateOf(false)
        private set

    var url by mutableStateOf("")
    var analyzing by mutableStateOf(false)
        private set
    var meta by mutableStateOf<VideoMeta?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    var selectedPreset by mutableStateOf(PRESETS.first())
    var playlistMode by mutableStateOf(false)
    var cookiesName by mutableStateOf<String?>(null)
        private set
    var cookiesFile by mutableStateOf<File?>(null)
        private set
    var showAllFormats by mutableStateOf(false)

    val jobs = mutableStateListOf<Job>()

    /* -------------------------- aba de baixados ---------------------- */
    var tab by mutableStateOf(0)
    var savedFiles by mutableStateOf<List<SavedFile>>(emptyList())
        private set
    var savedLoading by mutableStateOf(false)
        private set
    var allFilesAccess by mutableStateOf(true)
        private set

    fun refreshSaved() {
        viewModelScope.launch {
            savedLoading = true
            allFilesAccess = hasAllFilesAccess()
            val list = withContext(Dispatchers.IO) { runCatching { Saved.list(getApplication()) }.getOrDefault(emptyList()) }
            savedFiles = list
            savedLoading = false
        }
    }

    fun openSaved(ctx: Context = getApplication(), item: SavedFile) = Saved.open(ctx, item)

    fun shareSaved(ctx: Context = getApplication(), item: SavedFile) = Saved.share(ctx, item)

    fun deleteSaved(item: SavedFile) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Saved.delete(getApplication(), item) }
            message = if (ok) "Apagado: ${item.name}"
            else "Não consegui apagar. Ative \"Acesso a todos os arquivos\" em Ajustes."
            refreshSaved()
        }
    }

    /** API 30+: sem "todos os arquivos" o app não enxerga nem apaga o que não criou */
    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    private val sem = Semaphore(2)

    init {
        viewModelScope.launch { boot() }
    }

    private suspend fun boot() {
        try {
            withContext(Dispatchers.IO) { Engine.init(getApplication()) }
            val v = withContext(Dispatchers.IO) { Engine.version(getApplication()) }
            loadCookies()
            withContext(Dispatchers.Main) {
                engineVersion = v
                engineStatus = "Motor pronto"
                engineReady = true
            }
            maybeUpdateEngine()
            refreshSaved()
        } catch (t: Throwable) {
            withContext(Dispatchers.Main) {
                engineStatus = "Falha ao iniciar o motor"
                message = friendly(t)
            }
        }
    }

    private fun loadCookies() {
        val f = File(getApplication<Application>().filesDir, "cookies.txt")
        if (f.exists() && f.length() > 0) {
            cookiesFile = f
            cookiesName = "cookies.txt importado"
        }
    }

    /* ---------------------------- análise ---------------------------- */

    fun analyze() {
        val u = url.trim()
        if (u.isEmpty()) {
            message = "Cole o link do vídeo primeiro."
            return
        }
        if (!engineReady) {
            message = "O motor ainda está iniciando. Aguarde alguns segundos."
            return
        }
        analyzing = true
        message = null
        meta = null
        playlistMode = false
        viewModelScope.launch {
            try {
                val m = withContext(Dispatchers.IO) { Engine.analyze(u, cookiesFile) }
                meta = m
                selectedPreset = PRESETS.first()
                if (m.playlist) message = "Playlist com ${m.playlistCount} itens detectada."
            } catch (t: Throwable) {
                message = friendly(t)
            } finally {
                analyzing = false
            }
        }
    }

    fun clearMeta() {
        meta = null
        message = null
    }

    /* --------------------------- downloads --------------------------- */

    fun queuePreset() {
        val m = meta ?: run { message = "Analise um link antes de baixar."; return }
        val p = selectedPreset
        val job = Job(
            url = m.url,
            title = m.title,
            label = p.label,
            selector = p.selector,
            merge = p.merge,
            audioOnly = p.audioOnly,
            audioCodec = p.audioCodec,
            audioQuality = p.audioQuality,
            playlist = playlistMode && m.playlist,
        )
        enqueue(job)
    }

    fun queueFormat(fmt: Fmt) {
        val m = meta ?: run { message = "Analise um link antes de baixar."; return }
        val job = Job(
            url = m.url,
            title = m.title,
            label = fmt.label,
            selector = fmt.selector,
            merge = if (fmt.hasVideo) "mp4" else "",
            audioOnly = fmt.audioOnly,
            audioCodec = if (fmt.audioOnly) Engine.audioFormatFor(fmt.ext) else "",
            audioQuality = if (fmt.audioOnly) "0" else "",
            playlist = playlistMode && m.playlist,
        )
        enqueue(job)
    }

    private fun enqueue(job: Job) {
        jobs.add(0, job)
        viewModelScope.launch {
            sem.withPermit {
                if (job.cancelRequested) {
                    job.state = JobState.CANCELED
                    job.status = "Cancelado"
                    return@withPermit
                }
                job.state = JobState.RUNNING
                job.status = "Conectando…"
                try {
                    val outs = withContext(Dispatchers.IO) {
                        Engine.download(job, cookiesFile) { p, eta, line ->
                            if (p >= 0f) job.progress = p
                            job.eta = eta
                            if (line.isNotBlank()) job.status = line
                        }
                    }
                    job.outputs = outs
                    if (outs.isEmpty()) {
                        job.state = JobState.ERROR
                        job.error = "Nenhum arquivo foi gerado. Veja a mensagem do motor."
                        job.status = "Falhou"
                    } else {
                        job.state = JobState.DONE
                        job.progress = 100f
                        job.status = if (outs.size == 1) outs.first().name else "${outs.size} arquivos salvos"
                        refreshSaved()
                    }
                } catch (t: Throwable) {
                    if (job.cancelRequested) {
                        job.state = JobState.CANCELED
                        job.status = "Cancelado"
                    } else {
                        job.state = JobState.ERROR
                        val raw = friendly(t)
                        job.error = if (raw.contains("403")) {
                            "$raw\nDica: atualize o motor em Ajustes (Ajustes → Atualizar motor)."
                        } else raw
                        job.status = "Erro"
                    }
                }
            }
        }
    }

    fun cancel(job: Job) {
        job.cancelRequested = true
        job.status = "Cancelando…"
        viewModelScope.launch(Dispatchers.IO) { Engine.cancel(job.id) }
    }

    fun removeJob(job: Job) {
        if (job.state == JobState.RUNNING) cancel(job)
        jobs.remove(job)
    }

    /* ---------------------------- cookies ---------------------------- */

    fun setCookies(uri: Uri) {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val dest = File(ctx.filesDir, "cookies.txt")
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { out -> input.copyTo(out) }
                    }
                }
                val name = displayName(uri) ?: "cookies.txt"
                cookiesFile = dest
                cookiesName = name
                message = "Cookies importados: $name"
            } catch (t: Throwable) {
                message = "Não consegui ler o arquivo de cookies: ${t.message}"
            }
        }
    }

    fun clearCookies() {
        runCatching { cookiesFile?.delete() }
        cookiesFile = null
        cookiesName = null
        message = "Cookies removidos."
    }

    private fun displayName(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    /* ----------------------------- motor ----------------------------- */

    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences("baixatube", Context.MODE_PRIVATE)
    }

    /**
     * Mantém o yt-dlp em dia sem depender do usuário: com motor velho o YouTube
     * passa a devolver HTTP 403 nos formatos altos (4K/2K). Roda no máximo 1x/dia.
     */
    private suspend fun maybeUpdateEngine() {
        val last = prefs.getLong(KEY_UPDATED_AT, 0L)
        if (System.currentTimeMillis() - last < UPDATE_INTERVAL_MS) return
        withContext(Dispatchers.Main) {
            updatingEngine = true
            engineStatus = "Atualizando o motor (yt-dlp)…"
        }
        try {
            withContext(Dispatchers.IO) { Engine.update(getApplication()) }
            prefs.edit().putLong(KEY_UPDATED_AT, System.currentTimeMillis()).apply()
            val v = withContext(Dispatchers.IO) { Engine.version(getApplication()) }
            withContext(Dispatchers.Main) {
                engineVersion = v
                engineStatus = "Motor atualizado"
            }
        } catch (t: Throwable) {
            withContext(Dispatchers.Main) {
                engineStatus = "Motor local (não consegui atualizar)"
            }
        } finally {
            withContext(Dispatchers.Main) { updatingEngine = false }
        }
    }

    fun updateEngine() {
        if (updatingEngine) return
        updatingEngine = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { Engine.update(getApplication()) }
                prefs.edit().putLong(KEY_UPDATED_AT, System.currentTimeMillis()).apply()
                val v = withContext(Dispatchers.IO) { Engine.version(getApplication()) }
                engineVersion = v
                message = "Motor atualizado (yt-dlp $v)"
            } catch (t: Throwable) {
                message = "Falha ao atualizar: ${friendly(t)}"
            } finally {
                updatingEngine = false
            }
        }
    }

    /* ---------------------------- helpers --------------------------- */

    private fun friendly(t: Throwable): String {
        val raw = (t.message ?: t.toString()).trim()
        val errLine = raw.lines().firstOrNull { it.contains("ERROR:", true) }
        val text = (errLine ?: raw.lines().lastOrNull { it.isNotBlank() } ?: raw).trim()
        return if (text.length > 320) text.take(320) + "…" else text
    }

    companion object {
        private const val KEY_UPDATED_AT = "ytdlp_updated_at"
        private const val UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L
    }
}
