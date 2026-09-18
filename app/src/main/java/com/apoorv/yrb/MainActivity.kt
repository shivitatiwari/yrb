package com.apoorv.yrb

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.apoorv.yrb.data.HistoryEntry
import com.apoorv.yrb.data.HistoryStore
import com.apoorv.yrb.download.DownloadService
import com.apoorv.yrb.download.VideoInspection
import com.apoorv.yrb.download.YtDlpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private var historyVersion by mutableIntStateOf(0)
    private var receiverRegistered = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val historyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            historyVersion++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                YrbApp(historyVersion = historyVersion)
            }
        }

        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                historyReceiver,
                IntentFilter(DownloadService.ACTION_HISTORY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
        historyVersion++
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(historyReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun YrbApp(historyVersion: Int) {
        var tab by rememberSaveable { mutableIntStateOf(0) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Yrb", fontWeight = FontWeight.Bold)
                            Text("On-device yt-dlp", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Text("↓") },
                        label = { Text("Download") }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Text("↺") },
                        label = { Text("History") }
                    )
                }
            }
        ) { padding ->
            if (tab == 0) {
                DownloadScreen(Modifier.padding(padding))
            } else {
                HistoryScreen(historyVersion, Modifier.padding(padding))
            }
        }
    }

    @Composable
    private fun DownloadScreen(modifier: Modifier = Modifier) {
        var url by rememberSaveable { mutableStateOf("") }
        var inspection by remember { mutableStateOf<VideoInspection?>(null) }
        var selectedQuality by remember { mutableStateOf<Int?>(null) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Paste a YouTube link. Yrb checks formats on this device and only shows resolutions the video actually exposes.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item {
                TextField(
                    value = url,
                    onValueChange = {
                        url = it
                        inspection = null
                        selectedQuality = null
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("YouTube URL") },
                    placeholder = { Text("https://youtu.be/...") }
                )
            }
            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading && url.isNotBlank(),
                    onClick = {
                        if (!isYouTubeUrl(url)) {
                            error = "Enter a valid youtube.com or youtu.be link."
                            return@Button
                        }
                        loading = true
                        error = null
                        inspection = null
                        selectedQuality = null
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    YtDlpClient.inspect(url.trim())
                                }
                            }.onSuccess {
                                inspection = it
                                selectedQuality = it.qualities.firstOrNull()
                            }.onFailure {
                                error = it.message ?: "Could not inspect this video."
                            }
                            loading = false
                        }
                    }
                ) {
                    if (loading) {
                        CircularProgressIndicator()
                    } else {
                        Text("Check available qualities")
                    }
                }
            }

            error?.let { message ->
                item {
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
            }

            inspection?.let { info ->
                item {
                    Text(
                        info.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                item {
                    Text("Available", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(info.qualities) { quality ->
                            FilterChip(
                                selected = selectedQuality == quality,
                                onClick = { selectedQuality = quality },
                                label = { Text(qualityLabel(quality)) }
                            )
                        }
                    }
                }
                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedQuality != null,
                        onClick = {
                            val quality = selectedQuality ?: return@Button
                            val intent = DownloadService.createIntent(
                                this@MainActivity,
                                url.trim(),
                                info.title,
                                quality
                            )
                            ContextCompat.startForegroundService(this@MainActivity, intent)
                        }
                    ) {
                        Text("Download " + (selectedQuality?.let(::qualityLabel) ?: ""))
                    }
                }
                item {
                    Text(
                        "Files are saved to Downloads/Yrb. Progress continues in a foreground notification.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    @Composable
    private fun HistoryScreen(historyVersion: Int, modifier: Modifier = Modifier) {
        val store = remember { HistoryStore(this@MainActivity) }
        var history by remember(historyVersion) { mutableStateOf(store.readAll()) }

        if (history.isEmpty()) {
            Column(
                modifier = modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No downloads yet", style = MaterialTheme.typography.titleMedium)
                Text("Completed and failed downloads will appear here.")
            }
            return
        }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            store.clear()
                            history = emptyList()
                        }
                    ) {
                        Text("Clear history")
                    }
                }
            }
            items(history, key = { it.id }) { entry ->
                HistoryCard(entry)
            }
        }
    }

    @Composable
    private fun HistoryCard(entry: HistoryEntry) {
        val file = entry.path?.let(::File)
        val canOpen = entry.status == "completed" && file?.exists() == true

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (canOpen) {
                        Modifier.clickable { openVideo(requireNotNull(file)) }
                    } else {
                        Modifier
                    }
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(entry.title, fontWeight = FontWeight.SemiBold)
                Text(
                    qualityLabel(entry.quality) + " • " + entry.status,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    DateFormat.getDateTimeInstance().format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.bodySmall
                )
                entry.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (entry.status == "completed" && !canOpen) {
                    Text("File was moved or deleted.", style = MaterialTheme.typography.bodySmall)
                }
                if (canOpen) {
                    Text("Tap to open", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    private fun openVideo(file: File) {
        val uri = FileProvider.getUriForFile(this, packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
    }

    private fun isYouTubeUrl(value: String): Boolean {
        return runCatching {
            val host = Uri.parse(value.trim()).host?.lowercase() ?: return false
            host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
        }.getOrDefault(false)
    }

    private fun qualityLabel(quality: Int): String {
        return if (quality == 2160) "4K" else quality.toString() + "p"
    }
}
