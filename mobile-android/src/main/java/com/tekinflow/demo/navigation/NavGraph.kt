/*
 * NavGraph.kt
 * Uygulama navigasyon grafiği — rol tabanlı başlangıç hedefi ve
 * fade+slide geçiş animasyonları ile tüm ekran rotaları.
 */
package com.tekinflow.demo.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tekinflow.demo.domain.model.UserRole
import com.tekinflow.demo.domain.model.KullaniciOturumu
import com.tekinflow.demo.presentation.auth.AdminScreen
import com.tekinflow.demo.presentation.auth.LoginScreen
import com.tekinflow.demo.presentation.driver.DriverDetailScreen
import com.tekinflow.demo.presentation.driver.DriverScreen
import com.tekinflow.demo.presentation.logistics.LogisticsDetailScreen
import com.tekinflow.demo.presentation.logistics.LogisticsScreen
import com.tekinflow.demo.presentation.purchasing.PurchasingDetailScreen
import com.tekinflow.demo.presentation.purchasing.PurchasingScreen
import com.tekinflow.demo.util.Constants

/** Animasyon süresi (ms) */
private const val ANIM_DURATION = 300

/** Kayma oranı — ekranın 1/5'i kadar kayar */
private const val SLIDE_FRACTION = 5

@Composable
fun AppNavGraph(
    userSession: KullaniciOturumu,
    navController: NavHostController = rememberNavController()
) {
    val sessionManager = LocalSessionManager.current
    val currentUser = userSession.getUser()
    val startDestination = when (currentUser?.role) {
        UserRole.PURCHASING,
        UserRole.PURCHASING_ADMIN -> Constants.Routes.PURCHASING
        UserRole.LOGISTICS_OFFICE,
        UserRole.LOGISTICS_ADMIN -> Constants.Routes.LOGISTICS
        UserRole.DRIVER -> Constants.Routes.DRIVER
        UserRole.ADMIN -> if (currentUser.isSuperAdmin) Constants.Routes.ADMIN else Constants.Routes.LOGIN
        null -> Constants.Routes.LOGIN
    }

    LaunchedEffect(sessionManager) {
        sessionManager.sessionExpiredEvents.collect {
            navController.navigate(Constants.Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ── Giriş Ekranı ──
        composable(
            route = Constants.Routes.LOGIN,
            enterTransition = {
                fadeIn(tween(ANIM_DURATION)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            exitTransition = {
                fadeOut(tween(ANIM_DURATION)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            popEnterTransition = {
                fadeIn(tween(ANIM_DURATION)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            popExitTransition = {
                fadeOut(tween(ANIM_DURATION)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            }
        ) {
            LoginScreen(
                onLoginSuccess = { user ->
                    val destination = when (user.role) {
                        UserRole.PURCHASING,
                        UserRole.PURCHASING_ADMIN -> Constants.Routes.PURCHASING
                        UserRole.LOGISTICS_OFFICE,
                        UserRole.LOGISTICS_ADMIN -> Constants.Routes.LOGISTICS
                        UserRole.DRIVER -> Constants.Routes.DRIVER
                        UserRole.ADMIN -> if (user.isSuperAdmin) Constants.Routes.ADMIN else Constants.Routes.LOGIN
                    }
                    navController.navigate(destination) {
                        popUpTo(Constants.Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        // ── Admin Ekranı ──
        composable(
            route = Constants.Routes.ADMIN,
            enterTransition = {
                fadeIn(tween(ANIM_DURATION)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            exitTransition = {
                fadeOut(tween(ANIM_DURATION)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            popEnterTransition = {
                fadeIn(tween(ANIM_DURATION)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            popExitTransition = {
                fadeOut(tween(ANIM_DURATION)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            }
        ) {
            AdminScreen(
                onLogout = {
                    sessionManager.logout()
                    navController.navigate(Constants.Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Satınalma Liste Ekranı ──
        composable(
            route = Constants.Routes.PURCHASING,
            enterTransition = {
                fadeIn(tween(ANIM_DURATION)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            exitTransition = {
                fadeOut(tween(ANIM_DURATION)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            popEnterTransition = {
                fadeIn(tween(ANIM_DURATION)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            popExitTransition = {
                fadeOut(tween(ANIM_DURATION)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            }
        ) {
            PurchasingScreen(
                onRequestClick = { requestId ->
                    navController.navigate(Constants.Routes.purchasingDetail(requestId))
                },
                onCreateNew = {
                    navController.navigate(Constants.Routes.purchasingDetail(Constants.NEW_REQUEST_ID))
                },
                onLogout = {
                    sessionManager.logout()
                    navController.navigate(Constants.Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Satınalma Detay Ekranı ──
        composable(
            route = Constants.Routes.PURCHASING_DETAIL,
            arguments = listOf(
                navArgument(Constants.NavArgs.REQUEST_ID) { type = NavType.StringType }
            ),
            enterTransition = {
                fadeIn(tween(ANIM_DURATION)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            exitTransition = {
                fadeOut(tween(ANIM_DURATION)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            popEnterTransition = {
                fadeIn(tween(ANIM_DURATION)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            popExitTransition = {
                fadeOut(tween(ANIM_DURATION)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            }
        ) { backStackEntry ->
            val requestId = backStackEntry.arguments?.getString(Constants.NavArgs.REQUEST_ID) ?: return@composable
            PurchasingDetailScreen(
                requestId = requestId,
                onNavigateBack = { navController.navigateUp() },
                onLogout = {
                    sessionManager.logout()
                    navController.navigate(Constants.Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNotificationRequestClick = { notificationRequestId ->
                    navController.navigate(Constants.Routes.purchasingDetail(notificationRequestId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // ── Sevkiyat Liste Ekranı ──
        composable(
            route = Constants.Routes.LOGISTICS,
            enterTransition = {
                fadeIn(tween(ANIM_DURATION)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            exitTransition = {
                fadeOut(tween(ANIM_DURATION)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            popEnterTransition = {
                fadeIn(tween(ANIM_DURATION)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            popExitTransition = {
                fadeOut(tween(ANIM_DURATION)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            }
        ) {
            LogisticsScreen(
                onRequestClick = { requestId ->
                    navController.navigate(Constants.Routes.logisticsDetail(requestId))
                },
                onLogout = {
                    sessionManager.logout()
                    navController.navigate(Constants.Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Sevkiyat Detay Ekranı ──
        composable(
            route = Constants.Routes.LOGISTICS_DETAIL,
            arguments = listOf(
                navArgument(Constants.NavArgs.REQUEST_ID) { type = NavType.StringType }
            ),
            enterTransition = {
                fadeIn(tween(ANIM_DURATION)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            exitTransition = {
                fadeOut(tween(ANIM_DURATION)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            popEnterTransition = {
                fadeIn(tween(ANIM_DURATION)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            popExitTransition = {
                fadeOut(tween(ANIM_DURATION)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            }
        ) { backStackEntry ->
            val requestId = backStackEntry.arguments?.getString(Constants.NavArgs.REQUEST_ID) ?: return@composable
            LogisticsDetailScreen(
                requestId = requestId,
                onNavigateBack = { navController.navigateUp() },
                onLogout = {
                    sessionManager.logout()
                    navController.navigate(Constants.Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNotificationRequestClick = { notificationRequestId ->
                    navController.navigate(Constants.Routes.logisticsDetail(notificationRequestId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // ── Şoför Liste Ekranı ──
        composable(
            route = Constants.Routes.DRIVER,
            enterTransition = {
                fadeIn(tween(ANIM_DURATION)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            exitTransition = {
                fadeOut(tween(ANIM_DURATION)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            popEnterTransition = {
                fadeIn(tween(ANIM_DURATION)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            popExitTransition = {
                fadeOut(tween(ANIM_DURATION)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            }
        ) {
            DriverScreen(
                onRequestClick = { requestId ->
                    navController.navigate(Constants.Routes.driverDetail(requestId))
                },
                onLogout = {
                    sessionManager.logout()
                    navController.navigate(Constants.Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Şoför Detay Ekranı ──
        composable(
            route = Constants.Routes.DRIVER_DETAIL,
            arguments = listOf(
                navArgument(Constants.NavArgs.REQUEST_ID) { type = NavType.StringType }
            ),
            enterTransition = {
                fadeIn(tween(ANIM_DURATION)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            exitTransition = {
                fadeOut(tween(ANIM_DURATION)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            popEnterTransition = {
                fadeIn(tween(ANIM_DURATION)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            },
            popExitTransition = {
                fadeOut(tween(ANIM_DURATION)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(ANIM_DURATION)
                ) { it / SLIDE_FRACTION }
            }
        ) { backStackEntry ->
            val requestId = backStackEntry.arguments?.getString(Constants.NavArgs.REQUEST_ID) ?: return@composable
            DriverDetailScreen(
                requestId = requestId,
                onNavigateBack = { navController.navigateUp() },
                onLogout = {
                    sessionManager.logout()
                    navController.navigate(Constants.Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNotificationRequestClick = { notificationRequestId ->
                    navController.navigate(Constants.Routes.driverDetail(notificationRequestId)) {
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
