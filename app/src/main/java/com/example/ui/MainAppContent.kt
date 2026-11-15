package com.example.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.layout.AdaptiveContentWidth
import com.example.ui.layout.AdaptiveScreenContainer
import com.example.ui.layout.rememberAppWindowWidthSizeClass
import com.example.ui.layout.usesNavigationRail
import com.example.ui.navigation.BottomNavigationBar
import com.example.ui.navigation.SideNavigationRail
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DictionaryScreen
import com.example.ui.screens.DifficultWordsScreen
import com.example.ui.screens.LearningSessionScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PracticeSetupScreen
import com.example.ui.screens.ProgressStatsScreen
import com.example.ui.screens.SettingsScreen
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.Screen

@Composable
fun MainAppContent(viewModel: AppViewModel, activity: ComponentActivity) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val startup by viewModel.startup.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val reduceMotion by viewModel.reduceMotion.collectAsStateWithLifecycle()
    val hasSeenOnboarding by viewModel.hasSeenOnboarding.collectAsStateWithLifecycle()

    val widthSizeClass = rememberAppWindowWidthSizeClass()
    val isWideScreen = usesNavigationRail(widthSizeClass) && androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp >= 480
    val finishOnboarding: () -> Unit = {
        viewModel.setOnboardingSeen()
        viewModel.navigateTo(Screen.PracticeSetup)
        (activity as? com.example.MainActivity)
            ?.requestReviewReminderPermissionAfterOnboarding()
    }

    // Handle Android system back presses gracefully
    BackHandler(
        enabled = currentScreen != Screen.Dashboard &&
            currentScreen != Screen.Onboarding &&
            currentScreen !is Screen.PracticeSession &&
            currentScreen != Screen.ReviewSession &&
            currentScreen != Screen.DifficultSession
    ) {
        when (currentScreen) {
            Screen.PracticeSetup -> viewModel.navigateTo(Screen.Dashboard)
            Screen.DifficultWords -> viewModel.back()
            else -> viewModel.back()
        }
    }

    if (startup != com.example.StartupState.Ready) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (startup is com.example.StartupState.Error) {
                    Text((startup as com.example.StartupState.Error).message)
                    androidx.compose.material3.Button(onClick = viewModel::retryStartup) { Text("Спробувати ще раз") }
                } else CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Завантаження словника...",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    } else if (currentScreen == Screen.Onboarding ||
        (!hasSeenOnboarding && currentScreen == Screen.Dashboard)) {
        // Both first launch and replay draw behind system bars and apply insets once.
        OnboardingScreen(
            onFinish = finishOnboarding,
            widthSizeClass = widthSizeClass
        )
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            // Adaptive Navigation (Side Rail on Tablets/Wide Screens)
            if (isWideScreen && shouldShowNavigation(currentScreen)) {
                SideNavigationRail(
                    currentScreen = currentScreen,
                    onNavigate = { viewModel.navigateTo(it) }
                )
            }

            Scaffold(
                snackbarHost = {
                    message?.let { text ->
                        androidx.compose.material3.Snackbar(action = {
                            androidx.compose.material3.TextButton(onClick = viewModel::dismissMessage) { Text("Закрити") }
                        }) { Text(text) }
                    }
                },
                modifier = Modifier.weight(1f),
                bottomBar = {
                    // Adaptive Navigation (Bottom Bar on standard Mobile phones)
                    if (!isWideScreen && shouldShowNavigation(currentScreen)) {
                        BottomNavigationBar(
                            currentScreen = currentScreen,
                            onNavigate = { viewModel.navigateTo(it) }
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    val screen = currentScreen
                    AdaptiveScreenContainer(
                        widthSizeClass = widthSizeClass,
                        maxContentWidth = maxContentWidthFor(screen),
                        compactHorizontalPadding = compactHorizontalPaddingFor(screen)
                    ) {
                        when (screen) {
                            Screen.Onboarding -> Unit
                            Screen.Dashboard -> DashboardScreen(
                                viewModel = viewModel.dashboard,
                                widthSizeClass = widthSizeClass,
                                canPostNotifications = (activity as? com.example.MainActivity)?.canPostReviewReminders == true,
                                onOpenNotificationSettings = {
                                    (activity as? com.example.MainActivity)?.openReviewReminderSettings()
                                },
                                onEnableNotifications = {
                                    (activity as? com.example.MainActivity)?.setReviewRemindersEnabled(true)
                                },
                                onDismissNotifications = {
                                    (activity as? com.example.MainActivity)?.dismissReviewReminderPrimer()
                                },
                                onResumeSession = {
                                    (activity as? com.example.MainActivity)?.resumeLearningSession()
                                        ?: viewModel.resumeSession()
                                },
                                onStartReview = {
                                    (activity as? com.example.MainActivity)?.startReviewSession()
                                        ?: viewModel.startReview()
                                }
                            )
                            Screen.Dictionary -> DictionaryScreen(viewModel.dictionary, widthSizeClass)
                            Screen.PracticeSetup -> PracticeSetupScreen(viewModel.session)
                            is Screen.PracticeSession -> LearningSessionScreen(
                                viewModel = viewModel.session,
                                isPractice = true,
                                activity = activity,
                                onFinishSession = {
                                    (activity as? com.example.MainActivity)?.finishLearningSession()
                                        ?: viewModel.finishSession()
                                }
                            )
                            Screen.ReviewSession -> LearningSessionScreen(
                                viewModel = viewModel.session,
                                isPractice = false,
                                activity = activity,
                                onFinishSession = {
                                    (activity as? com.example.MainActivity)?.finishLearningSession()
                                        ?: viewModel.finishSession()
                                }
                            )
                            Screen.DifficultSession -> LearningSessionScreen(
                                viewModel = viewModel.session,
                                isPractice = false,
                                isDifficult = true,
                                activity = activity,
                                onFinishSession = {
                                    (activity as? com.example.MainActivity)?.finishLearningSession()
                                        ?: viewModel.finishSession()
                                }
                            )
                            Screen.ProgressStats -> ProgressStatsScreen(viewModel.dashboard, widthSizeClass)
                            Screen.DifficultWords -> DifficultWordsScreen(
                                viewModel = viewModel.dashboard,
                                widthSizeClass = widthSizeClass,
                                onBack = viewModel::closeDifficultWords
                            )
                            Screen.Settings -> SettingsScreen(viewModel.settings, activity)
                        }
                    }
                }
            }
        }
    }
}

private fun maxContentWidthFor(screen: Screen) = when (screen) {
    Screen.Dashboard,
    Screen.Dictionary,
    Screen.ProgressStats,
    Screen.DifficultWords -> AdaptiveContentWidth.Wide

    Screen.Settings -> AdaptiveContentWidth.Form

    Screen.Onboarding,
    Screen.PracticeSetup,
    is Screen.PracticeSession,
    Screen.ReviewSession,
    Screen.DifficultSession -> AdaptiveContentWidth.Focused
}

private fun compactHorizontalPaddingFor(screen: Screen) = when (screen) {
    Screen.PracticeSetup -> 24.dp

    else -> 16.dp
}

private fun shouldShowNavigation(screen: Screen): Boolean {
    // Hide default navigation tabs when the user is in an active practice/review session or onboarding
    return screen !is Screen.PracticeSession
        && screen != Screen.ReviewSession
        && screen != Screen.DifficultSession
        && screen != Screen.Onboarding
}
