package com.apoorv.yrb

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.apoorv.yrb.data.DownloadRecord
import com.apoorv.yrb.data.DownloadStatus
import com.apoorv.yrb.data.HistoryStore
import com.apoorv.yrb.download.DownloadService
import com.apoorv.yrb.download.FileSizeFormatter
import com.apoorv.yrb.download.QualityOption
import com.apoorv.yrb.download.SessionStore
import com.apoorv.yrb.download.VideoInspection
import com.apoorv.yrb.download.YtDlpClient
import com.apoorv.yrb.ui.theme.YrbTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var historyVersion by mutableIntStateOf(0)
    private var receiverRegistered = false
    private var requestedJobId by mutableStateOf<String?>(null)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val historyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            historyVersion++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedJobId = intent.getStringExtra(EXTRA_OPEN_JOB_ID)

        setContent {
            YrbTheme {
                YrbApp(
                    historyVersion = historyVersion,
                    requestedJobId = requestedJobId
                )
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedJobId = intent.getStringExtra(EXTRA_OPEN_JOB_ID)
        historyVersion++
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
    private fun YrbApp(
        historyVersion: Int,
        requestedJobId: String?
    ) {
        var screen by rememberSaveable {
            mutableIntStateOf(if (requestedJobId != null) SCREEN_DOWNLOADING else SCREEN_HOME)
        }
        var activeJobId by rememberSaveable { mutableStateOf(requestedJobId) }

        LaunchedEffect(requestedJobId) {
            if (requestedJobId != null) {
                activeJobId = requestedJobId
                screen = SCREEN_DOWNLOADING
            }
        }

        BackHandler(enabled = screen == SCREEN_DOWNLOADING) {
            screen = SCREEN_HOME
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    navigationIcon = {
                        if (screen == SCREEN_DOWNLOADING) {
                            IconButton(onClick = { screen = SCREEN_HOME }) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    title = {
                        Text(
                            when (screen) {
                                SCREEN_HISTORY -> "History"
                                SCREEN_DOWNLOADING -> "Download"
                                else -> "YRB by Apoorv"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            },
            bottomBar = {
                if (screen != SCREEN_DOWNLOADING) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationBarItem(
                            selected = screen == SCREEN_HOME,
                            onClick = { screen = SCREEN_HOME },
                            icon = {
                                Icon(Icons.Rounded.Download, contentDescription = null)
                            },
                            label = { Text("Download") }
                        )
                        NavigationBarItem(
                            selected = screen == SCREEN_HISTORY,
                            onClick = { screen = SCREEN_HISTORY },
                            icon = {
                                Icon(Icons.Rounded.History, contentDescription = null)
                            },
                            label = { Text("History") }
                        )
                    }
                }
            }
        ) { padding ->
            when (screen) {
                SCREEN_HISTORY -> HistoryScreen(
                    historyVersion = historyVersion,
                    modifier = Modifier.padding(padding),
                    onOpenJob = { jobId ->
                        activeJobId = jobId
                        screen = SCREEN_DOWNLOADING
                    }
                )

                SCREEN_DOWNLOADING -> DownloadingScreen(
                    jobId = activeJobId,
                    historyVersion = historyVersion,
                    modifier = Modifier.padding(padding),
                    onBackHome = { screen = SCREEN_HOME }
                )

                else -> HomeScreen(
                    modifier = Modifier.padding(padding),
                    onJobStarted = { jobId ->
                        activeJobId = jobId
                        screen = SCREEN_DOWNLOADING
                    }
                )
            }
        }
    }

    @Composable
    private fun HomeScreen(
        modifier: Modifier = Modifier,
        onJobStarted: (String) -> Unit
    ) {
        var url by rememberSaveable { mutableStateOf("") }
        var inspection by remember { mutableStateOf<VideoInspection?>(null) }
        var selectedLanguageId by remember { mutableStateOf<String?>(null) }
        var loading by remember { mutableStateOf(false) }
        var inspectStatusIndex by remember { mutableIntStateOf(0) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        val sessionStore = remember { SessionStore(this@MainActivity) }
        var sessionStatus by remember { mutableStateOf(sessionStore.status()) }
        var sessionNotice by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(loading) {
            if (loading) {
                inspectStatusIndex = 0
                while (true) {
                    delay(2700L)
                    inspectStatusIndex = (inspectStatusIndex + 1) % INSPECTION_STEPS.size
                }
            }
        }

        val sessionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                sessionStore.importFrom(uri)
                    .onSuccess {
                        sessionStatus = it
                        sessionNotice = "YouTube session connected. Retry the video."
                        error = null
                    }
                    .onFailure {
                        sessionNotice = null
                        error = it.message ?: "Could not import this YouTube session."
                    }
            }
        }

        val sessionLoginLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                sessionStatus = sessionStore.status()
                sessionNotice = if (sessionStatus.connected) {
                    null
                } else {
                    "The sign-in window closed without a usable YouTube session."
                }
                if (sessionStatus.connected) error = null
            }
        }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Download the quality you actually want.",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Everything runs on this phone. Yrb checks the video first, shows the available resolutions and estimated file sizes, then downloads locally.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = if (sessionStatus.connected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    }
                ) {
                    if (sessionStatus.connected) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "YouTube connected",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Downloads use this session automatically.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(
                                onClick = {
                                    sessionLoginLauncher.launch(
                                        Intent(
                                            this@MainActivity,
                                            YouTubeLoginActivity::class.java
                                        )
                                    )
                                }
                            ) {
                                Text("Refresh")
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Connect YouTube",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Sign in once inside Yrb before downloading. This keeps the download controls simple afterwards.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                            FilledTonalButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    sessionLoginLauncher.launch(
                                        Intent(
                                            this@MainActivity,
                                            YouTubeLoginActivity::class.java
                                        )
                                    )
                                }
                            ) {
                                Text("Sign in to YouTube")
                            }
                            TextButton(
                                onClick = {
                                    sessionLauncher.launch(
                                        arrayOf(
                                            "text/plain",
                                            "text/*",
                                            "application/octet-stream"
                                        )
                                    )
                                }
                            ) {
                                Text("Advanced: import cookies.txt")
                            }
                        }
                    }
                }
            }

            sessionNotice?.let { notice ->
                item {
                    Text(
                        notice,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = {
                                url = it
                                inspection = null
                                selectedLanguageId = null
                                error = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Rounded.Link, contentDescription = null)
                            },
                            label = { Text("YouTube link") },
                            placeholder = { Text("youtube.com/watch?v=...") }
                        )

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
                                selectedLanguageId = null

                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            YtDlpClient.inspect(this@MainActivity, url.trim())
                                        }
                                    }.onSuccess {
                                        inspection = it
                                        selectedLanguageId = it.defaultLanguageId
                                    }.onFailure {
                                        error = it.message ?: "Could not inspect this video."
                                    }
                                    loading = false
                                }
                            }
                        ) {
                            Text(if (loading) "Inspecting…" else "Inspect video")
                        }
                    }
                }
            }

            if (loading) {
                item {
                    val step = INSPECTION_STEPS[inspectStatusIndex % INSPECTION_STEPS.size]
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(step.first, fontWeight = FontWeight.SemiBold)
                            Text(
                                step.second,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            error?.let { message ->
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    message,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            if (requiresYouTubeSession(message)) {
                                FilledTonalButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        sessionLoginLauncher.launch(
                                            Intent(
                                                this@MainActivity,
                                                YouTubeLoginActivity::class.java
                                            )
                                        )
                                    }
                                ) {
                                    Text(
                                        if (sessionStatus.connected) {
                                            "Reconnect YouTube"
                                        } else {
                                            "Sign in to YouTube"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            inspection?.let { info ->
                val languageId = selectedLanguageId ?: info.defaultLanguageId
                val languageLabel = info.audioLanguages
                    .firstOrNull { it.id == languageId }
                    ?.label
                    ?: "Default audio"
                val qualityOptions = info.qualities(languageId)
                val audioOnlyOption = info.audioOnly(languageId)

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            info.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val duration = info.durationSeconds?.let(::formatDuration)
                        if (duration != null) {
                            Text(
                                duration,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            "Choose a video quality or download only the selected audio track.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (info.audioLanguages.size > 1) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Audio language",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(info.audioLanguages, key = { it.id }) { language ->
                                    FilterChip(
                                        selected = languageId == language.id,
                                        onClick = { selectedLanguageId = language.id },
                                        label = { Text(language.label) }
                                    )
                                }
                            }
                        }
                    }
                }

                audioOnlyOption?.let { option ->
                    item(key = "audio-only-" + languageId) {
                        QualityCard(
                            option = option,
                            onClick = {
                                val jobId = startDownload(
                                    url = url.trim(),
                                    title = info.title,
                                    option = option,
                                    audioLanguage = languageLabel
                                )
                                onJobStarted(jobId)
                            }
                        )
                    }
                }

                items(qualityOptions, key = { it.height }) { option ->
                    QualityCard(
                        option = option,
                        onClick = {
                            val jobId = startDownload(
                                url = url.trim(),
                                title = info.title,
                                option = option,
                                audioLanguage = languageLabel
                            )
                            onJobStarted(jobId)
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Finished files are saved in Downloads/Yrb. Partial video/audio fragments stay inside a temporary .partial folder until the job completes.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            item {
                AttributionFooter()
            }
        }
    }

    @Composable
    private fun AttributionFooter() {
        val uriHandler = LocalUriHandler.current

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Made by Apoorv Sandilya",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "contact@apoorv.sbs",
                    modifier = Modifier.clickable {
                        uriHandler.openUri("mailto:contact@apoorv.sbs")
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "X · @sandilyapoorv",
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://x.com/sandilyapoorv")
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Instagram · apoorvsandilya",
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://instagram.com/apoorvsandilya")
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    @Composable
    private fun QualityCard(
        option: QualityOption,
        onClick: () -> Unit
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        option.label,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (option.approximate && option.estimatedBytes != null) {
                            "≈ " + FileSizeFormatter.format(option.estimatedBytes)
                        } else {
                            FileSizeFormatter.format(option.estimatedBytes)
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        when {
                            option.estimatedBytes == null ->
                                "YouTube did not expose a reliable size estimate."
                            option.audioOnly ->
                                "Audio track only • exported as M4A"
                            else ->
                                "Video + audio total"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Icon(
                    Icons.Rounded.Download,
                    contentDescription = "Download " + option.label
                )
            }
        }
    }

    @Composable
    private fun DownloadingScreen(
        jobId: String?,
        historyVersion: Int,
        modifier: Modifier = Modifier,
        onBackHome: () -> Unit
    ) {
        if (jobId == null) {
            EmptyDownloadState(modifier, onBackHome)
            return
        }

        val store = remember { HistoryStore(this@MainActivity) }
        val record = remember(historyVersion, jobId) { store.find(jobId) }

        if (record == null) {
            EmptyDownloadState(modifier, onBackHome)
            return
        }

        val active = DownloadStatus.isActive(record.status)
        val preparing =
            active && record.progress <= 0f && record.downloadedBytes <= 0L
        val preparationSteps =
            if (record.quality == 0) AUDIO_PREPARATION_STEPS else VIDEO_PREPARATION_STEPS
        var preparationIndex by remember(record.id) { mutableIntStateOf(0) }

        LaunchedEffect(record.id, preparing) {
            if (preparing) {
                preparationIndex = 0
                while (true) {
                    delay(2700L)
                    preparationIndex = (preparationIndex + 1) % preparationSteps.size
                }
            }
        }

        val preparationStep =
            preparationSteps[preparationIndex % preparationSteps.size]

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        record.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            append(qualityLabel(record.quality))
                            record.audioLanguage?.let {
                                append(" • ")
                                append(it)
                            }
                            append(" • ")
                            append(FileSizeFormatter.format(record.estimatedBytes))
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (preparing) preparationStep.first else record.stage,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (preparing) "Preparing" else record.progress.roundToInt().toString() + "%",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (preparing) {
                            Text(
                                preparationStep.second,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (preparing) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else if (record.progress > 0f || record.estimatedBytes != null) {
                            LinearProgressIndicator(
                                progress = { (record.progress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatBlock(
                                modifier = Modifier.weight(1f),
                                label = "Speed",
                                value = if (record.speedBytesPerSecond > 0L) {
                                    FileSizeFormatter.format(record.speedBytesPerSecond) + "/s"
                                } else "—"
                            )
                            StatBlock(
                                modifier = Modifier.weight(1f),
                                label = "ETA",
                                value = record.etaSeconds?.let(::formatEta) ?: "—"
                            )
                            StatBlock(
                                modifier = Modifier.weight(1f),
                                label = "Written",
                                value = FileSizeFormatter.format(record.downloadedBytes)
                            )
                        }
                    }
                }
            }

            if (active) {
                item {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            startService(DownloadService.cancelIntent(this@MainActivity))
                        }
                    ) {
                        Text("Cancel download")
                    }
                }
            }

            if (record.status == DownloadStatus.COMPLETED) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Download complete", fontWeight = FontWeight.Bold)
                                Text(
                                    "Saved to Downloads/Yrb",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                record.filePath?.let { path ->
                    item {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { openMedia(File(path)) }
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text(if (record.quality == 0) "Open audio" else "Open video")
                        }
                    }
                }
            }

            if (record.status == DownloadStatus.FAILED) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "Download failed",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Text(
                                record.error ?: "The download could not be completed.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            if (!active) {
                item {
                    FilledTonalButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onBackHome
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text("Back to downloads")
                    }
                }
            }
        }
    }

    @Composable
    private fun EmptyDownloadState(
        modifier: Modifier,
        onBackHome: () -> Unit
    ) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Download not found", style = MaterialTheme.typography.titleLarge)
                FilledTonalButton(onClick = onBackHome) {
                    Text("Back")
                }
            }
        }
    }

    @Composable
    private fun StatBlock(
        modifier: Modifier,
        label: String,
        value: String
    ) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    value,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    @Composable
    private fun HistoryScreen(
        historyVersion: Int,
        modifier: Modifier = Modifier,
        onOpenJob: (String) -> Unit
    ) {
        val store = remember { HistoryStore(this@MainActivity) }
        var localRefresh by remember { mutableIntStateOf(0) }
        val history = remember(historyVersion, localRefresh) { store.readAll() }

        if (history.isEmpty()) {
            Box(
                modifier = modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.History, contentDescription = null)
                    Text("Nothing here yet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Active and completed downloads will appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return
        }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            store.clearFinished()
                            localRefresh++
                        }
                    ) {
                        Text("Clear finished")
                    }
                }
            }

            items(history, key = { it.id }) { record ->
                HistoryCard(record, onOpenJob)
            }
        }
    }

    @Composable
    private fun HistoryCard(
        record: DownloadRecord,
        onOpenJob: (String) -> Unit
    ) {
        val file = record.filePath?.let(::File)
        val canOpen = record.status == DownloadStatus.COMPLETED && file?.exists() == true
        val active = DownloadStatus.isActive(record.status)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    when {
                        active -> onOpenJob(record.id)
                        canOpen -> openMedia(requireNotNull(file))
                    }
                }
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            record.title,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            buildString {
                                append(qualityLabel(record.quality))
                                record.audioLanguage?.let {
                                    append(" • ")
                                    append(it)
                                }
                                append(" • ")
                                append(record.stage)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Text(
                        DateFormat.getDateTimeInstance(
                            DateFormat.SHORT,
                            DateFormat.SHORT
                        ).format(Date(record.timestamp)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (active) {
                    if (record.progress > 0f || record.estimatedBytes != null) {
                        LinearProgressIndicator(
                            progress = { (record.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        buildString {
                            append(record.progress.roundToInt())
                            append("%")
                            if (record.speedBytesPerSecond > 0L) {
                                append(" • ")
                                append(FileSizeFormatter.format(record.speedBytesPerSecond))
                                append("/s")
                            }
                            record.etaSeconds?.let {
                                append(" • ")
                                append(formatEta(it))
                                append(" left")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    HorizontalDivider()
                    Text(
                        when (record.status) {
                            DownloadStatus.COMPLETED ->
                                FileSizeFormatter.format(record.downloadedBytes) + " • Tap to open"
                            DownloadStatus.CANCELLED -> "Cancelled"
                            else -> record.error ?: "Failed"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (record.status == DownloadStatus.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }

    private fun startDownload(
        url: String,
        title: String,
        option: QualityOption,
        audioLanguage: String
    ): String {
        val store = HistoryStore(this)
        val existingActive = store.readAll().firstOrNull {
            DownloadStatus.isActive(it.status)
        }
        if (existingActive != null) return existingActive.id

        val jobId = UUID.randomUUID().toString()
        store.upsert(
            DownloadRecord(
                id = jobId,
                title = title,
                url = url,
                quality = option.height,
                audioLanguage = audioLanguage,
                status = DownloadStatus.QUEUED,
                stage = "Queued",
                progress = 0f,
                estimatedBytes = option.estimatedBytes,
                timestamp = System.currentTimeMillis()
            )
        )
        historyVersion++

        ContextCompat.startForegroundService(
            this,
            DownloadService.createIntent(
                context = this,
                jobId = jobId,
                url = url,
                title = title,
                quality = option
            )
        )

        return jobId
    }

    private fun openMedia(file: File) {
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(this, packageName + ".files", file)
        val extension = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension)
            ?: if (extension in setOf("m4a", "mp3", "aac", "ogg", "opus")) {
                "audio/*"
            } else {
                "video/*"
            }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
    }

    private fun requiresYouTubeSession(message: String): Boolean {
        val normalized = message.lowercase()
        return normalized.contains("sign in to confirm") ||
            normalized.contains("not a bot") ||
            normalized.contains("authenticated session") ||
            normalized.contains("po token") ||
            normalized.contains("every anonymous playback route")
    }

    private fun isYouTubeUrl(value: String): Boolean =
        runCatching {
            val host = Uri.parse(value.trim()).host?.lowercase() ?: return false
            host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
        }.getOrDefault(false)

    private fun qualityLabel(quality: Int): String =
        when (quality) {
            0 -> "Audio only"
            2160 -> "4K"
            else -> quality.toString() + "p"
        }

    private fun formatDuration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val hours = safe / 3600L
        val minutes = (safe % 3600L) / 60L
        val remaining = safe % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, remaining)
        } else {
            "%d:%02d".format(minutes, remaining)
        }
    }

    private fun formatEta(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val minutes = safe / 60L
        val remaining = safe % 60L
        return if (minutes > 0L) {
            minutes.toString() + "m " + remaining.toString() + "s"
        } else {
            remaining.toString() + "s"
        }
    }

    companion object {
        const val EXTRA_OPEN_JOB_ID = "open_job_id"
        private const val SCREEN_HOME = 0
        private const val SCREEN_HISTORY = 1
        private const val SCREEN_DOWNLOADING = 2

        private val INSPECTION_STEPS = listOf(
            "Reading video details" to "Checking the title, duration and available media streams.",
            "Finding video streams" to "Looking for the resolutions YouTube exposes for this video.",
            "Finding audio tracks" to "Checking the best audio stream for the selected video.",
            "Finding languages" to "Looking for alternate and original-language audio tracks.",
            "Checking quality options" to "Matching available resolutions with their audio streams.",
            "Preparing choices" to "Calculating sizes and getting the download options ready."
        )

        private val VIDEO_PREPARATION_STEPS = listOf(
            "Fetching video" to "Opening the selected YouTube media route.",
            "Locking selected quality" to "Keeping the resolution you chose for this download.",
            "Matching audio track" to "Pairing the selected language with the video stream.",
            "Preparing local download" to "Setting up the on-device download and merge pipeline.",
            "Starting transfer" to "Waiting for the first media bytes from YouTube."
        )

        private val AUDIO_PREPARATION_STEPS = listOf(
            "Fetching audio" to "Opening the selected YouTube audio stream.",
            "Locking selected language" to "Keeping the audio language you chose.",
            "Preparing audio track" to "Setting up the audio-only download on this phone.",
            "Preparing M4A" to "Getting the final audio container ready.",
            "Starting transfer" to "Waiting for the first audio bytes from YouTube."
        )
    }
}
