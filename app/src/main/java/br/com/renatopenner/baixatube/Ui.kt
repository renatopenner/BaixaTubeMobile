package br.com.renatopenner.baixatube

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ------------------------------------------------------------------ */
/* componentes                                                         */
/* ------------------------------------------------------------------ */

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun UrlCard(vm: MainViewModel) {
    val clip = LocalClipboardManager.current
    Surface(shape = Chroma, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = vm.url,
                onValueChange = { vm.url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Link do vídeo") },
                placeholder = { Text("YouTube, Instagram, Facebook, TikTok…") },
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { vm.analyze() }),
                trailingIcon = {
                    if (vm.url.isNotEmpty()) {
                        IconButton(onClick = { vm.url = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpar")
                        }
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { clip.getText()?.text?.trim()?.let { vm.url = it } },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Colar") }
                Button(
                    onClick = { vm.analyze() },
                    enabled = !vm.analyzing && vm.engineReady,
                    modifier = Modifier.weight(1.4f).height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (vm.analyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(if (vm.meta == null) "Analisar" else "Analisar de novo")
                    }
                }
            }
            if (!vm.engineReady || vm.updatingEngine) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    if (!vm.engineReady) vm.engineStatus else "Atualizando o motor (yt-dlp)…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            vm.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun MetaCard(vm: MainViewModel) {
    val m = vm.meta ?: return
    Surface(shape = Chroma, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column {
            val bmp = rememberRemoteBitmap(m.thumb)
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    m.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(
                    m.uploader.ifBlank { null },
                    durationText(m.durationSec).ifBlank { null },
                    when {
                        m.playlist -> "playlist · ${m.playlistCount} itens"
                        m.formats.isNotEmpty() -> "${m.formats.size} formatos"
                        else -> null
                    },
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (m.playlist) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Baixar a playlist inteira",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Switch(checked = vm.playlistMode, onCheckedChange = { vm.playlistMode = it })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetSection(vm: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Qualidade")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PRESETS.forEach { p ->
                FilterChip(
                    selected = vm.selectedPreset.pid == p.pid,
                    onClick = { vm.selectedPreset = p },
                    label = { Text(p.label, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
        Text(
            vm.selectedPreset.hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { vm.queuePreset() },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Baixar · ${vm.selectedPreset.label}")
        }
    }
}

@Composable
fun FormatsSection(vm: MainViewModel) {
    val m = vm.meta ?: return
    if (m.formats.isEmpty()) return
    val shown = if (vm.showAllFormats) m.formats else m.formats.take(6)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Formatos exatos (${m.formats.size})", Modifier.weight(1f))
            TextButton(onClick = { vm.showAllFormats = !vm.showAllFormats }) {
                Text(if (vm.showAllFormats) "Ver menos" else "Ver todos")
            }
        }
        shown.forEach { f ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(f.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${f.kind} · id ${f.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.queueFormat(f) }) { Text("Baixar") }
                }
            }
        }
    }
}

@Composable
fun JobCard(job: Job, vm: MainViewModel) {
    val ctx = LocalContext.current
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        job.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        job.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    when (job.state) {
                        JobState.QUEUED -> "na fila"
                        JobState.RUNNING -> "${job.progress.toInt()}%"
                        JobState.DONE -> "concluído"
                        JobState.ERROR -> "erro"
                        JobState.CANCELED -> "cancelado"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when (job.state) {
                        JobState.DONE -> ExtraColors.ok
                        JobState.ERROR -> MaterialTheme.colorScheme.error
                        JobState.CANCELED -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            }

            when (job.state) {
                JobState.QUEUED, JobState.RUNNING -> {
                    LinearProgressIndicator(
                        progress = { (job.progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            job.status,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (job.eta > 0) {
                            Text(
                                "ETA ${durationText(job.eta)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = { vm.cancel(job) }) { Text("Cancelar") }
                }

                JobState.DONE -> {
                    Text(
                        job.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = ExtraColors.ok,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val first = job.outputs.firstOrNull()
                        if (first != null) {
                            TextButton(onClick = { openFile(ctx, first) }) { Text("Abrir") }
                            TextButton(onClick = { shareFile(ctx, first) }) { Text("Compartilhar") }
                        }
                        TextButton(onClick = { vm.tab = 1 }) { Text("Baixados") }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { vm.removeJob(job) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remover")
                        }
                    }
                }

                JobState.ERROR -> {
                    Text(
                        job.error ?: "Falhou",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row {
                        TextButton(onClick = { vm.removeJob(job) }) { Text("Remover") }
                        Spacer(Modifier.weight(1f))
                    }
                }

                JobState.CANCELED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Cancelado",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = { vm.removeJob(job) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remover")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    vm: MainViewModel,
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    onCookies: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Ajustes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tema escuro", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Mesma paleta do BaixaTube do PC",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = dark, onCheckedChange = onDark)
                }
                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Cookies de conta", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (vm.cookiesName != null) "Importado: ${vm.cookiesName}"
                        else "Instagram, Facebook e TikTok pedem login. Exporte o cookies.txt do navegador e importe aqui.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCookies) { Text("Importar") }
                        if (vm.cookiesName != null) {
                            TextButton(onClick = { vm.clearCookies() }) { Text("Remover") }
                        }
                    }
                }
                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Motor yt-dlp ${vm.engineVersion}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Atualize quando algum site parar de funcionar.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { vm.updateEngine() }, enabled = !vm.updatingEngine) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (vm.updatingEngine) "Atualizando…" else "Atualizar motor")
                    }
                }
                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Arquivos baixados", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (vm.allFilesAccess) "Acesso total liberado — a aba Baixados lista tudo."
                        else "Sem acesso total ao armazenamento: ative para ver e apagar downloads antigos.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openFolder(ctx) }) { Text("Abrir pasta") }
                        if (!vm.allFilesAccess) {
                            OutlinedButton(onClick = { requestAllFilesAccess(ctx) }) { Text("Permitir acesso") }
                        }
                    }
                }
                Text(
                    "BaixaTube Mobile ${BuildConfig.VERSION_NAME} · ${Engine.downloadDir().absolutePath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

/* ------------------------------------------------------------------ */
/* miniatura                                                           */
/* ------------------------------------------------------------------ */

@Composable
fun rememberRemoteBitmap(url: String?): ImageBitmap? {
    var bmp by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            bmp = null
            return@LaunchedEffect
        }
        bmp = withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 12000
                    instanceFollowRedirects = true
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36",
                    )
                }
                conn.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    return bmp
}

/* ------------------------------------------------------------------ */
/* intents                                                             */
/* ------------------------------------------------------------------ */

fun mimeFor(file: File): String = when (file.extension.lowercase(Locale.US)) {
    "mp4", "m4v" -> "video/mp4"
    "mkv" -> "video/x-matroska"
    "webm" -> "video/webm"
    "m4a" -> "audio/mp4"
    "mp3" -> "audio/mpeg"
    "opus", "ogg" -> "audio/ogg"
    "flac" -> "audio/flac"
    "aac" -> "audio/aac"
    "wav" -> "audio/wav"
    "mov" -> "video/quicktime"
    else -> "*/*"
}

fun uriFor(ctx: Context, file: File): Uri? = runCatching {
    FileProvider.getUriForFile(ctx, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
}.getOrNull()

fun openFile(ctx: Context, file: File) {
    val uri = uriFor(ctx, file)
    if (uri == null) {
        Toast.makeText(ctx, "Arquivo não encontrado", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeFor(file))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(intent) }.onFailure {
        Toast.makeText(ctx, "Nenhum app para abrir .${file.extension}", Toast.LENGTH_SHORT).show()
    }
}

fun shareFile(ctx: Context, file: File) {
    val uri = uriFor(ctx, file)
    if (uri == null) {
        Toast.makeText(ctx, "Arquivo não encontrado para compartilhar", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeFor(file)
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "Compartilhar ${file.name}").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        ctx.startActivity(chooser)
    }.onFailure { e ->
        Toast.makeText(ctx, "Erro ao abrir compartilhamento: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun openFolder(ctx: Context) {
    Saved.openFolder(ctx)
}

/** API 30+: manda o usuário para a tela de "Acesso a todos os arquivos" deste app */
fun requestAllFilesAccess(ctx: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val direct = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${ctx.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (!runCatching { ctx.startActivity(direct) }.isSuccess) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* aba "Baixados"                                                      */
/* ------------------------------------------------------------------ */

fun dateText(ms: Long): String =
    if (ms <= 0L) "" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(ms))

@Composable
fun SavedScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.refreshSaved() }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (vm.savedFiles.isEmpty()) "Sem arquivos"
                    else "${vm.savedFiles.size} arquivo(s) · ${mb(vm.savedFiles.sumOf { it.size })}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Download/BaixaTube",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { Saved.openFolder(ctx) }) { Text("Pasta") }
            IconButton(onClick = { vm.refreshSaved() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar lista")
            }
        }
        if (vm.savedLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!vm.allFilesAccess) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Libere \"Acesso a todos os arquivos\" para ver e apagar também os downloads antigos da pasta.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { requestAllFilesAccess(ctx) }) { Text("Permitir acesso") }
                }
            }
        }
        if (vm.savedFiles.isEmpty() && !vm.savedLoading) {
            Text(
                "Nada por aqui ainda. Baixe um vídeo na aba Baixar — o arquivo aparece nesta lista e fica em Download/BaixaTube.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
        ) {
            items(vm.savedFiles, key = { it.name }) { item ->
                SavedRow(
                    item = item,
                    onOpen = { Saved.open(ctx, item) },
                    onShare = { Saved.share(ctx, item) },
                    onDelete = { vm.deleteSaved(item) },
                )
            }
        }
    }
}

@Composable
fun SavedRow(item: SavedFile, onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 2.dp)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(
                        mb(item.size).ifBlank { null },
                        dateText(item.modified).ifBlank { null },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onOpen) { Text("Abrir") }
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = "Compartilhar")
                }
                IconButton(onClick = { confirm = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Excluir")
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Excluir arquivo?") },
            text = { Text(item.name, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onDelete()
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}

/* cor de destaque reutilizável em textos de status */
val AccentGreen = Color(0xFF3DDC97)
