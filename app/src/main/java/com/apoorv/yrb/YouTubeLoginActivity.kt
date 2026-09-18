package com.apoorv.yrb

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.apoorv.yrb.download.SessionStore
import com.apoorv.yrb.ui.theme.YrbTheme

class YouTubeLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            YrbTheme {
                YouTubeSessionBrowser(
                    onClose = { finish() },
                    onSessionSaved = {
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun YouTubeSessionBrowser(
        onClose: () -> Unit,
        onSessionSaved: () -> Unit
    ) {
        val cookieManager = remember { CookieManager.getInstance() }
        val sessionStore = remember { SessionStore(this) }
        var webView by remember { mutableStateOf<WebView?>(null) }
        var message by remember {
            mutableStateOf(
                "Sign in to YouTube in this browser. Yrb never sees your password. " +
                    "When YouTube shows you as signed in, tap Use this session."
            )
        }
        var saving by remember { mutableStateOf(false) }

        BackHandler {
            val current = webView
            if (current?.canGoBack() == true) {
                current.goBack()
            } else {
                onClose()
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                webView?.stopLoading()
                webView?.destroy()
                webView = null
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Close")
                        }
                    },
                    title = {
                        Text(
                            "Connect YouTube",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 10.dp,
                                bottom = 16.dp
                            )
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving && webView != null,
                        onClick = {
                            val current = webView ?: return@Button
                            saving = true

                            sessionStore.captureFromWebView(
                                cookieManager = cookieManager,
                                userAgent = current.settings.userAgentString.orEmpty()
                            ).onSuccess {
                                message = "YouTube session connected."
                                onSessionSaved()
                            }.onFailure {
                                message = it.message
                                    ?: "Could not detect a signed-in YouTube session yet."
                                saving = false
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        Text(
                            if (saving) "Saving session…" else "Use this session",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        ) { padding ->
            AndroidView(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.setSupportMultipleWindows(false)

                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val scheme = request?.url?.scheme
                                return scheme != "http" && scheme != "https"
                            }
                        }

                        webView = this
                        loadUrl("https://www.youtube.com/")
                    }
                }
            )
        }
    }
}
