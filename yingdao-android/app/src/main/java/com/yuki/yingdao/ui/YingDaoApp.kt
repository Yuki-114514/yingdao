package com.yuki.yingdao.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yuki.yingdao.data.AppContainer
import com.yuki.yingdao.data.ProjectStatus
import com.yuki.yingdao.ui.screens.CaptureScreen
import com.yuki.yingdao.ui.screens.HomeScreen
import com.yuki.yingdao.ui.screens.NewProjectScreen
import com.yuki.yingdao.ui.screens.OutputScreen
import com.yuki.yingdao.ui.screens.PlanScreen
import com.yuki.yingdao.ui.screens.ReviewScreen

private enum class Screen(val route: String) {
    Home("home"),
    NewProject("new_project"),
    Plan("plan"),
    Capture("capture"),
    Review("review"),
    Output("output"),
}

internal fun destinationRouteFor(status: ProjectStatus): String {
    return when (status) {
        ProjectStatus.ShotPlanReady -> Screen.Plan.route
        ProjectStatus.Shooting -> Screen.Capture.route
        ProjectStatus.ReviewReady -> Screen.Review.route
        ProjectStatus.AssemblyReady -> Screen.Output.route
        ProjectStatus.Draft,
        ProjectStatus.BriefReady,
        -> Screen.Home.route
    }
}

internal fun projectStatusLabel(status: ProjectStatus): String {
    return when (status) {
        ProjectStatus.Draft -> "还没开始"
        ProjectStatus.BriefReady -> "正在准备"
        ProjectStatus.ShotPlanReady -> "可以开拍了"
        ProjectStatus.Shooting -> "拍摄中"
        ProjectStatus.ReviewReady -> "待挑片"
        ProjectStatus.AssemblyReady -> "可以出片了"
    }
}

@Composable
fun YingDaoApp(
    viewModel: YingDaoViewModel = viewModel {
        YingDaoViewModel(aiDirectorService = AppContainer.aiDirectorService())
    },
) {
    val navController = rememberNavController()
    val uiState = viewModel.uiState

    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    innerPadding = innerPadding,
                    uiState = uiState,
                    onCreateProject = {
                        viewModel.resetDraft()
                        navController.navigate(Screen.NewProject.route)
                    },
                    onContinueProject = { projectId ->
                        viewModel.openProject(projectId)
                        val project = uiState.projects.find { it.id == projectId }
                        navController.navigate(destinationRouteFor(project?.status ?: ProjectStatus.Draft))
                    },
                )
            }
            composable(Screen.NewProject.route) {
                NewProjectScreen(
                    innerPadding = innerPadding,
                    uiState = uiState,
                    onUpdateDraft = viewModel::updateDraft,
                    onGeneratePlan = {
                        viewModel.generateDirectorPlan()
                        navController.navigate(Screen.Plan.route)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.Plan.route) {
                PlanScreen(
                    innerPadding = innerPadding,
                    uiState = uiState,
                    onSelectShot = viewModel::selectShot,
                    onBack = { navController.popBackStack() },
                    onStartShooting = { navController.navigate(Screen.Capture.route) },
                )
            }
            composable(Screen.Capture.route) {
                CaptureScreen(
                    innerPadding = PaddingValues(0.dp),
                    uiState = uiState,
                    onSelectShot = viewModel::selectShot,
                    onRecordedClip = viewModel::registerRecordedClipForSelectedShot,
                    onApprove = viewModel::approveSelectedShot,
                    onRetake = viewModel::requestRetakeForSelectedShot,
                    onSkip = viewModel::skipSelectedShot,
                    onOpenReview = { navController.navigate(Screen.Review.route) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.Review.route) {
                ReviewScreen(
                    innerPadding = innerPadding,
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onBuildAssembly = {
                        viewModel.buildAssemblySuggestion()
                        navController.navigate(Screen.Output.route)
                    },
                )
            }
            composable(Screen.Output.route) {
                OutputScreen(
                    innerPadding = innerPadding,
                    uiState = uiState,
                    onBackToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
