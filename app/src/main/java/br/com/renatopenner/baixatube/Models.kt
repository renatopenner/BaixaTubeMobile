package br.com.renatopenner.baixatube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/* ------------------------------------------------------------------ */
/* utilidades                                                          */
/* ------------------------------------------------------------------ */

fun mb(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes >= 1_000_000_000L -> String.format(Locale.US, "%.2f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    else -> String.format(Locale.US, "%d KB", bytes / 1000L)
}

fun durationText(sec: Long): String {
    if (sec <= 0L) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun codecShort(codec: String): String = when {
    codec.isEmpty() || codec == "none" -> "—"
    codec.startsWith("avc1") || codec.startsWith("avc3") -> "H.264"
    codec.startsWith("hev1") || codec.startsWith("hvc1") -> "H.265"
    codec.startsWith("vp9") || codec.startsWith("vp09") -> "VP9"
    codec.startsWith("av01") -> "AV1"
    codec.startsWith("mp4a") -> "AAC"
    codec.startsWith("opus") -> "Opus"
    codec.startsWith("vorbis") -> "Vorbis"
    codec.startsWith("ec-3") -> "E-AC3"
    codec.startsWith("ac-3") -> "AC3"
    codec.startsWith("mp3") -> "MP3"
    codec.startsWith("flac") -> "FLAC"
    else -> codec.substringBefore('.').uppercase(Locale.US)
}

/* ------------------------------------------------------------------ */
/* formatos                                                            */
/* ------------------------------------------------------------------ */

data class Fmt(
    val id: String,
    val ext: String,
    val height: Int,
    val fps: Double,
    val vcodec: String,
    val acodec: String,
    val tbr: Double,
    val size: Long,
    val note: String,
) {
    val hasVideo: Boolean get() = vcodec.isNotEmpty() && vcodec != "none"
    val hasAudio: Boolean get() = acodec.isNotEmpty() && acodec != "none"
    val audioOnly: Boolean get() = !hasVideo && hasAudio

    val kind: String
        get() = when {
            hasVideo && hasAudio -> "vídeo + áudio"
            hasVideo -> "vídeo (sem áudio)"
            hasAudio -> "só áudio"
            else -> "?"
        }

    val quality: String
        get() = when {
            height > 0 && fps > 30 -> "${height}p${fps.roundToInt()}"
            height > 0 -> "${height}p"
            tbr > 0 -> "${tbr.roundToInt()}k"
            else -> "—"
        }

    val tracks: String get() = "${codecShort(vcodec)} / ${codecShort(acodec)}"

    val label: String
        get() {
            val sizeTxt = if (size > 0) " · ${mb(size)}" else ""
            return "$quality · ${ext.uppercase(Locale.US)} · $tracks$sizeTxt"
        }

    /** seletor yt-dlp para este formato exato (junta áudio m4a quando é só vídeo) */
    val selector: String get() = if (hasVideo && !hasAudio) "$id+ba[ext=m4a]/$id+ba/$id" else id
}

/* ------------------------------------------------------------------ */
/* metadados do vídeo                                                  */
/* ------------------------------------------------------------------ */

data class VideoMeta(
    val url: String,
    val title: String,
    val uploader: String,
    val durationSec: Long,
    val thumb: String?,
    val formats: List<Fmt>,
    val playlist: Boolean,
    val playlistCount: Int,
)

/* ------------------------------------------------------------------ */
/* presets (mesmos do BaixaTube desktop)                               */
/* ------------------------------------------------------------------ */

data class Preset(
    val pid: String,
    val label: String,
    val hint: String,
    val selector: String,
    val merge: String = "mp4",
    val audioCodec: String = "",
    val audioQuality: String = "",
    val audioOnly: Boolean = false,
)

/**
 * Melhor resolução até `h`, **sem exigir codec**: o YouTube não oferece H.264
 * acima de 1080p, então exigir avc1 primeiro fazia o preset "4K" baixar 1080p.
 * Ordem: MP4 (AV1/VP9 em mp4_dash) → H.264 MP4 → qualquer codec → arquivo único.
 */
private fun upto(h: Int): String =
    "bv*[height<=$h][ext=mp4]+ba[ext=m4a]/" +
        "bv*[height<=$h][vcodec^=avc1]+ba[ext=m4a]/" +
        "bv*[height<=$h]+ba/" +
        "b[height<=$h]"

val PRESETS: List<Preset> = listOf(
    Preset(
        "best_mp4", "Melhor MP4", "Até 4K H.264/AAC — roda em qualquer celular e TV",
        "bv*[vcodec^=avc1][ext=mp4]+ba[ext=m4a]/bv*[vcodec^=avc1]+ba/b[ext=mp4]/bv*+ba/b",
        merge = "mp4",
    ),
    Preset(
        "best_any", "Original", "Sem reencode (VP9/AV1) em MKV — máxima fidelidade",
        "bv*+ba/b", merge = "mkv",
    ),
    Preset("2160p", "4K", "Melhor resolução até 2160p (AV1/VP9 em MP4)", upto(2160)),
    Preset("1440p", "2K", "Melhor resolução até 1440p", upto(1440)),
    Preset("1080p", "Full HD", "Melhor resolução até 1080p", upto(1080)),
    Preset("720p", "HD", "Melhor resolução até 720p", upto(720)),
    Preset("480p", "480p", "Melhor resolução até 480p", upto(480)),
    Preset("360p", "360p", "Arquivo leve, até 360p", upto(360)),
    Preset(
        "mp3", "MP3 320", "Extrai o áudio e converte para MP3 320 kbps",
        "ba/b", merge = "", audioCodec = "mp3", audioQuality = "320K", audioOnly = true,
    ),
    Preset(
        "m4a", "M4A", "Copia o áudio AAC original (sem perder qualidade)",
        "ba[ext=m4a]/ba/b", merge = "", audioCodec = "m4a", audioQuality = "0", audioOnly = true,
    ),
    Preset(
        "opus", "Opus", "Copia o áudio Opus original (melhor por bitrate)",
        "ba[ext=webm]/ba/b", merge = "", audioCodec = "opus", audioQuality = "0", audioOnly = true,
    ),
)

/* ------------------------------------------------------------------ */
/* fila de downloads                                                   */
/* ------------------------------------------------------------------ */

enum class JobState { QUEUED, RUNNING, DONE, ERROR, CANCELED }

class Job(
    val url: String,
    val title: String,
    val label: String,
    val selector: String,
    val merge: String,
    val audioOnly: Boolean,
    val audioCodec: String,
    val audioQuality: String,
    val playlist: Boolean,
) {
    val id: String = UUID.randomUUID().toString()
    var state by mutableStateOf(JobState.QUEUED)
    var progress by mutableFloatStateOf(0f)
    var eta by mutableLongStateOf(-1L)
    var speed by mutableStateOf("")
    var status by mutableStateOf("Na fila")
    var outputs by mutableStateOf<List<File>>(emptyList())
    var error by mutableStateOf<String?>(null)
    var cancelRequested = false
}
