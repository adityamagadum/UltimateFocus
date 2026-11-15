package com.example.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Home")
    object Blocks : Screen("blocks", "Blocks")
    object Insights : Screen("insights", "Insights")
    object Settings : Screen("settings", "Settings")
    object Session : Screen("session", "Focus session")
    object PauseEditor : Screen("pause_editor", "Pause screen")
    object SessionEditor : Screen("session_editor/{profileId}", "Session") {
        const val NEW = "new"
        fun route(profileId: String?): String = "session_editor/" + (profileId ?: NEW)
    }
}
