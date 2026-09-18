package com.abdeveloper.abdownloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abdeveloper.abdownloader.settings.AppThemeMode
import com.abdeveloper.abdownloader.ui.components.DeleteConfirmDialog
import com.abdeveloper.abdownloader.ui.components.PlaylistSelectorBottomSheet
import com.abdeveloper.abdownloader.ui.components.QualityPickerBottomSheet
import com.abdeveloper.abdownloader.ui.components.TaskDetailsDialog
import com.abdeveloper.abdownloader.ui.screens.AppInfoScreen
import com.abdeveloper.abdownloader.ui.screens.DownloadsScreen
import com.abdeveloper.abdownloader.ui.screens.HistoryScreen
import com.abdeveloper.abdownloader.ui.screens.HomeScreen
import com.abdeveloper.abdownloader.ui.screens.PrivacyPolicyScreen
import com.abdeveloper.abdownloader.ui.screens.SettingsScreen
import com.abdeveloper.abdownloader.ui.screens.TermsOfUseScreen
import com.abdeveloper.abdownloader.ui.screens.UncompletedScreen
import com.abdeveloper.abdownloader.ui.theme.ABDownloaderTheme
import com.abdeveloper.abdownloader.ui.theme.AccentBlue
import com.abdeveloper.abdownloader.ui.viewmodels.MainViewModel
import com.abdeveloper.abdownloader.ui.viewmodels.SubScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isDark = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            ABDownloaderTheme(darkTheme = isDark) {
                MainAppScreen(
                    viewModel = viewModel,
                    activity = this@MainActivity
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.onUrlInputChanged(sharedText)
                viewModel.startExtraction(sharedText)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: MainViewModel,
    activity: ComponentActivity
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val currentSubScreen by viewModel.currentSubScreen.collectAsState()
    val uncompletedCount by viewModel.uncompletedCount.collectAsState()

    val showQualitySheet by viewModel.showQualitySheet.collectAsState()
    val showPlaylistSelector by viewModel.showPlaylistSelector.collectAsState()
    val extractedMedia by viewModel.extractedMedia.collectAsState()
    val selectedQualityOption by viewModel.selectedQualityOption.collectAsState()
    val selectedPhotoIds by viewModel.selectedPhotoIds.collectAsState()
    val selectedPlaylistVideos by viewModel.selectedPlaylistVideoIds.collectAsState()

    val detailsTask by viewModel.detailsTask.collectAsState()
    val showDeleteConfirmDialog by viewModel.showDeleteConfirmDialog.collectAsState()
    val pendingDeleteIds by viewModel.pendingDeleteIds.collectAsState()

    val qualitySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val playlistSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (currentSubScreen == null) {
                NavigationBar(
                    modifier = Modifier.testTag("main_bottom_nav"),
                    tonalElevation = 6.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        icon = {
                            Icon(Icons.Default.Home, contentDescription = "Home")
                        },
                        label = { Text("Home", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentBlue,
                            selectedTextColor = AccentBlue,
                            indicatorColor = AccentBlue.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.testTag("nav_tab_home")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (uncompletedCount > 0) {
                                        Badge(containerColor = AccentBlue) {
                                            Text(text = "$uncompletedCount", color = Color.White)
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Downloads")
                            }
                        },
                        label = { Text("Downloads", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentBlue,
                            selectedTextColor = AccentBlue,
                            indicatorColor = AccentBlue.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.testTag("nav_tab_downloads")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { viewModel.selectTab(2) },
                        icon = {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        },
                        label = { Text("Settings", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentBlue,
                            selectedTextColor = AccentBlue,
                            indicatorColor = AccentBlue.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.testTag("nav_tab_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Handle Sub-Screens navigation
            when (currentSubScreen) {
                SubScreen.UNCOMPLETED_LIST -> {
                    UncompletedScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateToSubScreen(null) }
                    )
                }
                SubScreen.HISTORY -> {
                    HistoryScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateToSubScreen(null) }
                    )
                }
                SubScreen.APP_INFO -> {
                    AppInfoScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateToSubScreen(null) }
                    )
                }
                SubScreen.PRIVACY_POLICY -> {
                    PrivacyPolicyScreen(
                        onBack = { viewModel.navigateToSubScreen(null) }
                    )
                }
                SubScreen.TERMS_OF_USE -> {
                    TermsOfUseScreen(
                        onBack = { viewModel.navigateToSubScreen(null) }
                    )
                }
                null -> {
                    // Top-Level Tabs
                    when (selectedTab) {
                        0 -> HomeScreen(viewModel = viewModel)
                        1 -> DownloadsScreen(viewModel = viewModel)
                        2 -> SettingsScreen(viewModel = viewModel)
                    }
                }
            }

            // Quality Picker Bottom Sheet
            if (showQualitySheet && extractedMedia != null) {
                QualityPickerBottomSheet(
                    media = extractedMedia!!,
                    selectedOption = selectedQualityOption,
                    selectedPhotoIds = selectedPhotoIds,
                    playlistItemCount = if (extractedMedia!!.isPlaylist) selectedPlaylistVideos.size else null,
                    onOptionSelected = { viewModel.selectQualityOption(it) },
                    onPhotoToggled = { viewModel.togglePhotoSelection(it) },
                    onConfirmDownload = { viewModel.confirmDownload(activity) },
                    onDismiss = { viewModel.dismissQualitySheet() },
                    sheetState = qualitySheetState
                )
            }

            // Playlist Selection Sheet
            if (showPlaylistSelector && extractedMedia != null) {
                PlaylistSelectorBottomSheet(
                    media = extractedMedia!!,
                    selectedVideoIds = selectedPlaylistVideos,
                    onToggleVideo = { viewModel.togglePlaylistVideo(it) },
                    onToggleSelectAll = { viewModel.toggleSelectAllPlaylist() },
                    onProceedToQuality = { viewModel.openPlaylistQualitySheet() },
                    onDismiss = { viewModel.dismissPlaylistSelector() },
                    sheetState = playlistSheetState
                )
            }

            // Media Task Details Dialog
            if (detailsTask != null) {
                TaskDetailsDialog(
                    task = detailsTask!!,
                    onDismiss = { viewModel.dismissDetails() }
                )
            }

            // Delete Confirmation Dialog
            if (showDeleteConfirmDialog) {
                DeleteConfirmDialog(
                    count = pendingDeleteIds.size,
                    onConfirm = { viewModel.confirmDeleteTasks() },
                    onDismiss = { viewModel.dismissDeleteDialog() }
                )
            }
        }
    }
}
