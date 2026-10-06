package com.securebank.mobile.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.securebank.mobile.AppContainer
import com.securebank.mobile.ui.accounts.AccountDetailScreen
import com.securebank.mobile.ui.accounts.AccountDetailViewModel
import com.securebank.mobile.ui.accounts.AccountsScreen
import com.securebank.mobile.ui.accounts.AccountsViewModel
import com.securebank.mobile.ui.accounts.HomeScreen
import com.securebank.mobile.ui.accounts.HomeViewModel
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Início", Icons.Filled.Home),
    Tab("accounts", "Contas", Icons.Filled.AccountBox),
)

/** Área logada: barra inferior nas telas de topo; o detalhe da conta abre por cima, com "voltar". */
@Composable
fun MainShell(container: AppContainer) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            if (route == "home" || route == "accounts") {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(container.banking) } })
                HomeScreen(
                    vm,
                    onOpenAccount = { nav.navigate("accounts/$it") },
                    onOpenAccounts = { nav.navigate("accounts") { launchSingleTop = true } },
                    onLogout = { scope.launch { container.auth.logout() } },
                )
            }
            composable("accounts") {
                val vm: AccountsViewModel = viewModel(factory = viewModelFactory { initializer { AccountsViewModel(container.banking) } })
                AccountsScreen(vm, onOpenAccount = { nav.navigate("accounts/$it") })
            }
            composable("accounts/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val vm: AccountDetailViewModel = viewModel(factory = viewModelFactory { initializer { AccountDetailViewModel(container.banking, id) } })
                AccountDetailScreen(vm, onBack = { nav.popBackStack() })
            }
        }
    }
}
