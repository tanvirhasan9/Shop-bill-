package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Bill
import com.example.model.Customer
import com.example.model.ShopProfile
import com.example.model.UiState
import com.example.ui.components.BillSummaryCard
import com.example.ui.components.CustomerFormDialog
import com.example.ui.theme.ReceiptMonoBoldStyle
import com.example.ui.theme.UnpaidRed
import com.example.ui.theme.UnpaidRedBg
import com.example.util.formatMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerId: String,
    profileState: UiState<ShopProfile>,
    customersState: UiState<List<Customer>>,
    billsState: UiState<List<Bill>>,
    onBack: () -> Unit,
    onEditCustomer: (Customer, name: String, phone: String, email: String, address: String, notes: String) -> Unit,
    onDeleteCustomer: (String) -> Unit,
    onNewBillForCustomer: (Customer) -> Unit,
    onOpenBill: (String) -> Unit
) {
    val currency = (profileState as? UiState.Success)?.data?.currencySymbol?.ifBlank { "$" } ?: "$"
    val customer = (customersState as? UiState.Success)?.data?.firstOrNull { it.id == customerId }
    val customerBills = remember(billsState, customerId) {
        (billsState as? UiState.Success)?.data?.filter { it.customerId == customerId }.orEmpty()
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(customer?.name ?: "Customer") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("customer_detail_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (customer != null) {
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("edit_customer_button")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit customer")
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_customer_button")
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Delete customer",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (customer == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Customer not found", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text("Total Spent", style = MaterialTheme.typography.labelSmall)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = formatMoney(customer.totalSpent, currency),
                                                style = ReceiptMonoBoldStyle,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = MaterialTheme.shapes.medium,
                                        color = if (customer.totalDue > 0.0) UnpaidRedBg else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text("Balance Due", style = MaterialTheme.typography.labelSmall)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = formatMoney(customer.totalDue, currency),
                                                style = ReceiptMonoBoldStyle,
                                                color = if (customer.totalDue > 0.0) UnpaidRed else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (customer.phone.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(customer.phone, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                if (customer.email.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(customer.email, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                if (customer.address.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(customer.address, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                if (customer.notes.isNotBlank()) {
                                    Text(
                                        text = "Notes: ${customer.notes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Button(
                                    onClick = { onNewBillForCustomer(customer) },
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("customer_new_bill_button")
                                ) {
                                    Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("New Bill for ${customer.name}", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Receipt History (${customerBills.size})",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    if (customerBills.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text(
                                    text = "No receipts issued to this customer yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(customerBills, key = { it.id }) { bill ->
                            BillSummaryCard(
                                bill = bill,
                                currencySymbol = currency,
                                onClick = { onOpenBill(bill.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && customer != null) {
        CustomerFormDialog(
            initialCustomer = customer,
            onDismiss = { showEditDialog = false },
            onSave = { name, phone, email, address, notes ->
                onEditCustomer(customer, name, phone, email, address, notes)
                showEditDialog = false
            }
        )
    }

    if (showDeleteConfirm && customer != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${customer.name}?") },
            text = { Text("Existing receipts will remain in your history, but this customer profile will be removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCustomer(customer.id)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Text("Cancel")
                }
            }
        )
    }
}
