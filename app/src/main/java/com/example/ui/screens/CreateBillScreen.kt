package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Customer
import com.example.model.DraftLineItem
import com.example.model.PaymentMethod
import com.example.model.ShopProduct
import com.example.model.ShopProfile
import com.example.model.UiState
import com.example.ui.BillDraftState
import com.example.ui.components.CustomerFormDialog
import com.example.ui.theme.ReceiptMonoBoldStyle
import com.example.ui.theme.ReceiptMonoStyle
import com.example.ui.theme.UnpaidRed
import com.example.util.formatMoney
import com.example.util.formatQty

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBillScreen(
    draft: BillDraftState,
    profileState: UiState<ShopProfile>,
    customersState: UiState<List<Customer>>,
    productsState: UiState<List<ShopProduct>>,
    onBack: () -> Unit,
    onSelectCustomer: (Customer?) -> Unit,
    onUpdateWalkIn: (String, String) -> Unit,
    onQuickCreateCustomer: (name: String, phone: String, email: String, address: String, notes: String, onCreated: (Customer) -> Unit) -> Unit,
    onAddProduct: (ShopProduct) -> Unit,
    onAddCustomItem: (name: String, qty: Double, unitLabel: String, price: Double) -> Unit,
    onUpdateItemQty: (String, Double) -> Unit,
    onRemoveItem: (String) -> Unit,
    onUpdatePricing: (discountText: String, taxPercentText: String) -> Unit,
    onSelectPaymentMethod: (PaymentMethod) -> Unit,
    onUpdatePaidAmount: (String) -> Unit,
    onSetPayFull: () -> Unit,
    onSetPayUnpaid: () -> Unit,
    onUpdateNotes: (String) -> Unit,
    onSubmitBill: () -> Unit
) {
    val profile = (profileState as? UiState.Success)?.data ?: ShopProfile()
    val currency = profile.currencySymbol.ifBlank { "$" }
    val customers = (customersState as? UiState.Success)?.data.orEmpty()
    val products = (productsState as? UiState.Success)?.data.orEmpty()

    var showCustomerPicker by remember { mutableStateOf(false) }
    var showQuickCustomerCreate by remember { mutableStateOf(false) }
    var showCustomItemDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Customer Bill") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("create_bill_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${draft.items.size} item${if (draft.items.size == 1) "" else "s"} • Grand Total",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatMoney(draft.grandTotal, currency),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onSubmitBill,
                        enabled = !draft.isSaving && draft.items.isNotEmpty(),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .testTag("issue_receipt_button")
                    ) {
                        if (draft.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving...")
                        } else {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Issue Receipt", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Customer Section
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Customer", style = MaterialTheme.typography.titleMedium)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(
                                        onClick = { showCustomerPicker = true },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("pick_customer_button")
                                    ) {
                                        Text(if (draft.selectedCustomer == null) "Select Saved" else "Change")
                                    }
                                }
                            }

                            if (draft.selectedCustomer != null) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = draft.selectedCustomer.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            if (draft.selectedCustomer.phone.isNotBlank()) {
                                                Text(
                                                    text = draft.selectedCustomer.phone,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                        TextButton(
                                            onClick = { onSelectCustomer(null) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Text("Walk-in")
                                        }
                                    }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = draft.walkInName,
                                        onValueChange = { onUpdateWalkIn(it, draft.walkInPhone) },
                                        label = { Text("Customer Name (or Walk-in)") },
                                        placeholder = { Text("Walk-in Customer") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("walkin_name_input")
                                    )
                                    OutlinedTextField(
                                        value = draft.walkInPhone,
                                        onValueChange = { onUpdateWalkIn(draft.walkInName, it) },
                                        label = { Text("Phone") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        modifier = Modifier
                                            .weight(0.8f)
                                            .testTag("walkin_phone_input")
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Quick Add Catalog + Custom Item Button
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Items", style = MaterialTheme.typography.titleMedium)
                            OutlinedButton(
                                onClick = { showCustomItemDialog = true },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("add_custom_item_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Custom Item")
                            }
                        }

                        if (products.isNotEmpty()) {
                            Text(
                                text = "Tap catalog items to add:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(products, key = { it.id }) { product ->
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .clickable { onAddProduct(product) }
                                            .testTag("quick_add_product_${product.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Filled.Add,
                                                contentDescription = "Add ${product.name}",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = product.name,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${formatMoney(product.unitPrice, currency)} / ${product.unitLabel}",
                                                    style = ReceiptMonoStyle,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Cart Line Items
                if (draft.items.isEmpty()) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No items in this bill yet",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap a catalog item above or tap Custom Item to add lines.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(draft.items, key = { it.localKey }) { item ->
                        DraftLineItemRow(
                            item = item,
                            currency = currency,
                            onDecrease = { onUpdateItemQty(item.localKey, item.quantity - 1.0) },
                            onIncrease = { onUpdateItemQty(item.localKey, item.quantity + 1.0) },
                            onRemove = { onRemoveItem(item.localKey) }
                        )
                    }
                }

                // 4. Totals, Discount, Tax & Payment
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Totals & Payment", style = MaterialTheme.typography.titleMedium)

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = draft.discountText,
                                    onValueChange = { onUpdatePricing(it, draft.taxPercentText) },
                                    label = { Text("Discount ($currency)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("bill_discount_input")
                                )
                                OutlinedTextField(
                                    value = draft.taxPercentText,
                                    onValueChange = { onUpdatePricing(draft.discountText, it) },
                                    label = { Text("Tax %") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("bill_tax_input")
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            SummaryAmountRow("Subtotal", formatMoney(draft.subtotal, currency))
                            if (draft.discountAmount > 0.0) {
                                SummaryAmountRow("Discount", "-${formatMoney(draft.discountAmount, currency)}")
                            }
                            if (draft.taxAmount > 0.0 || draft.taxPercent > 0.0) {
                                SummaryAmountRow(
                                    "Tax (${formatQty(draft.taxPercent)}%)",
                                    formatMoney(draft.taxAmount, currency)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Grand Total", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = formatMoney(draft.grandTotal, currency),
                                    style = ReceiptMonoBoldStyle,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Text("Payment Method", style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(PaymentMethod.entries) { method ->
                                    FilterChip(
                                        selected = draft.paymentMethod == method,
                                        onClick = { onSelectPaymentMethod(method) },
                                        label = { Text(method.label) },
                                        modifier = Modifier.testTag("payment_method_${method.name.lowercase()}")
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = if (draft.isPaidAutoSynced) {
                                        if (draft.effectivePaidAmount == 0.0) "0"
                                        else String.format("%.2f", draft.effectivePaidAmount)
                                    } else draft.paidAmountText,
                                    onValueChange = onUpdatePaidAmount,
                                    label = { Text("Amount Paid ($currency)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("bill_paid_amount_input")
                                )
                                TextButton(
                                    onClick = onSetPayFull,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("pay_full_button")
                                ) {
                                    Text("Full")
                                }
                                TextButton(
                                    onClick = onSetPayUnpaid,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("pay_unpaid_button")
                                ) {
                                    Text("Unpaid")
                                }
                            }

                            if (draft.dueAmount > 0.0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Balance Due",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = UnpaidRed
                                    )
                                    Text(
                                        text = formatMoney(draft.dueAmount, currency),
                                        style = ReceiptMonoBoldStyle,
                                        color = UnpaidRed
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = draft.notes,
                                onValueChange = onUpdateNotes,
                                label = { Text("Receipt Notes (optional)") },
                                maxLines = 2,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("bill_notes_input")
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    if (showCustomerPicker) {
        CustomerPickerDialog(
            customers = customers,
            onDismiss = { showCustomerPicker = false },
            onSelect = { customer ->
                onSelectCustomer(customer)
                showCustomerPicker = false
            },
            onCreateNew = {
                showCustomerPicker = false
                showQuickCustomerCreate = true
            }
        )
    }

    if (showQuickCustomerCreate) {
        CustomerFormDialog(
            initialCustomer = null,
            onDismiss = { showQuickCustomerCreate = false },
            onSave = { name, phone, email, address, notes ->
                onQuickCreateCustomer(name, phone, email, address, notes) { created ->
                    onSelectCustomer(created)
                }
                showQuickCustomerCreate = false
            }
        )
    }

    if (showCustomItemDialog) {
        CustomItemDialog(
            currency = currency,
            onDismiss = { showCustomItemDialog = false },
            onAdd = { name, qty, unit, price ->
                onAddCustomItem(name, qty, unit, price)
                showCustomItemDialog = false
            }
        )
    }
}

@Composable
private fun DraftLineItemRow(
    item: DraftLineItem,
    currency: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatMoney(item.unitPrice, currency)} / ${item.unitLabel}",
                    style = ReceiptMonoStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onDecrease,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity")
                }
                Text(
                    text = formatQty(item.quantity),
                    style = ReceiptMonoBoldStyle,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(
                    onClick = onIncrease,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase quantity")
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatMoney(item.lineTotal, currency),
                    style = ReceiptMonoBoldStyle
                )
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Remove item",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryAmountRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = ReceiptMonoStyle)
    }
}

@Composable
private fun CustomItemDialog(
    currency: String,
    onDismiss: () -> Unit,
    onAdd: (name: String, qty: Double, unitLabel: String, price: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("1") }
    var unitLabel by remember { mutableStateOf("pcs") }
    var priceText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Item to Bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Item Description *") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_item_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("Qty") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_item_qty_input")
                    )
                    OutlinedTextField(
                        value = unitLabel,
                        onValueChange = { unitLabel = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_item_unit_input")
                    )
                }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Unit Price ($currency)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_item_price_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isEmpty()) {
                        error = "Enter item description"
                    } else {
                        val qty = (qtyText.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.01)
                        val price = (priceText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                        onAdd(name, qty, unitLabel.ifBlank { "pcs" }, price)
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_custom_item_button")
            ) {
                Text("Add Item")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CustomerPickerDialog(
    customers: List<Customer>,
    onDismiss: () -> Unit,
    onSelect: (Customer) -> Unit,
    onCreateNew: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(customers, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) customers
        else customers.filter {
            it.name.lowercase().contains(q) || it.phone.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select Customer")
                TextButton(
                    onClick = onCreateNew,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("picker_new_customer_button")
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search name or phone...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Text(
                        text = "No matching customers. Tap New to add one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { it.id }) { cust ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(cust) }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(cust.name, style = MaterialTheme.typography.titleSmall)
                                    if (cust.phone.isNotBlank()) {
                                        Text(
                                            cust.phone,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                Text("Close")
            }
        }
    )
}
