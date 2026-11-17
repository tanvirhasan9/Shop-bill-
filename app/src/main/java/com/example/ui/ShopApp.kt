package com.example.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.model.Customer
import com.example.model.ShopProduct
import com.example.model.ShopProfile
import com.example.model.UiState
import com.example.ui.components.CustomerFormDialog
import com.example.ui.components.ProductFormDialog
import com.example.ui.components.ShopSettingsDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BillsScreen
import com.example.ui.screens.CatalogScreen
import com.example.ui.screens.CreateBillScreen
import com.example.ui.screens.CustomerDetailScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ReceiptDetailScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_BILLS = "bills"
const val ROUTE_CUSTOMERS = "customers"
const val ROUTE_CATALOG = "catalog"
const val ROUTE_CREATE_BILL = "create_bill"
const val ROUTE_RECEIPT = "receipt/{billId}"
const val ROUTE_CUSTOMER_DETAIL = "customer/{customerId}"

private data class TopLevelTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

private val TOP_TABS = listOf(
    TopLevelTab(ROUTE_DASHBOARD, "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    TopLevelTab(ROUTE_BILLS, "Bills", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong, "nav_bills"),
    TopLevelTab(ROUTE_CUSTOMERS, "Customers", Icons.Filled.People, Icons.Outlined.People, "nav_customers"),
    TopLevelTab(ROUTE_CATALOG, "Items", Icons.Filled.Inventory2, Icons.Outlined.Inventory2, "nav_catalog")
)

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            val viewModel: ShopViewModel = viewModel(
                key = user.uid,
                factory = ShopViewModel.provideFactory(user.uid)
            )
            ShopMainShell(
                userId = user.uid,
                userEmail = user.email,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun ShopMainShell(
    userId: String,
    userEmail: String?,
    viewModel: ShopViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val profileState by viewModel.shopProfileState.collectAsStateWithLifecycle()
    val customersState by viewModel.customersState.collectAsStateWithLifecycle()
    val productsState by viewModel.productsState.collectAsStateWithLifecycle()
    val billsState by viewModel.billsState.collectAsStateWithLifecycle()
    val selectedBillState by viewModel.selectedBillState.collectAsStateWithLifecycle()
    val selectedBillItemsState by viewModel.selectedBillItemsState.collectAsStateWithLifecycle()
    val billDraft by viewModel.billDraft.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showCustomerDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ShopProduct?>(null) }
    var showProductDialog by remember { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTopLevel = currentRoute in TOP_TABS.map { it.route }

    val profile = (profileState as? UiState.Success)?.data ?: ShopProfile(userId = userId)
    val currency = profile.currencySymbol.ifBlank { "$" }

    val openNewBill: (Customer?) -> Unit = { preselected ->
        viewModel.startNewBill(preselected)
        navController.navigate(ROUTE_CREATE_BILL)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (isTopLevel && !useNavRail) {
                    NavigationBar {
                        TOP_TABS.forEach { tab ->
                            val selected = navBackStackEntry?.destination?.hierarchy?.any {
                                it.route == tab.route
                            } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (isTopLevel && !useNavRail && currentRoute != ROUTE_DASHBOARD) {
                    ExtendedFloatingActionButton(
                        onClick = { openNewBill(null) },
                        icon = { Icon(Icons.Filled.AddShoppingCart, contentDescription = null) },
                        text = { Text("New Bill") },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.testTag("fab_new_bill")
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevel && useNavRail) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .statusBarsPadding(),
                        header = {
                            FloatingActionButton(
                                onClick = { openNewBill(null) },
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .testTag("rail_fab_new_bill")
                            ) {
                                Icon(Icons.Filled.AddShoppingCart, contentDescription = "New Bill")
                            }
                        }
                    ) {
                        Spacer(modifier = Modifier.weight(0.2f))
                        TOP_TABS.forEach { tab ->
                            val selected = navBackStackEntry?.destination?.hierarchy?.any {
                                it.route == tab.route
                            } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_DASHBOARD,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_DASHBOARD) {
                        DashboardScreen(
                            profileState = profileState,
                            billsState = billsState,
                            customersState = customersState,
                            onOpenSettings = { showSettingsDialog = true },
                            onNewBill = { openNewBill(null) },
                            onAddCustomer = { showCustomerDialog = true },
                            onAddProduct = {
                                editingProduct = null
                                showProductDialog = true
                            },
                            onOpenBill = { billId ->
                                viewModel.openReceipt(billId)
                                navController.navigate("receipt/$billId")
                            },
                            onViewAllBills = {
                                navController.navigate(ROUTE_BILLS) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }

                    composable(ROUTE_BILLS) {
                        BillsScreen(
                            profileState = profileState,
                            billsState = billsState,
                            onOpenBill = { billId ->
                                viewModel.openReceipt(billId)
                                navController.navigate("receipt/$billId")
                            },
                            onNewBill = { openNewBill(null) }
                        )
                    }

                    composable(ROUTE_CUSTOMERS) {
                        CustomersScreen(
                            profileState = profileState,
                            customersState = customersState,
                            onAddCustomer = { showCustomerDialog = true },
                            onOpenCustomer = { customerId ->
                                navController.navigate("customer/$customerId")
                            }
                        )
                    }

                    composable(ROUTE_CATALOG) {
                        CatalogScreen(
                            profileState = profileState,
                            productsState = productsState,
                            onAddProduct = {
                                editingProduct = null
                                showProductDialog = true
                            },
                            onEditProduct = { product ->
                                editingProduct = product
                                showProductDialog = true
                            },
                            onDeleteProduct = { productId ->
                                viewModel.deleteProduct(productId)
                            }
                        )
                    }

                    composable(ROUTE_CREATE_BILL) {
                        CreateBillScreen(
                            draft = billDraft,
                            profileState = profileState,
                            customersState = customersState,
                            productsState = productsState,
                            onBack = { navController.popBackStack() },
                            onSelectCustomer = { viewModel.selectDraftCustomer(it) },
                            onUpdateWalkIn = { name, phone -> viewModel.updateWalkInInfo(name, phone) },
                            onQuickCreateCustomer = { name, phone, email, address, notes, onCreated ->
                                viewModel.saveCustomer(null, name, phone, email, address, notes) { newId ->
                                    onCreated(
                                        Customer(
                                            id = newId,
                                            userId = userId,
                                            name = name.trim(),
                                            phone = phone.trim(),
                                            email = email.trim(),
                                            address = address.trim(),
                                            notes = notes.trim()
                                        )
                                    )
                                }
                            },
                            onAddProduct = { viewModel.addProductToDraft(it) },
                            onAddCustomItem = { name, qty, unit, price ->
                                viewModel.addCustomItemToDraft(name, qty, unit, price)
                            },
                            onUpdateItemQty = { key, qty -> viewModel.updateDraftItemQuantity(key, qty) },
                            onRemoveItem = { key -> viewModel.removeDraftItem(key) },
                            onUpdatePricing = { disc, tax -> viewModel.updateDraftPricing(disc, tax) },
                            onSelectPaymentMethod = { viewModel.updateDraftPaymentMethod(it) },
                            onUpdatePaidAmount = { viewModel.updateDraftPaidAmount(it) },
                            onSetPayFull = { viewModel.setDraftPayFull() },
                            onSetPayUnpaid = { viewModel.setDraftPayUnpaid() },
                            onUpdateNotes = { viewModel.updateDraftNotes(it) },
                            onSubmitBill = {
                                viewModel.submitBill { createdBillId ->
                                    navController.navigate("receipt/$createdBillId") {
                                        popUpTo(ROUTE_CREATE_BILL) { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    composable(
                        route = ROUTE_RECEIPT,
                        arguments = listOf(navArgument("billId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val billId = backStackEntry.arguments?.getString("billId").orEmpty()
                        LaunchedEffect(billId) {
                            if (billId.isNotBlank()) viewModel.openReceipt(billId)
                        }
                        ReceiptDetailScreen(
                            profileState = profileState,
                            billState = selectedBillState,
                            itemsState = selectedBillItemsState,
                            onBack = { navController.popBackStack() },
                            onRecordPayment = { bill, amt, method ->
                                viewModel.recordPayment(bill, amt, method)
                            },
                            onVoidBill = { bill ->
                                viewModel.voidBill(bill)
                            },
                            onDeleteBill = { id ->
                                viewModel.deleteBill(id) {
                                    navController.popBackStack()
                                }
                            }
                        )
                    }

                    composable(
                        route = ROUTE_CUSTOMER_DETAIL,
                        arguments = listOf(navArgument("customerId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val customerId = backStackEntry.arguments?.getString("customerId").orEmpty()
                        CustomerDetailScreen(
                            customerId = customerId,
                            profileState = profileState,
                            customersState = customersState,
                            billsState = billsState,
                            onBack = { navController.popBackStack() },
                            onEditCustomer = { cust, name, phone, email, address, notes ->
                                viewModel.saveCustomer(cust.id, name, phone, email, address, notes)
                            },
                            onDeleteCustomer = { id ->
                                viewModel.deleteCustomer(id) {
                                    navController.popBackStack()
                                }
                            },
                            onNewBillForCustomer = { cust -> openNewBill(cust) },
                            onOpenBill = { billId ->
                                viewModel.openReceipt(billId)
                                navController.navigate("receipt/$billId")
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        ShopSettingsDialog(
            profile = profile,
            userEmail = userEmail,
            onDismiss = { showSettingsDialog = false },
            onSave = { name, phone, address, curr, tax, footer ->
                viewModel.saveShopSettings(name, phone, address, curr, tax, footer) {
                    showSettingsDialog = false
                }
            },
            onSignOut = {
                showSettingsDialog = false
                signOut(credentialManager, scope)
            }
        )
    }

    if (showCustomerDialog) {
        CustomerFormDialog(
            initialCustomer = null,
            onDismiss = { showCustomerDialog = false },
            onSave = { name, phone, email, address, notes ->
                viewModel.saveCustomer(null, name, phone, email, address, notes) {
                    showCustomerDialog = false
                }
            }
        )
    }

    if (showProductDialog) {
        ProductFormDialog(
            initialProduct = editingProduct,
            currencySymbol = currency,
            onDismiss = { showProductDialog = false },
            onSave = { name, sku, category, price, unitLabel ->
                viewModel.saveProduct(editingProduct?.id, name, sku, category, price, unitLabel) {
                    showProductDialog = false
                }
            }
        )
    }
}
