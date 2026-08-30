package com.adnanfoisal.infinitydesign.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.adnanfoisal.infinitydesign.screens.direction.DirectionScreen
import com.adnanfoisal.infinitydesign.screens.editor.EditorScreen
import com.adnanfoisal.infinitydesign.screens.generation.GenerationScreen
import com.adnanfoisal.infinitydesign.screens.home.HomeScreen
import com.adnanfoisal.infinitydesign.screens.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val GENERATION = "generation"
    const val DIRECTION = "direction/{blueprintId}"
    fun direction(id: String) = "direction/$id"
    const val EDITOR = "editor/{projectId}"
    fun editor(id: String) = "editor/$id"
    const val SETTINGS = "settings"
}

@Composable
fun RootNavigation() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onNewProject = { nav.navigate(Routes.GENERATION) },
                onOpenProject = { id -> nav.navigate(Routes.editor(id)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.GENERATION) {
            GenerationScreen(
                onBlueprintReady = { id -> nav.navigate(Routes.direction(id)) },
                onCancel = { nav.popBackStack() },
            )
        }
        composable(
            Routes.DIRECTION,
            arguments = listOf(navArgument("blueprintId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("blueprintId").orEmpty()
            DirectionScreen(
                blueprintId = id,
                onAccept = { pid -> nav.navigate(Routes.editor(pid)) { popUpTo(Routes.HOME) } },
                onRegenerate = { nav.navigate(Routes.GENERATION) { popUpTo(Routes.HOME) } },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Routes.EDITOR,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("projectId").orEmpty()
            EditorScreen(
                projectId = id,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
