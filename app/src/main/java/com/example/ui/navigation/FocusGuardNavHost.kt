package com.example.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.FocusGuardBottomNav
import com.example.ui.screens.blocks.BlocksScreen
import com.example.ui.screens.editor.PauseScreenEditor
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.insights.InsightsScreen
import com.example.ui.screens.session.FocusSessionScreen
import com.example.ui.screens.sessions.SessionEditorScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.theme.FocusSurface
import com.example.viewmodel.FocusGuardViewModel

/** The four screens on the bottom bar. Everything else is pushed on top of one of them. */
private val TabRoutes = setOf(
    Screen.Home.route,
    Screen.Blocks.route,
    Screen.Insights.route,
    Screen.Settings.route
)

private const val TAB_FADE_IN_MS = 180
private const val TAB_FADE_OUT_MS = 90
private const val PUSH_MS = 280

@Composable
fun FocusGuardApp(
    viewModel: FocusGuardViewModel = viewModel(factory = FocusGuardViewModel.Factory),
    /** Sends UltimateFocus to the background so another app can be brought forward. */
    onMinimise: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route
    val navigator = remember(navController) { TabNavigator(navController) }

    Scaffold(
        containerColor = FocusSurface,
        bottomBar = {
            AnimatedVisibility(
                visible = currentRoute in TabRoutes,
                enter = slideInVertically(tween(PUSH_MS, easing = FastOutSlowInEasing)) { it } +
                    fadeIn(tween(PUSH_MS)),
                exit = slideOutVertically(tween(PUSH_MS, easing = FastOutSlowInEasing)) { it } +
                    fadeOut(tween(PUSH_MS / 2))
            ) {
                FocusGuardBottomNav(
                    currentRoute = currentRoute,
                    onNavigate = { screen -> navigator.openTab(screen.route) }
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Screens add their own status-bar spacing; the bar inset already
                // applied here must not be applied a second time.
                .consumeWindowInsets(innerPadding),
            // The library default is a slow 700 ms cross-fade, which is what made moving
            // between screens feel laggy. Tabs now swap with a quick fade, and screens
            // opened on top (session, pause editor) slide in and back out.
            enterTransition = { if (isTabSwitch()) tabEnter() else pushEnter() },
            exitTransition = { if (isTabSwitch()) tabExit() else pushExit() },
            popEnterTransition = { if (isTabSwitch()) tabEnter() else popEnter() },
            popExitTransition = { if (isTabSwitch()) tabExit() else popExit() }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenSession = dropUnlessResumed { navigator.push(Screen.Session.route) },
                    // Blocks is a tab, so it is opened as one. Pushing it on top of Home
                    // used to leave a hidden copy of Blocks inside Home: tapping Home on
                    // the bottom bar then brought back Blocks instead of Home.
                    onOpenBlocks = { navigator.openTab(Screen.Blocks.route) },
                    onEditSessionProfile = { id -> navigator.push(Screen.SessionEditor.route(id)) }
                )
            }

            composable(Screen.Blocks.route) {
                BlocksScreen(
                    viewModel = viewModel,
                    onMinimise = onMinimise
                )
            }

            composable(Screen.Insights.route) {
                InsightsScreen(viewModel = viewModel)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onOpenPauseEditor = dropUnlessResumed { navigator.push(Screen.PauseEditor.route) }
                )
            }

            composable(Screen.Session.route) {
                FocusSessionScreen(
                    viewModel = viewModel,
                    // dropUnlessResumed ignores a second tap while the screen is already
                    // leaving. Two quick taps used to pop Home as well and leave an empty,
                    // stuck screen.
                    onBack = dropUnlessResumed { navigator.back() }
                )
            }

            composable(Screen.SessionEditor.route) { entry ->
                val id = entry.arguments?.getString("profileId")
                    ?.takeIf { it != Screen.SessionEditor.NEW }
                SessionEditorScreen(
                    viewModel = viewModel,
                    profileId = id,
                    onBack = dropUnlessResumed { navigator.back() }
                )
            }

            composable(Screen.PauseEditor.route) {
                PauseScreenEditor(
                    viewModel = viewModel,
                    onBack = dropUnlessResumed { navigator.back() }
                )
            }
        }
    }
}

/**
 * The one place that moves between screens, so every path follows the same rules.
 *
 * Home is always the single root. Opening another tab puts it directly on top of Home,
 * so the back stack is never more than Home → tab → (one pushed screen). Pressing back
 * from any tab returns to Home, and back on Home leaves the app, as in other apps.
 *
 * The earlier version saved and restored each tab's history. With every screen in one
 * flat graph that history was stored under Home, so returning to Home restored whatever
 * screen had been on top of it: in Settings, tapping Home could open Blocks, or nothing
 * would seem to happen at all.
 */
private class TabNavigator(private val navController: NavHostController) {

    fun openTab(route: String) {
        if (currentRoute() == route) return
        if (route == Screen.Home.route) {
            // Home is always at the bottom of the stack, so going there is just a pop.
            if (!navController.popBackStack(Screen.Home.route, inclusive = false)) {
                navController.navigate(Screen.Home.route) { launchSingleTop = true }
            }
            return
        }
        navController.navigate(route) {
            popUpTo(Screen.Home.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    /** Opens a screen on top of the current one (no bottom bar). */
    fun push(route: String) {
        // Also ignores a second tap while the same kind of screen is already open
        // (e.g. "session_editor/{profileId}" vs "session_editor/new").
        val current = currentRoute()
        if (current == route || current?.substringBefore('/') == route.substringBefore('/')) return
        navController.navigate(route) { launchSingleTop = true }
    }

    /** Goes back one screen, and never past Home, so the screen can never end up empty. */
    fun back() {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            openTab(Screen.Home.route)
        }
    }

    private fun currentRoute(): String? = navController.currentBackStackEntry?.destination?.route
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in TabRoutes && targetState.destination.route in TabRoutes

private fun tabEnter(): EnterTransition =
    fadeIn(tween(TAB_FADE_IN_MS, delayMillis = TAB_FADE_OUT_MS / 2, easing = LinearOutSlowInEasing))

private fun tabExit(): ExitTransition =
    fadeOut(tween(TAB_FADE_OUT_MS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushEnter(): EnterTransition =
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Start,
        tween(PUSH_MS, easing = FastOutSlowInEasing)
    ) { it / 4 } + fadeIn(tween(PUSH_MS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushExit(): ExitTransition =
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Start,
        tween(PUSH_MS, easing = FastOutSlowInEasing)
    ) { it / 8 } + fadeOut(tween(PUSH_MS / 2))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.End,
        tween(PUSH_MS, easing = FastOutSlowInEasing)
    ) { it / 8 } + fadeIn(tween(PUSH_MS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.End,
        tween(PUSH_MS, easing = FastOutSlowInEasing)
    ) { it / 4 } + fadeOut(tween(PUSH_MS / 2))
