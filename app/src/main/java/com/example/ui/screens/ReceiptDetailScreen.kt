package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.Bill
import com.example.model.BillLineItem
import com.example.model.BillStatus
import com.example.model.PaymentMethod
import com.example.model.ShopProfile
import com.example.model.UiState
import com.example.ui.components.BillStatusPill
import com.example.ui.theme.CrispPaperWhite
import com.example.ui.theme.CharcoalInk
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.PaperOutlineVariant
import com.example.ui.theme.ReceiptMonoBoldStyle
import com.example.ui.theme.ReceiptMonoStyle
import com.example.ui.theme.UnpaidRed
import com.example.util.formatBillTimestamp
import com.example.util.formatMoney
import com.example.util.formatQty
import com.example.util.printReceiptPdf
import com.example.util.shareReceiptImage
import com.example.util.shareTextReceipt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailScreen(
    profileState: UiState<ShopProfile>,
    billState: UiState<Bill?>,
    itemsState: UiState<List<BillLineItem>>,
    onBack: () -> Unit,
    onRecordPayment: (Bill, Double, PaymentMethod) -> Unit,
    onVoidBill: (Bill) -> Unit,
    onDeleteBill: (String) -> Unit
) {
    val context = LocalContext.current
    val profile = (profileState as? UiState.Success)?.data ?: ShopProfile()
    val currency = profile.currencySymbol.ifBlank { "$" }
    val bill = (billState as? UiState.Success)?.data
    val lineItems = (itemsState as? UiState.Success)?.data.orEmpty()

    var showMenu by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showVoidConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(bill?.billNumber ?: "Digital Receipt") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("receipt_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (bill != null) {
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("receipt_more_button")
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                if (bill.parsedStatus() != BillStatus.VOID) {
                                    DropdownMenuItem(
                                        text = { Text("Void Bill") },
                                        leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            showVoidConfirm = true
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete Receipt", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirm = true
                                    }
                                )
                            }
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
            when {
                billState is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                billState is UiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = billState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                bill == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Receipt not found", style = MaterialTheme.typography.titleMedium)
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Tactile Paper Receipt Sheet
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SerratedReceiptEdge(isTop = true, paperColor = CrispPaperWhite)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CrispPaperWhite)
                                        .padding(horizontal = 22.dp, vertical = 18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Shop Header
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = profile.shopName.ifBlank { "MY SHOP" }.uppercase(),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = CharcoalInk,
                                            textAlign = TextAlign.Center
                                        )
                                        if (profile.shopAddress.isNotBlank()) {
                                            Text(
                                                text = profile.shopAddress,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MutedSlate,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        if (profile.shopPhone.isNotBlank()) {
                                            Text(
                                                text = "Tel: ${profile.shopPhone}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MutedSlate
                                            )
                                        }
                                    }

                                    DashedReceiptDivider()

                                    // Bill Meta
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = bill.billNumber,
                                                style = ReceiptMonoBoldStyle,
                                                color = CharcoalInk
                                            )
                                            Text(
                                                text = formatBillTimestamp(bill.createdAt),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MutedSlate
                                            )
                                        }
                                        BillStatusPill(status = bill.parsedStatus())
                                    }

                                    Column {
                                        Text(
                                            text = "CUSTOMER: ${bill.customerName}",
                                            style = ReceiptMonoStyle,
                                            color = CharcoalInk
                                        )
                                        if (bill.customerPhone.isNotBlank()) {
                                            Text(
                                                text = "PHONE: ${bill.customerPhone}",
                                                style = ReceiptMonoStyle,
                                                color = MutedSlate
                                            )
                                        }
                                    }

                                    DashedReceiptDivider()

                                    // Line Items Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("ITEM", style = MaterialTheme.typography.labelSmall, color = MutedSlate)
                                        Text("AMOUNT", style = MaterialTheme.typography.labelSmall, color = MutedSlate)
                                    }

                                    if (itemsState is UiState.Loading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                    } else {
                                        lineItems.forEach { item ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        color = CharcoalInk
                                                    )
                                                    Text(
                                                        text = "${formatQty(item.quantity)} ${item.unitLabel} × ${formatMoney(item.unitPrice, currency)}",
                                                        style = ReceiptMonoStyle,
                                                        color = MutedSlate
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = formatMoney(item.lineTotal, currency),
                                                    style = ReceiptMonoStyle,
                                                    color = CharcoalInk
                                                )
                                            }
                                        }
                                    }

                                    DashedReceiptDivider()

                                    // Totals
                                    ReceiptRow("Subtotal", formatMoney(bill.subtotal, currency))
                                    if (bill.discountAmount > 0.0) {
                                        ReceiptRow("Discount", "-${formatMoney(bill.discountAmount, currency)}")
                                    }
                                    if (bill.taxAmount > 0.0 || bill.taxPercent > 0.0) {
                                        ReceiptRow(
                                            "Tax (${formatQty(bill.taxPercent)}%)",
                                            formatMoney(bill.taxAmount, currency)
                                        )
                                    }

                                    HorizontalDivider(color = PaperOutlineVariant)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "TOTAL",
                                            style = ReceiptMonoBoldStyle,
                                            color = CharcoalInk
                                        )
                                        Text(
                                            text = formatMoney(bill.totalAmount, currency),
                                            style = ReceiptMonoBoldStyle,
                                            color = CharcoalInk
                                        )
                                    }

                                    ReceiptRow(
                                        "Paid (${bill.parsedPaymentMethod().label})",
                                        formatMoney(bill.paidAmount, currency)
                                    )

                                    if (bill.dueAmount > 0.0 && bill.parsedStatus() != BillStatus.VOID) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "BALANCE DUE",
                                                style = ReceiptMonoBoldStyle,
                                                color = UnpaidRed
                                            )
                                            Text(
                                                text = formatMoney(bill.dueAmount, currency),
                                                style = ReceiptMonoBoldStyle,
                                                color = UnpaidRed
                                            )
                                        }
                                    }

                                    if (bill.notes.isNotBlank()) {
                                        DashedReceiptDivider()
                                        Text(
                                            text = "Note: ${bill.notes}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MutedSlate
                                        )
                                    }

                                    if (profile.receiptFooter.isNotBlank()) {
                                        DashedReceiptDivider()
                                        Text(
                                            text = profile.receiptFooter,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MutedSlate,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                SerratedReceiptEdge(isTop = false, paperColor = CrispPaperWhite)
                            }
                        }

                        // Record Payment CTA when balance due > 0
                        if (bill.dueAmount > 0.0 && bill.parsedStatus() != BillStatus.VOID) {
                            item {
                                Button(
                                    onClick = { showPaymentDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    ),
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .testTag("record_payment_button")
                                ) {
                                    Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Record Payment (${formatMoney(bill.dueAmount, currency)} due)",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Export & Share Actions
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { shareReceiptImage(context, profile, bill, lineItems) },
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("share_receipt_image_button")
                                ) {
                                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share Image")
                                }
                                OutlinedButton(
                                    onClick = { shareTextReceipt(context, profile, bill, lineItems) },
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("share_receipt_text_button")
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share Text")
                                }
                            }
                        }

                        item {
                            OutlinedButton(
                                onClick = { printReceiptPdf(context, profile, bill, lineItems) },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("print_pdf_receipt_button")
                            ) {
                                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Print or Save as PDF")
                            }
                        }

                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    if (showPaymentDialog && bill != null) {
        RecordPaymentDialog(
            dueAmount = bill.dueAmount,
            currency = currency,
            onDismiss = { showPaymentDialog = false },
            onConfirm = { amount, method ->
                onRecordPayment(bill, amount, method)
                showPaymentDialog = false
            }
        )
    }

    if (showVoidConfirm && bill != null) {
        AlertDialog(
            onDismissRequest = { showVoidConfirm = false },
            title = { Text("Void ${bill.billNumber}?") },
            text = { Text("Voiding marks this receipt as cancelled and clears any unpaid balance. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onVoidBill(bill)
                        showVoidConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Void Bill")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoidConfirm = false }, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirm && bill != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${bill.billNumber}?") },
            text = { Text("Permanently remove this receipt from your records?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBill(bill.id)
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

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = ReceiptMonoStyle, color = MutedSlate)
        Text(text = value, style = ReceiptMonoStyle, color = CharcoalInk)
    }
}

@Composable
private fun DashedReceiptDivider() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .padding(vertical = 1.dp)
    ) {
        val dashWidth = 10f
        val gap = 8f
        var x = 0f
        while (x < size.width) {
            drawLine(
                color = PaperOutlineVariant,
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset((x + dashWidth).coerceAtMost(size.width), 0f),
                strokeWidth = 2f
            )
            x += dashWidth + gap
        }
    }
}

@Composable
private fun SerratedReceiptEdge(isTop: Boolean, paperColor: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
    ) {
        val toothWidth = 24f
        val count = (size.width / toothWidth).toInt().coerceAtLeast(1)
        val step = size.width / count
        val path = Path()
        if (isTop) {
            path.moveTo(0f, size.height)
            for (i in 0 until count) {
                val startX = i * step
                path.lineTo(startX + step / 2f, 0f)
                path.lineTo(startX + step, size.height)
            }
            path.close()
        } else {
            path.moveTo(0f, 0f)
            for (i in 0 until count) {
                val startX = i * step
                path.lineTo(startX + step / 2f, size.height)
                path.lineTo(startX + step, 0f)
            }
            path.close()
        }
        drawPath(path = path, color = paperColor)
    }
}

@Composable
private fun RecordPaymentDialog(
    dueAmount: Double,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, PaymentMethod) -> Unit
) {
    var amountText by remember { mutableStateOf(String.format("%.2f", dueAmount)) }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Remaining due: ${formatMoney(dueAmount, currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; error = null },
                    label = { Text("Payment Amount ($currency)") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("record_payment_amount_input")
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PaymentMethod.entries.filter { it != PaymentMethod.CREDIT }) { pm ->
                        FilterChip(
                            selected = method == pm,
                            onClick = { method = pm },
                            label = { Text(pm.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    if (amt == null || amt <= 0.0) {
                        error = "Enter a positive amount"
                    } else {
                        onConfirm(amt.coerceAtMost(dueAmount), method)
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_record_payment_button")
            ) {
                Text("Save Payment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                Text("Cancel")
            }
        }
    )
}
