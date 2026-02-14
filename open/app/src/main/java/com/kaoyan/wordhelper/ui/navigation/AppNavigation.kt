package com.kaoyan.wordhelper.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kaoyan.wordhelper.ui.screen.BookBuildGuideScreen
import com.kaoyan.wordhelper.ui.screen.BookManageScreen
import com.kaoyan.wordhelper.ui.screen.LearningMode
import com.kaoyan.wordhelper.ui.screen.LearningScreen
import com.kaoyan.wordhelper.ui.screen.ProfileScreen
import com.kaoyan.wordhelper.ui.screen.SearchScreen
import com.kaoyan.wordhelper.ui.screen.StatsScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Learning : Screen("learning", "学习", Icons.Filled.MenuBook)
    data object Search : Screen("search", "查词", Icons.Filled.Search)
    data object BookManage : Screen("book_manage", "词库", Icons.Outlined.LibraryBooks)
    data object Profile : Screen("profile", "我的", Icons.Filled.Person)
    data object Stats : Screen("stats", "学习数据", Icons.Filled.MenuBook)
    data object BookBuildGuide : Screen("book_build_guide", "词书构建教程", Icons.Outlined.LibraryBooks)
}

private val bottomNavItems = listOf(Screen.Learning, Screen.Search, Screen.BookManage, Screen.Profile)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var learningMode by rememberSaveable { mutableStateOf(LearningMode.RECOGNITION) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        modifier = Modifier.testTag(
                            when (screen) {
                                Screen.Learning -> "tab_learning"
                                Screen.Search -> "tab_search"
                                Screen.BookManage -> "tab_book_manage"
                                Screen.Profile -> "tab_profile"
                                Screen.Stats -> "tab_stats"
                                Screen.BookBuildGuide -> "tab_book_build_guide"
                            }
                        ),
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Learning.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Learning.route) {
                LearningScreen(
                    initialMode = learningMode,
                    onModeChange = { learningMode = it }
                )
            }
            composable(Screen.Search.route) { SearchScreen() }
            composable(Screen.BookManage.route) {
                BookManageScreen(
                    onStartSpelling = {
                        learningMode = LearningMode.SPELLING
                        navController.navigate(Screen.Learning.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenBuildGuide = { navController.navigate(Screen.BookBuildGuide.route) }
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(onOpenStats = { navController.navigate(Screen.Stats.route) })
            }
            composable(Screen.Stats.route) {
                StatsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.BookBuildGuide.route) {
                BookBuildGuideScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
