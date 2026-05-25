package com.pengnini.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pengnini.app.ui.library.LibraryScreen
import com.pengnini.app.ui.lock.LockScreen
import com.pengnini.app.ui.player.PlayerScreen
import com.pengnini.app.ui.settings.SettingsCategoryScreen
import com.pengnini.app.ui.settings.SettingsScreen
import com.pengnini.app.ui.splash.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val LOCK = "lock"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val SETTINGS_CATEGORY = "settings/{cat}"
    const val PLAYER = "player/{videoUri}"
    fun settingsCategory(cat: String) = "settings/$cat"
    fun player(uri: String) = "player/${android.net.Uri.encode(uri)}"
}

private const val SLIDE_DURATION_MS = 280
private const val FADE_DURATION_MS = 220

// 빠른 더블 탭으로 인한 중복 navigate / popBackStack 방지.
// 전환 중에는 currentBackStackEntry의 lifecycle이 STARTED/CREATED 상태라 RESUMED일 때만 통과.
private fun NavController.safeNavigate(
    route: String,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    val state = currentBackStackEntry?.lifecycle?.currentState
    if (state == null || state == Lifecycle.State.RESUMED) {
        navigate(route, builder)
    }
}

private fun NavController.safePop() {
    val state = currentBackStackEntry?.lifecycle?.currentState
    if (state == null || state == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}

@Composable
fun PengniniApp() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        // 일반 화면 간 전환은 slide, Player ↔ 다른 화면은 fade (PlayerView 첫 frame 부담 줄임)
        enterTransition = {
            if (targetState.destination.route == Routes.PLAYER) {
                fadeIn(tween(FADE_DURATION_MS))
            } else {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(SLIDE_DURATION_MS),
                )
            }
        },
        exitTransition = {
            if (targetState.destination.route == Routes.PLAYER) {
                fadeOut(tween(FADE_DURATION_MS))
            } else {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(SLIDE_DURATION_MS),
                )
            }
        },
        popEnterTransition = {
            if (initialState.destination.route == Routes.PLAYER) {
                fadeIn(tween(FADE_DURATION_MS))
            } else {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(SLIDE_DURATION_MS),
                )
            }
        },
        popExitTransition = {
            if (initialState.destination.route == Routes.PLAYER) {
                fadeOut(tween(FADE_DURATION_MS))
            } else {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(SLIDE_DURATION_MS),
                )
            }
        },
    ) {
        composable(
            route = Routes.SPLASH,
            enterTransition = { fadeIn(tween(FADE_DURATION_MS)) },
            exitTransition = { fadeOut(tween(FADE_DURATION_MS)) },
        ) {
            SplashScreen(
                libraryRoute = Routes.LIBRARY,
                lockRoute = Routes.LOCK,
                onNavigate = { route ->
                    navController.safeNavigate(route) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.LOCK,
            enterTransition = { fadeIn(tween(FADE_DURATION_MS)) },
            exitTransition = { fadeOut(tween(FADE_DURATION_MS)) },
        ) {
            LockScreen(
                onUnlocked = {
                    navController.safeNavigate(Routes.LIBRARY) {
                        popUpTo(Routes.LOCK) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenSettings = { navController.safeNavigate(Routes.SETTINGS) },
                onOpenPlayer = { uri -> navController.safeNavigate(Routes.player(uri)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.safePop() },
                onOpenCategory = { cat -> navController.safeNavigate(Routes.settingsCategory(cat)) },
            )
        }
        composable(
            route = Routes.SETTINGS_CATEGORY,
            arguments = listOf(navArgument("cat") { type = NavType.StringType }),
        ) { entry ->
            val cat = entry.arguments?.getString("cat") ?: ""
            SettingsCategoryScreen(
                category = cat,
                onBack = { navController.safePop() },
            )
        }
        composable(
            route = Routes.PLAYER,
            arguments = listOf(navArgument("videoUri") { type = NavType.StringType }),
        ) {
            PlayerScreen(
                onBack = { navController.safePop() },
                onPlayOther = { uri ->
                    navController.safeNavigate(Routes.player(uri)) {
                        popUpTo(Routes.PLAYER) { inclusive = true }
                    }
                },
            )
        }
    }
}
