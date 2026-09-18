package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            LegalSection(
                title = "1. Zero Personal Data Collection",
                body = "ABDownloader operates 100% on-device. We do not operate any backend servers, tracking servers, or analytics databases. We do not collect, transmit, store, or sell any personal data, search queries, or downloaded media."
            )
            LegalSection(
                title = "2. Storage Access & Permissions",
                body = "The app utilizes standard Android MediaStore and scoped storage APIs strictly to save media files you request to your device's public Downloads directory. No other files on your device are ever accessed, read, or modified."
            )
            LegalSection(
                title = "3. Network Communication",
                body = "Network requests originate directly from your device to the target social media host platforms (e.g., YouTube, Instagram, TikTok, Twitter/X) to retrieve video streams and format information. These requests do not route through any intermediary proxy servers."
            )
            LegalSection(
                title = "4. Zero Advertising or Tracking SDKs",
                body = "ABDownloader contains zero advertising SDKs, tracking libraries, or telemetry frameworks. Your downloads and media remain entirely private on your device."
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfUseScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms of Use", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            LegalSection(
                title = "1. Lawful & Personal Use",
                body = "ABDownloader is provided for personal, non-commercial archiving, fair-use educational review, and offline viewing. Users are solely responsible for ensuring that their use of this software complies with applicable copyright laws and platform terms."
            )
            LegalSection(
                title = "2. Copyright & Intellectual Property",
                body = "You must only download content that you own, have licensed, or have explicit permission from the copyright owner to download. ABDownloader does not endorse unauthorized downloading or redistribution of copyrighted material."
            )
            LegalSection(
                title = "3. Disclaimer of Warranty",
                body = "The application is provided 'as is' without warranties of any kind. The developers are not liable for any issues, platform rate limits, or account restrictions that may arise from using third-party services."
            )
        }
    }
}

@Composable
private fun LegalSection(title: String, body: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 19.sp
        )
    }
}
