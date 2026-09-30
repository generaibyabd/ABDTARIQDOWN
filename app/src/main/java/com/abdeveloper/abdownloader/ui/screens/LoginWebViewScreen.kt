package com.abdeveloper.abdownloader.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager as WebKitCookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.abdeveloper.abdownloader.ui.theme.AccentBlue
import com.abdeveloper.abdownloader.ui.viewmodels.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginWebViewScreen(
    platformKey: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val platformInfo = remember(platformKey) {
        when (platformKey.lowercase()) {
            "instagram" -> Triple(
                "Instagram",
                "https://www.instagram.com/accounts/login/",
                "instagram.com"
            )
            "tiktok" -> Triple(
                "TikTok",
                "https://www.tiktok.com/login",
                "tiktok.com"
            )
            "twitter" -> Triple(
                "X (Twitter)",
                "https://x.com/login",
                "x.com"
            )
            else -> Triple(
                platformKey.replaceFirstChar { it.uppercase() },
                "https://www.${platformKey.lowercase()}.com",
                "${platformKey.lowercase()}.com"
            )
        }
    }

    val (displayName, loginUrl, domain) = platformInfo

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var loginDetected by remember { mutableStateOf(false) }

    fun checkCookiesForLogin(): Boolean {
        return try {
            val cookieJar = WebKitCookieManager.getInstance()
            val domainUrl = when (platformKey.lowercase()) {
                "instagram" -> "https://www.instagram.com"
                "tiktok" -> "https://www.tiktok.com"
                "twitter" -> "https://x.com"
                else -> "https://www.$domain"
            }
            val rawCookies = cookieJar.getCookie(domainUrl) ?: ""
            when (platformKey.lowercase()) {
                "instagram" -> rawCookies.contains("sessionid=") || rawCookies.contains("ds_user_id=")
                "tiktok" -> rawCookies.contains("sessionid=") || rawCookies.contains("sessionid_ss=") || rawCookies.contains("sid_tt=")
                "twitter" -> rawCookies.contains("auth_token=") || rawCookies.contains("twid=")
                else -> rawCookies.isNotBlank()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun completeLogin() {
        val cookieJar = WebKitCookieManager.getInstance()
        cookieJar.flush()

        val domainUrl = when (platformKey.lowercase()) {
            "instagram" -> "https://www.instagram.com"
            "tiktok" -> "https://www.tiktok.com"
            "twitter" -> "https://x.com"
            else -> "https://www.$domain"
        }

        var rawCookies = cookieJar.getCookie(domainUrl) ?: ""
        if (platformKey.lowercase() == "twitter") {
            val twCookies = cookieJar.getCookie("https://twitter.com") ?: ""
            if (twCookies.isNotBlank()) {
                rawCookies = if (rawCookies.isBlank()) twCookies else "$rawCookies; $twCookies"
            }
        }

        if (rawCookies.isNotBlank()) {
            viewModel.saveWebViewCookies(platformKey, domain, rawCookies)
        }
        onBack()
    }

    BackHandler {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Log in to $displayName",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Official web login • On-device only",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (loginDetected) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Login session detected! Tap below to finish.",
                                    color = Color(0xFF2E7D32),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { completeLogin() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("done_logged_in_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Text(
                            text = "Done, I'm Logged In",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        val cookieJar = WebKitCookieManager.getInstance()
                        cookieJar.setAcceptCookie(true)
                        cookieJar.setAcceptThirdPartyCookies(this, true)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageProgress = newProgress / 100f
                                isLoading = newProgress < 100
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                if (checkCookiesForLogin()) {
                                    loginDetected = true
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val destUrl = request?.url?.toString().orEmpty()
                                // If redirected away from login page to home/feed, check login
                                if (checkCookiesForLogin()) {
                                    loginDetected = true
                                }
                                return false
                            }
                        }

                        loadUrl(loginUrl)
                        webViewRef = this
                    }
                }
            )

            if (isLoading) {
                LinearProgressIndicator(
                    progress = { pageProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = AccentBlue,
                    trackColor = AccentBlue.copy(alpha = 0.2f)
                )
            }
        }
    }
}
