package com.example.hivechat.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.hivechat.ui.screens.*
import com.example.hivechat.viewmodel.ChatViewModel

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Setup : Screen("setup")
    object DeviceList : Screen("device_list")
    object Chat : Screen("chat")
}

@Composable
fun HiveChatNavigation(
    viewModel: ChatViewModel = viewModel()
) {
    val navController = rememberNavController()
    val userName by viewModel.userName.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val allMessages by viewModel.allMessages.collectAsState()

    // Check if user has a saved name
    LaunchedEffect(Unit) {
        val savedName = viewModel.getSavedUserName()
        if (savedName != null) {
            viewModel.setUserName(savedName)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        // Splash Screen
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToSetup = {
                    if (userName.isNotEmpty()) {
                        navController.navigate(Screen.DeviceList.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.Setup.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        // Setup Screen
        composable(Screen.Setup.route) {
            SetupScreen(
                onNameSet = { name ->
                    viewModel.setUserName(name)
                    navController.navigate(Screen.DeviceList.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }

        // Device List Screen
        composable(Screen.DeviceList.route) {
            DeviceListScreen(
                myName = userName,
                devices = devices,
                isDiscovering = isDiscovering,
                onDeviceClick = { device ->
                    viewModel.selectDevice(device)
                    navController.navigate(Screen.Chat.route)
                },
                onDiscoverClick = {
                    if (isDiscovering) {
                        viewModel.stopDiscovery()
                    } else {
                        viewModel.startDiscovery()
                    }
                },
                onLogout = {
                    viewModel.clearUserName()
                    viewModel.stopDiscovery()
                    navController.navigate(Screen.Setup.route) {
                        popUpTo(Screen.DeviceList.route) { inclusive = true }
                    }
                }
            )
        }

        // Chat Screen
        composable(Screen.Chat.route) {
            selectedDevice?.let { device ->
                val messages = allMessages[device.id] ?: emptyList()

                ChatScreen(
                    device = device,
                    messages = messages,
                    onSendMessage = { text ->
                        viewModel.sendMessage(text)
                    },
                    onBackClick = {
                        viewModel.clearSelectedDevice()
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}