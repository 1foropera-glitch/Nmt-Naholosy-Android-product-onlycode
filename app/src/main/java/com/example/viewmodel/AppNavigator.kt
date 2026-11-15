package com.example.viewmodel

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppNavigator(private val savedState: SavedStateHandle) {
    private var stack = savedState.get<ArrayList<String>>("routes") ?: arrayListOf("dashboard")
    private val mutableScreen = MutableStateFlow(decode(stack.lastOrNull() ?: "dashboard"))
    val screen = mutableScreen.asStateFlow()

    fun navigate(screen: Screen) {
        val route = encode(screen)
        if (screen in listOf(Screen.Dashboard, Screen.Dictionary, Screen.ProgressStats, Screen.Settings)) {
            stack = arrayListOf(route)
            publish()
            return
        }
        if (stack.lastOrNull() != route) stack.add(route)
        if (stack.size > 30) stack.removeAt(0)
        publish()
    }
    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else stack = arrayListOf("dashboard")
        publish()
    }
    fun home() { stack = arrayListOf("dashboard"); publish() }
    private fun publish() {
        savedState["routes"] = ArrayList(stack)
        mutableScreen.value = decode(stack.last())
    }

    private fun encode(screen: Screen): String = when (screen) {
        Screen.Onboarding -> "onboarding"
        Screen.Dashboard -> "dashboard"
        Screen.Dictionary -> "dictionary"
        Screen.PracticeSetup -> "practice-setup"
        is Screen.PracticeSession -> "practice:${screen.amount}"
        Screen.ReviewSession -> "review"
        Screen.ProgressStats -> "progress"
        Screen.DifficultWords -> "difficult"
        Screen.DifficultSession -> "difficult-session"
        Screen.Settings -> "settings"
    }
    private fun decode(route: String): Screen = when {
        route.startsWith("practice:") -> Screen.PracticeSession(route.substringAfter(':').toIntOrNull() ?: 12)
        else -> when (route) {
            "onboarding" -> Screen.Onboarding
            "dictionary" -> Screen.Dictionary
            "practice-setup" -> Screen.PracticeSetup
            "review" -> Screen.ReviewSession
            "progress" -> Screen.ProgressStats
            "difficult" -> Screen.DifficultWords
            "difficult-session" -> Screen.DifficultSession
            "settings" -> Screen.Settings
            else -> Screen.Dashboard
        }
    }
}
