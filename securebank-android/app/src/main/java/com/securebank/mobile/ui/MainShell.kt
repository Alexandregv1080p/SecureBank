package com.securebank.mobile.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
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
import com.securebank.mobile.ui.money.MoneyKind
import com.securebank.mobile.ui.money.MoneyScreen
import com.securebank.mobile.ui.money.MoneyViewModel
import com.securebank.mobile.ui.money.PaymentScreen
import com.securebank.mobile.ui.money.PaymentViewModel
import com.securebank.mobile.ui.money.TransferScreen
import com.securebank.mobile.ui.money.TransferViewModel
import com.securebank.mobile.ui.more.MoreScreen
import com.securebank.mobile.ui.piggy.NewPiggyScreen
import com.securebank.mobile.ui.invest.ApplyInvestmentScreen
import com.securebank.mobile.ui.invest.ApplyInvestmentViewModel
import com.securebank.mobile.ui.invest.InvestmentDetailScreen
import com.securebank.mobile.ui.invest.InvestmentDetailViewModel
import com.securebank.mobile.ui.invest.InvestmentsScreen
import com.securebank.mobile.ui.invest.InvestmentsViewModel
import com.securebank.mobile.ui.pix.PixChargesScreen
import com.securebank.mobile.ui.pix.PixChargesViewModel
import com.securebank.mobile.ui.pix.PixHistoryScreen
import com.securebank.mobile.ui.pix.PixRefundScreen
import com.securebank.mobile.ui.pix.PixRefundViewModel
import com.securebank.mobile.ui.pix.PixSchedulesScreen
import com.securebank.mobile.ui.pix.PixSchedulesViewModel
import com.securebank.mobile.ui.pix.PixHistoryViewModel
import com.securebank.mobile.ui.pix.PixHubScreen
import com.securebank.mobile.ui.pix.PixKeysScreen
import com.securebank.mobile.ui.pix.PixKeysViewModel
import com.securebank.mobile.ui.pix.PixReceiveScreen
import com.securebank.mobile.ui.pix.PixReceiveViewModel
import com.securebank.mobile.ui.pix.PixSendScreen
import com.securebank.mobile.ui.pix.PixSendViewModel
import com.securebank.mobile.ui.piggy.NewPiggyViewModel
import com.securebank.mobile.ui.piggy.PiggiesScreen
import com.securebank.mobile.ui.piggy.PiggiesViewModel
import com.securebank.mobile.ui.piggy.PiggyDetailScreen
import com.securebank.mobile.ui.piggy.PiggyDetailViewModel
import com.securebank.mobile.ui.more.NotificationsScreen
import com.securebank.mobile.ui.more.NotificationsViewModel
import com.securebank.mobile.ui.more.SecurityScreen
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Início", Icons.Filled.Home),
    Tab("accounts", "Contas", Icons.Filled.AccountBox),
    Tab("pix", "Pix", Icons.Filled.Send),
    Tab("pay", "Pagar", Icons.Filled.CheckCircle),
    Tab("more", "Mais", Icons.Filled.Menu),
)

/** Área logada: barra inferior nas telas de topo; o detalhe da conta abre por cima, com "voltar". */
@Composable
fun MainShell(container: AppContainer) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            if (route in tabs.map { it.route }) {
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
                val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(container.banking, container.piggies) } })
                HomeScreen(
                    vm,
                    onOpenAccount = { nav.navigate("accounts/$it") },
                    onOpenAccounts = { nav.navigate("accounts") { launchSingleTop = true } },
                    onOpenPiggy = { nav.navigate("piggies/$it") },
                    onOpenPiggies = { nav.navigate("piggies") },
                    onPix = { nav.navigate("pix") },
                    onTransfer = { nav.navigate("transfer") },
                    onPay = { nav.navigate("pay") },
                )
            }
            composable("accounts") {
                val vm: AccountsViewModel = viewModel(factory = viewModelFactory { initializer { AccountsViewModel(container.banking) } })
                AccountsScreen(vm, onOpenAccount = { nav.navigate("accounts/$it") })
            }
            composable("accounts/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val vm: AccountDetailViewModel = viewModel(factory = viewModelFactory { initializer { AccountDetailViewModel(container.banking, id) } })
                AccountDetailScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onDeposit = { nav.navigate("accounts/$id/money/deposit") },
                    onWithdraw = { nav.navigate("accounts/$id/money/withdraw") },
                )
            }
            composable(
                "accounts/{id}/money/{kind}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("kind") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val kind = if (entry.arguments?.getString("kind") == "withdraw") MoneyKind.WITHDRAW else MoneyKind.DEPOSIT
                val vm: MoneyViewModel = viewModel(factory = viewModelFactory { initializer { MoneyViewModel(container.banking, id, kind) } })
                MoneyScreen(container, vm, onBack = { nav.popBackStack() })
            }
            composable("transfer") {
                val vm: TransferViewModel = viewModel(factory = viewModelFactory { initializer { TransferViewModel(container.banking) } })
                TransferScreen(container, vm, onOpenAccounts = { nav.navigate("accounts") })
            }
            composable("more") {
                MoreScreen(
                    onTransfer = { nav.navigate("transfer") },
                    onPiggies = { nav.navigate("piggies") },
                    onInvestments = { nav.navigate("investments") },
                    onNotifications = { nav.navigate("notifications") },
                    onSecurity = { nav.navigate("security") },
                    onLogout = { scope.launch { container.auth.logout() } },
                )
            }
            composable("pix") {
                val vm: PixHistoryViewModel = viewModel(factory = viewModelFactory { initializer { PixHistoryViewModel(container.pix, container.banking) } })
                PixHubScreen(
                    vm,
                    onSend = { nav.navigate("pix/send") },
                    onReceive = { nav.navigate("pix/receive") },
                    onKeys = { nav.navigate("pix/keys") },
                    onHistory = { nav.navigate("pix/history") },
                    onCharges = { nav.navigate("pix/charges") },
                    onSchedules = { nav.navigate("pix/schedules") },
                )
            }
            composable("pix/send") {
                val vm: PixSendViewModel = viewModel(factory = viewModelFactory { initializer { PixSendViewModel(container.pix, container.banking) } })
                PixSendScreen(
                    container, vm,
                    onHistory = { nav.navigate("pix/history") { popUpTo("pix") } },
                    onOpenAccounts = { nav.navigate("accounts") },
                )
            }
            composable("pix/receive") {
                val vm: PixReceiveViewModel = viewModel(factory = viewModelFactory { initializer { PixReceiveViewModel(container.pix, container.banking) } })
                PixReceiveScreen(vm, onKeys = { nav.navigate("pix/keys") }, onBack = { nav.popBackStack() })
            }
            composable("pix/keys") {
                val vm: PixKeysViewModel = viewModel(factory = viewModelFactory { initializer { PixKeysViewModel(container.pix, container.banking) } })
                PixKeysScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("pix/history") {
                val vm: PixHistoryViewModel = viewModel(factory = viewModelFactory { initializer { PixHistoryViewModel(container.pix, container.banking) } })
                PixHistoryScreen(vm, onRefund = { nav.navigate("pix/refund/$it") }, onBack = { nav.popBackStack() })
            }
            composable("pix/refund/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val vm: PixRefundViewModel = viewModel(factory = viewModelFactory { initializer { PixRefundViewModel(container.pix, id) } })
                PixRefundScreen(container, vm, onBack = { nav.popBackStack() })
            }
            composable("pix/charges") {
                val vm: PixChargesViewModel = viewModel(factory = viewModelFactory { initializer { PixChargesViewModel(container.pix, container.banking) } })
                PixChargesScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("pix/schedules") {
                val vm: PixSchedulesViewModel = viewModel(factory = viewModelFactory { initializer { PixSchedulesViewModel(container.pix, container.banking) } })
                PixSchedulesScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("investments") {
                val vm: InvestmentsViewModel = viewModel(factory = viewModelFactory { initializer { InvestmentsViewModel(container.investments, container.banking) } })
                InvestmentsScreen(vm, onApply = { nav.navigate("investments/apply/$it") }, onOpen = { nav.navigate("investments/$it") }, onBack = { nav.popBackStack() })
            }
            composable("investments/apply/{code}", arguments = listOf(navArgument("code") { type = NavType.StringType })) { entry ->
                val code = entry.arguments?.getString("code").orEmpty()
                val vm: ApplyInvestmentViewModel = viewModel(factory = viewModelFactory { initializer { ApplyInvestmentViewModel(container.investments, container.banking, code) } })
                ApplyInvestmentScreen(
                    container, vm,
                    onOpen = { id -> nav.navigate("investments/$id") { popUpTo("investments") } },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("investments/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val vm: InvestmentDetailViewModel = viewModel(factory = viewModelFactory { initializer { InvestmentDetailViewModel(container.investments, container.banking, id) } })
                InvestmentDetailScreen(container, vm, onBack = { nav.popBackStack() })
            }
            composable("piggies") {
                val vm: PiggiesViewModel = viewModel(factory = viewModelFactory { initializer { PiggiesViewModel(container.piggies, container.banking) } })
                PiggiesScreen(vm, onOpen = { nav.navigate("piggies/$it") }, onNew = { nav.navigate("piggies/new") }, onBack = { nav.popBackStack() })
            }
            composable("piggies/new") {
                val vm: NewPiggyViewModel = viewModel(factory = viewModelFactory { initializer { NewPiggyViewModel(container.banking, container.piggies) } })
                NewPiggyScreen(
                    vm,
                    onCreated = { id -> nav.navigate("piggies/$id") { popUpTo("piggies/new") { inclusive = true } } },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("piggies/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val vm: PiggyDetailViewModel = viewModel(factory = viewModelFactory { initializer { PiggyDetailViewModel(container.piggies, container.banking, id) } })
                PiggyDetailScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("notifications") {
                val vm: NotificationsViewModel = viewModel(factory = viewModelFactory { initializer { NotificationsViewModel(container.banking) } })
                NotificationsScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("security") {
                SecurityScreen(container, onBack = { nav.popBackStack() })
            }
            composable("pay") {
                val vm: PaymentViewModel = viewModel(factory = viewModelFactory { initializer { PaymentViewModel(container.banking) } })
                PaymentScreen(container, vm, onOpenAccounts = { nav.navigate("accounts") })
            }
        }
    }
}
