package br.com.renatopenner.baixatube

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/**
 * Arquivo já baixado. Vem por caminho direto (`file`) ou, quando o Android
 * esconde a pasta por scoped storage, pelo MediaStore (`uri`).
 */
data class SavedFile(
    val name: String,
    val size: Long,
    val modified: Long,
    val mime: String,
    val file: File?,
    val uri: Uri?,
)

object Saved {

    private val mediaExt = setOf(
        "mp4", "mkv", "webm", "mov", "m4v", "3gp",
        "mp3", "m4a", "opus", "ogg", "flac", "wav", "aac",
    )

    /** lista Download/BaixaTube combinando acesso direto + MediaStore */
    fun list(ctx: Context): List<SavedFile> {
        val out = LinkedHashMap<String, SavedFile>()
        val dir = Engine.downloadDir()

        runCatching {
            dir.listFiles()?.forEach { f ->
                if (!f.isFile) return@forEach
                if (f.name.startsWith(".")) return@forEach
                val ext = f.extension.lowercase(Locale.US)
                if (ext !in mediaExt) return@forEach
                out[f.name] = SavedFile(
                    name = f.name,
                    size = f.length(),
                    modified = f.lastModified(),
                    mime = mimeFor(f),
                    file = f,
                    uri = null,
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val collection = MediaStore.Files.getContentUri("external")
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.MediaColumns.MIME_TYPE,
                )
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                val args = arrayOf("Download/BaixaTube/%")
                ctx.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(1) ?: continue
                        if (name in out) continue
                        if (name.startsWith(".")) continue
                        if (name.substringAfterLast('.', "").lowercase(Locale.US) !in mediaExt) continue
                        val id = c.getLong(0)
                        val size = c.getLong(2)
                        val modified = c.getLong(3) * 1000L
                        val mime = c.getString(4) ?: "*/*"
                        out[name] = SavedFile(
                            name = name,
                            size = size,
                            modified = modified,
                            mime = mime,
                            file = null,
                            uri = ContentUris.withAppendedId(collection, id),
                        )
                    }
                }
            }
        }

        return out.values.sortedByDescending { it.modified }
    }

    fun open(ctx: Context, item: SavedFile) {
        val data = item.uri ?: item.file?.let { uriFor(ctx, it) } ?: return
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(data, item.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }.onFailure {
            Toast.makeText(ctx, "Nenhum app para abrir este arquivo", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(ctx: Context, item: SavedFile) {
        val data = item.uri ?: item.file?.let { uriFor(ctx, it) } ?: return
        val intent = Intent(Intent.ACTION_SEND)
            .setType(item.mime)
            .putExtra(Intent.EXTRA_STREAM, data)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { ctx.startActivity(Intent.createChooser(intent, "Compartilhar")) }
    }

    fun delete(ctx: Context, item: SavedFile): Boolean {
        val byFile = item.file?.let { runCatching { it.delete() }.getOrDefault(false) } ?: false
        if (byFile) return true
        return item.uri?.let { uri ->
            runCatching { ctx.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
        } ?: false
    }

    /** abre a pasta no gerenciador de arquivos do sistema; false se nenhum aceitou */
    fun openFolder(ctx: Context): Boolean {
        val dir = Engine.downloadDir()

        // 1) gerenciador de arquivos direto na pasta
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val doc = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Download/BaixaTube",
                )
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(doc, DocumentsContract.Document.MIME_TYPE_DIR)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return true
            }
        }

        // 2) FileProvider (funciona em vários gerenciadores de terceiros)
        runCatching {
            val uri = FileProvider.getUriForFile(
                ctx, "${BuildConfig.APPLICATION_ID}.fileprovider", dir,
            )
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "resource/folder")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
            return true
        }

        // 3) seletor de pastas do sistema (sempre existe), começando em Download
        runCatching {
            val start = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:Download",
            )
            ctx.startActivity(
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .putExtra(DocumentsContract.EXTRA_INITIAL_URI, start)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return true
        }

        // 4) último recurso: caminho na área de transferência
        val path = dir.absolutePath
        runCatching {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("pasta", path))
        }
        Toast.makeText(ctx, "Caminho copiado: $path", Toast.LENGTH_LONG).show()
        return false
    }
}
