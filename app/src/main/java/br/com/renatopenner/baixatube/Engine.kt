package br.com.renatopenner.baixatube

import android.content.Context
import android.os.Environment
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Motor de download: yt-dlp + ffmpeg compilados para Android (youtubedl-android).
 * Tudo roda no aparelho, sem servidor.
 */
object Engine {

    private const val OUT_TPL = "%(title).150B [%(id)s] %(resolution)s.%(ext)s"
    private const val OUT_TPL_AUDIO = "%(title).150B [%(id)s].%(ext)s"
    private var initialized = false
    private var appCtx: Context? = null

    @Synchronized
    fun init(ctx: Context) {
        if (initialized) return
        appCtx = ctx.applicationContext
        YoutubeDL.getInstance().init(ctx)
        FFmpeg.getInstance().init(ctx)
        initialized = true
    }

    /**
     * O yt-dlp novo exige um runtime JavaScript para o YouTube (sem ele avisa
     * "some formats may be missing"). A lib traz o QuickJS como `libqjs.so`.
     */
    private fun applyJsRuntime(req: YoutubeDLRequest) {
        val ctx = appCtx ?: return
        val qjs = File(ctx.applicationInfo.nativeLibraryDir, "libqjs.so")
        if (qjs.exists()) req.addOption("--js-runtimes", "quickjs:${qjs.absolutePath}")
    }

    fun version(ctx: Context): String =
        runCatching { YoutubeDL.getInstance().version(ctx) ?: "-" }.getOrDefault("-")

    fun downloadDir(): File {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(base, "BaixaTube")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /* ---------------------------- análise ---------------------------- */

    fun analyze(url: String, cookies: File?): VideoMeta {
        val req = YoutubeDLRequest(url)
        req.addOption("--dump-single-json")
        req.addOption("--flat-playlist")
        req.addOption("--no-warnings")
        req.addOption("--ignore-config")
        req.addOption("--no-check-formats")
        applyJsRuntime(req)
        if (cookies != null && cookies.exists()) req.addOption("--cookies", cookies.absolutePath)

        val resp: YoutubeDLResponse = YoutubeDL.getInstance().execute(req, null, null)
        val raw = resp.out.lines().firstOrNull { it.trimStart().startsWith("{") }
            ?: throw IllegalStateException(
                resp.err.lines().lastOrNull { it.isNotBlank() }?.trim() ?: "sem resposta do yt-dlp"
            )
        val json = JSONObject(raw)

        val entries = json.optJSONArray("entries")
        val type = json.optString("_type")
        val isPlaylist = entries != null && (type == "playlist" || type == "multi_video" || entries.length() > 1)

        val firstEntry = if (isPlaylist && entries != null && entries.length() > 0) entries.optJSONObject(0) else null

        val title = json.optString("title").ifBlank {
            firstEntry?.optString("title").orEmpty().ifBlank { "(sem título)" }
        }
        val uploader = json.optString("uploader").ifBlank {
            json.optString("channel").ifBlank { firstEntry?.optString("uploader").orEmpty() }
        }
        val duration = json.optDouble("duration", 0.0).toLong()
        val thumb = json.optString("thumbnail").ifBlank {
            firstEntry?.optString("thumbnail").orEmpty().ifBlank { null }
        }

        val formats = ArrayList<Fmt>()
        val arr = json.optJSONArray("formats") ?: firstEntry?.optJSONArray("formats")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                val ext = f.optString("ext", "")
                val vcodec = f.optString("vcodec", "")
                val acodec = f.optString("acodec", "")
                val note = f.optString("format_note", "")
                val hasV = vcodec.isNotBlank() && vcodec != "none"
                val hasA = acodec.isNotBlank() && acodec != "none"
                if (ext == "mhtml" || note.contains("storyboard", true)) continue
                if (!hasV && !hasA) continue
                val size = f.optLong("filesize", 0L).let { if (it > 0) it else f.optLong("filesize_approx", 0L) }
                formats.add(
                    Fmt(
                        id = f.optString("format_id", ""),
                        ext = ext,
                        height = if (f.has("height") && !f.isNull("height")) f.optInt("height") else 0,
                        fps = if (f.has("fps") && !f.isNull("fps")) f.optDouble("fps", 0.0) else 0.0,
                        vcodec = vcodec,
                        acodec = acodec,
                        tbr = if (f.has("tbr") && !f.isNull("tbr")) f.optDouble("tbr", 0.0) else 0.0,
                        size = size,
                        note = note,
                    )
                )
            }
        }

        return VideoMeta(
            url = url,
            title = title,
            uploader = uploader,
            durationSec = duration,
            thumb = thumb,
            formats = formats,
            playlist = isPlaylist,
            playlistCount = entries?.length() ?: 1,
        )
    }

    /* --------------------------- download ---------------------------- */

    fun audioFormatFor(ext: String): String = when (ext.lowercase(Locale.US)) {
        "m4a", "mp4", "aac" -> "m4a"
        "webm", "opus" -> "opus"
        "mp3" -> "mp3"
        "flac" -> "flac"
        else -> "best"
    }

    fun download(job: Job, cookies: File?, onProgress: (Float, Long, String) -> Unit): List<File> {
        val dir = downloadDir()
        val tmp = File(dir, ".tmp/${job.id}")
        tmp.mkdirs()
        val started = System.currentTimeMillis()
        removeStaleCovers(dir)

        val req = YoutubeDLRequest(job.url)
        val tpl = if (job.audioOnly) OUT_TPL_AUDIO else OUT_TPL
        req.addOption("-o", File(dir, tpl).absolutePath)
        req.addOption("--no-mtime")
        req.addOption("--ignore-config")
        req.addOption("--newline")
        req.addOption("--no-warnings")
        req.addOption("-f", job.selector)
        req.addOption("--concurrent-fragments", 4)
        req.addOption("--embed-metadata")
        req.addOption("--convert-thumbnails", "jpg")
        applyJsRuntime(req)
        req.addOption("-P", "temp:${tmp.absolutePath}")
        if (cookies != null && cookies.exists()) req.addOption("--cookies", cookies.absolutePath)
        if (!job.playlist) req.addOption("--no-playlist")

        if (job.audioOnly) {
            req.addOption("-x")
            req.addOption("--audio-format", job.audioCodec.ifBlank { "m4a" })
            req.addOption("--audio-quality", job.audioQuality.ifBlank { "0" })
        } else {
            if (job.merge.isNotBlank()) req.addOption("--merge-output-format", job.merge)
            req.addOption("--embed-thumbnail")
        }

        try {
            YoutubeDL.getInstance().execute(req, job.id) { progress, eta, line ->
                onProgress(progress, eta, shortLine(line))
            }
        } catch (t: Throwable) {
            // job cancelado/falho: não deixa .part e .temp ocupando espaço
            runCatching { sweepLeftovers(dir, started) }
            runCatching { tmp.deleteRecursively() }
            throw t
        }

        runCatching { tmp.deleteRecursively() }
        runCatching {
            val parent = tmp.parentFile
            if (parent != null && parent.isDirectory && parent.list()?.isEmpty() == true) parent.delete()
        }
        val outputs = collectOutputs(dir, started)
        cleanupCovers(dir, outputs)
        indexMedia(outputs)
        return outputs
    }

    fun cancel(jobId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(jobId) }
    }

    fun update(ctx: Context) {
        YoutubeDL.getInstance().updateYoutubeDL(ctx)
    }

    private fun shortLine(line: String): String {
        val t = line.trim()
        return when {
            t.startsWith("[download]") -> t.removePrefix("[download]").trim()
            t.startsWith("[Merger]") -> "Mesclando áudio e vídeo…"
            t.startsWith("[ExtractAudio]") -> "Convertendo áudio…"
            t.startsWith("[Metadata]") -> "Gravando metadados…"
            t.startsWith("[ThumbnailsConvertor]") || t.startsWith("[EmbedThumbnail]") -> "Embutindo capa…"
            t.startsWith("[youtube]") || t.startsWith("[instagram]") ||
                t.startsWith("[facebook]") || t.startsWith("[tiktok]") -> "Lendo informações…"
            t.length > 90 -> t.take(90) + "…"
            else -> t
        }
    }

    /** registra os arquivos no MediaStore (aparecem na galeria e na aba Baixados) */
    private fun indexMedia(files: List<File>) {
        val ctx = appCtx ?: return
        if (files.isEmpty()) return
        runCatching {
            android.media.MediaScannerConnection.scanFile(
                ctx, files.map { it.absolutePath }.toTypedArray(), null, null,
            )
        }
    }

    /** restos de downloads interrompidos (.part, .ytdl, .temp.mp4) */
    private fun sweepLeftovers(dir: File, since: Long) {
        dir.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            if (f.lastModified() < since - 3000L) return@forEach
            val ext = f.extension.lowercase(Locale.US)
            if (ext in setOf("part", "ytdl") || f.name.contains(".temp.")) {
                runCatching { f.delete() }
            }
        }
    }

    /** capa solta de tentativa anterior faz a conversão de thumbnail falhar */
    private fun removeStaleCovers(dir: File) {
        val images = setOf("jpg", "jpeg", "webp", "png")
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.extension.lowercase(Locale.US) in images) runCatching { f.delete() }
        }
    }

    /** o embed de capa deixa um .webp/.jpg ao lado do vídeo; apaga o resto */
    private fun cleanupCovers(dir: File, outputs: List<File>) {
        if (outputs.isEmpty()) return
        val stems = outputs.map { it.nameWithoutExtension }.toSet()
        val images = setOf("jpg", "jpeg", "webp", "png")
        dir.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            if (f.extension.lowercase(Locale.US) !in images) return@forEach
            if (f.nameWithoutExtension in stems) runCatching { f.delete() }
        }
    }

    /** arquivos realmente produzidos pelo job (ignora temporários e capas) */
    private fun collectOutputs(dir: File, since: Long): List<File> {
        val ignore = setOf("part", "ytdl", "tmp", "jpg", "jpeg", "webp", "png")
        val out = ArrayList<File>()
        dir.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            if (f.lastModified() < since - 3000L) return@forEach
            if (f.name.startsWith(".")) return@forEach
            val ext = f.extension.lowercase(Locale.US)
            if (ext in ignore) return@forEach
            if (f.length() <= 0L) return@forEach
            out.add(f)
        }
        return out.sortedByDescending { it.lastModified() }
    }
}
