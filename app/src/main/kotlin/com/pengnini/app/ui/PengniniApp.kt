package com.pengnini.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pengnini.app.R
import com.pengnini.app.ui.folder.FolderScreen
import com.pengnini.app.ui.folder.FolderVideosScreen
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
    const val FOLDERS = "folders"
    const val FOLDER_VIDEOS = "folders/{bucketKey}"
    const val SETTINGS = "settings"
    const val SETTINGS_CATEGORY = "settings/{cat}"
    const val PLAYER = "player/{videoUri}"
    fun settingsCategory(cat: String) = "settings/$cat"
    fun folderVideos(bucketKey: String) = "folders/${android.net.Uri.encode(bucketKey)}"
    fun player(uri: String) = "player/${android.net.Uri.encode(uri)}"
}

/** 하단 탭이 보이는 목적지. 플레이어·잠금·스플래시는 전체화면이라 제외. */
private val BAR_ROUTES = setOf(
    Routes.LIBRARY,
    Routes.FOLDERS,
    Routes.FOLDER_VIDEOS,
    Routes.SETTINGS,
    Routes.SETTINGS_CATEGORY,
)

/** 탭 사이 이동은 계층 이동이 아니므로 slide 대신 fade. */
private val TAB_ROOTS = setOf(Routes.LIBRARY, Routes.FOLDERS, Routes.SETTINGS)

private const val SLIDE_DURATION_MS = 280
private const val FADE_DURATION_MS = 220

private enum class Tab(val route: String, val icon: ImageVector, val labelRes: Int) {
    HOME(Routes.LIBRARY, Icons.Outlined.Home, R.string.nav_home),
    FOLDERS(Routes.FOLDERS, Icons.Outlined.Folder, R.string.nav_folders),
    SETTINGS(Routes.SETTINGS, Icons.Outlined.Settings, R.string.nav_settings),
}

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

/**
 * 탭 전환. 탭 스택을 쌓지 않고, 각 탭이 보던 위치는 저장·복원한다.
 * 이미 그 탭이면 아무 것도 하지 않는다(같은 화면 재생성 방지).
 */
private fun NavController.navigateTab(route: String) {
    val dest = currentDestination
    if (dest?.isIn(route) == true) {
        // 같은 탭을 다시 누르면 그 탭의 첫 화면으로 되돌아간다.
        if (dest.route != route) popBackStack(route, inclusive = false)
        return
    }
    safeNavigate(route) {
        // 탭은 서로 쌓이지 않게 홈까지 정리하고, 각 탭이 보던 위치는 저장·복원한다.
        popUpTo(Routes.LIBRARY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** 현재 목적지가 해당 탭(또는 그 탭의 하위 화면)인지. */
private fun NavDestination.isIn(tabRoute: String): Boolean = when (tabRoute) {
    Routes.FOLDERS -> route == Routes.FOLDERS || route == Routes.FOLDER_VIDEOS
    Routes.SETTINGS -> route == Routes.SETTINGS || route == Routes.SETTINGS_CATEGORY
    else -> route == tabRoute
}

@Composable
fun PengniniApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val barVisible = currentRoute in BAR_ROUTES

    Column(modifier = Modifier.fillMaxSize()) {
        // 탭바가 화면 아래를 덮고 있는 동안에는 그 위 영역이 시스템 내비게이션 바에 닿지 않는다.
        // 인셋을 여기서 소비해 각 화면 Scaffold가 하단 여백을 한 번 더 잡지 않게 한다.
        Box(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (barVisible) {
                        Modifier.consumeWindowInsets(WindowInsets.navigationBars)
                    } else {
                        Modifier
                    },
                ),
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.SPLASH,
                modifier = Modifier.fillMaxSize(),
                // 일반 화면 간 전환은 slide, Player·탭 이동은 fade
                enterTransition = {
                    if (isFadeTransition(initialState.destination.route, targetState.destination.route)) {
                        fadeIn(tween(FADE_DURATION_MS))
                    } else {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(SLIDE_DURATION_MS),
                        )
                    }
                },
                exitTransition = {
                    if (isFadeTransition(initialState.destination.route, targetState.destination.route)) {
                        fadeOut(tween(FADE_DURATION_MS))
                    } else {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(SLIDE_DURATION_MS),
                        )
                    }
                },
                popEnterTransition = {
                    if (isFadeTransition(initialState.destination.route, targetState.destination.route)) {
                        fadeIn(tween(FADE_DURATION_MS))
                    } else {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(SLIDE_DURATION_MS),
                        )
                    }
                },
                popExitTransition = {
                    if (isFadeTransition(initialState.destination.route, targetState.destination.route)) {
                        fadeOut(tween(FADE_DURATION_MS))
                    } else {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(SLIDE_DURATION_MS),
                        )
                    }
                },
            ) {
                composable(Routes.SPLASH) {
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
                composable(Routes.LOCK) {
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
                        onOpenPlayer = { uri -> navController.safeNavigate(Routes.player(uri)) },
                    )
                }
                composable(Routes.FOLDERS) {
                    FolderScreen(
                        onOpenBucket = { key -> navController.safeNavigate(Routes.folderVideos(key)) },
                    )
                }
                composable(
                    route = Routes.FOLDER_VIDEOS,
                    arguments = listOf(navArgument("bucketKey") { type = NavType.StringType }),
                ) {
                    FolderVideosScreen(
                        onBack = { navController.safePop() },
                        onOpenPlayer = { uri -> navController.safeNavigate(Routes.player(uri)) },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        showBack = false,
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

        if (barVisible) {
            // 기본값은 배경보다 밝은 톤이라 탭바가 떠 보인다. 화면 바닥에 붙은 것으로 읽히게
            // 배경과 같은 색을 주고, 위쪽 실선 하나로만 경계를 만든다.
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                ) {
                    Tab.entries.forEach { tab ->
                        val selected = backStackEntry?.destination?.isIn(tab.route) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Player 진출입과 탭 사이 이동은 slide가 어색해서 fade로 처리한다. */
private fun isFadeTransition(from: String?, to: String?): Boolean =
    to == Routes.PLAYER || from == Routes.PLAYER ||
        (from in TAB_ROOTS && to in TAB_ROOTS)
