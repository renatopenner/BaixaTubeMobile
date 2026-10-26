package br.com.renatopenner.baixatube

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent()
        setContent {
            var dark by rememberSaveable { mutableStateOf(true) }
            BaixaTubeTheme(dark) {
                AppScreen(vm = vm, dark = dark, onToggleTheme = { dark = it })
            }
        }
    }

    private fun handleShareIntent() {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = Regex("https?://\\S+").find(text)?.value?.trim() ?: return
        vm.url = url
    }
}

@Composable
fun AppScreen(vm: MainViewModel, dark: Boolean, onToggleTheme: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.message = "Sem permissão de armazenamento não consigo salvar em Download."
    }
    val cookiesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.setCookies(uri)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    val listState = rememberLazyListState()
    val jobCount = vm.jobs.size
    val downloadsIndex = if (vm.meta != null) 4 else 1
    LaunchedEffect(jobCount) {
        if (jobCount > 0) {
            kotlinx.coroutines.delay(150)
            runCatching { listState.animateScrollToItem(downloadsIndex) }
        }
    }

    LaunchedEffect(vm.tab) {
        if (vm.tab == 1) vm.refreshSaved()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopRow(vm.engineVersion) { showSettings = true }
                TabsRow(vm)
            }
        },
    ) { padding ->
        if (vm.tab == 1) {
            SavedScreen(vm, Modifier.padding(padding).navigationBarsPadding())
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { UrlCard(vm) }

                val meta = vm.meta
                if (meta != null) {
                    item { MetaCard(vm) }
                    item { PresetSection(vm) }
                    item { FormatsSection(vm) }
                }

                if (vm.jobs.isNotEmpty()) {
                    item { SectionTitle("Downloads") }
                    items(vm.jobs, key = { it.id }) { job -> JobCard(job, vm) }
                }

                item {
                    Text(
                        "Arquivos salvos em Download/BaixaTube",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            vm = vm,
            dark = dark,
            onDark = onToggleTheme,
            onCookies = { cookiesLauncher.launch(arrayOf("*/*")) },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
fun TabsRow(vm: MainViewModel) {
    val tabs = listOf(
        "Baixar",
        if (vm.savedFiles.isEmpty()) "Baixados" else "Baixados (${vm.savedFiles.size})",
    )
    TabRow(
        selectedTabIndex = vm.tab,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = {},
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = vm.tab == index,
                onClick = {
                    vm.tab = index
                    if (index == 1) vm.refreshSaved()
                },
                text = {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (vm.tab == index) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}

@Composable
fun TopRow(version: String, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("↓", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "BaixaTube",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    version.isBlank() || version == "-" -> "mobile · motor local"
                    else -> "mobile · yt-dlp $version"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
        }
    }
}
