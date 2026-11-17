package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Bill
import com.example.model.BillStatus
import com.example.model.Customer
import com.example.model.ShopProduct
import com.example.model.ShopProfile
import com.example.ui.theme.PaidGreen
import com.example.ui.theme.PaidGreenBg
import com.example.ui.theme.PartialAmber
import com.example.ui.theme.PartialAmberBg
import com.example.ui.theme.ReceiptMonoBoldStyle
import com.example.ui.theme.ReceiptMonoStyle
import com.example.ui.theme.UnpaidRed
import com.example.ui.theme.UnpaidRedBg
import com.example.ui.theme.VoidSlate
import com.example.ui.theme.VoidSlateBg
import com.example.util.formatBillTimestamp
import com.example.util.formatMoney

@Composable
fun BillStatusPill(status: BillStatus, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        BillStatus.PAID -> PaidGreenBg to PaidGreen
        BillStatus.PARTIAL -> PartialAmberBg to PartialAmber
        BillStatus.UNPAID -> UnpaidRedBg to UnpaidRed
        BillStatus.VOID -> VoidSlateBg to VoidSlate
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Text(
            text = status.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun BillSummaryCard(
    bill: Bill,
    currencySymbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = bill.parsedStatus()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bill_card_${bill.id}")
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = bill.customerName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${bill.billNumber}  •  ${formatBillTimestamp(bill.createdAt)}",
                            style = ReceiptMonoStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                BillStatusPill(status = status)
            }

            if (bill.itemsSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = bill.itemsSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${bill.itemCount} item${if (bill.itemCount == 1L) "" else "s"} • ${bill.parsedPaymentMethod().label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (bill.dueAmount > 0.0 && status != BillStatus.VOID) {
                        Text(
                            text = "Due ${formatMoney(bill.dueAmount, currencySymbol)}  •  ",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = UnpaidRed
                        )
                    }
                    Text(
                        text = formatMoney(bill.totalAmount, currencySymbol),
                        style = ReceiptMonoBoldStyle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    actionTag: String,
    @DrawableRes illustrationRes: Int? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (illustrationRes != null) {
                Image(
                    painter = painterResource(id = illustrationRes),
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAction,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(actionTag)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun CustomerFormDialog(
    initialCustomer: Customer?,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, email: String, address: String, notes: String) -> Unit
) {
    var name by remember(initialCustomer) { mutableStateOf(initialCustomer?.name ?: "") }
    var phone by remember(initialCustomer) { mutableStateOf(initialCustomer?.phone ?: "") }
    var email by remember(initialCustomer) { mutableStateOf(initialCustomer?.email ?: "") }
    var address by remember(initialCustomer) { mutableStateOf(initialCustomer?.address ?: "") }
    var notes by remember(initialCustomer) { mutableStateOf(initialCustomer?.notes ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialCustomer == null) "Add Customer" else "Edit Customer",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text("Customer Name *") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_phone_input")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_email_input")
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_address_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isEmpty()) {
                        nameError = "Please enter customer name"
                    } else {
                        onSave(name, phone, email, address, notes)
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_customer_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ProductFormDialog(
    initialProduct: ShopProduct?,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (name: String, sku: String, category: String, price: Double, unitLabel: String) -> Unit
) {
    var name by remember(initialProduct) { mutableStateOf(initialProduct?.name ?: "") }
    var sku by remember(initialProduct) { mutableStateOf(initialProduct?.sku ?: "") }
    var category by remember(initialProduct) { mutableStateOf(initialProduct?.category ?: "") }
    var priceText by remember(initialProduct) {
        mutableStateOf(
            if (initialProduct != null && initialProduct.unitPrice > 0.0) {
                initialProduct.unitPrice.toString()
            } else ""
        )
    }
    var unitLabel by remember(initialProduct) { mutableStateOf(initialProduct?.unitLabel ?: "pcs") }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialProduct == null) "Add Catalog Item" else "Edit Item",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text("Item / Service Name *") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Price ($currencySymbol)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_price_input")
                    )
                    OutlinedTextField(
                        value = unitLabel,
                        onValueChange = { unitLabel = it },
                        label = { Text("Unit") },
                        placeholder = { Text("pcs / kg") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("product_unit_input")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_category_input")
                    )
                    OutlinedTextField(
                        value = sku,
                        onValueChange = { sku = it },
                        label = { Text("SKU / Code") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("product_sku_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isEmpty()) {
                        nameError = "Please enter an item name"
                    } else {
                        val price = priceText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        onSave(name, sku, category, price, unitLabel.ifBlank { "pcs" })
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_product_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ShopSettingsDialog(
    profile: ShopProfile,
    userEmail: String?,
    onDismiss: () -> Unit,
    onSave: (
        shopName: String,
        shopPhone: String,
        shopAddress: String,
        currencySymbol: String,
        taxPercent: Double,
        receiptFooter: String
    ) -> Unit,
    onSignOut: () -> Unit
) {
    var shopName by remember(profile) { mutableStateOf(profile.shopName) }
    var shopPhone by remember(profile) { mutableStateOf(profile.shopPhone) }
    var shopAddress by remember(profile) { mutableStateOf(profile.shopAddress) }
    var currencySymbol by remember(profile) { mutableStateOf(profile.currencySymbol) }
    var taxPercentText by remember(profile) {
        mutableStateOf(
            if (profile.taxPercent == 0.0) "0"
            else if (profile.taxPercent % 1.0 == 0.0) profile.taxPercent.toInt().toString()
            else profile.taxPercent.toString()
        )
    }
    var receiptFooter by remember(profile) { mutableStateOf(profile.receiptFooter) }
    var nameError by remember { mutableStateOf<String?>(null) }

    val quickCurrencies = listOf("$", "৳", "₹", "€", "£")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Storefront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Shop Settings", style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close settings")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it; nameError = null },
                    label = { Text("Shop Name *") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_shop_name_input")
                )
                OutlinedTextField(
                    value = shopPhone,
                    onValueChange = { shopPhone = it },
                    label = { Text("Shop Phone") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_shop_phone_input")
                )
                OutlinedTextField(
                    value = shopAddress,
                    onValueChange = { shopAddress = it },
                    label = { Text("Shop Address") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_shop_address_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currencySymbol,
                        onValueChange = { currencySymbol = it.take(6) },
                        label = { Text("Currency") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.9f)
                            .testTag("settings_currency_input")
                    )
                    OutlinedTextField(
                        value = taxPercentText,
                        onValueChange = { taxPercentText = it },
                        label = { Text("Default Tax %") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_tax_input")
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    quickCurrencies.forEach { sym ->
                        Surface(
                            shape = CircleShape,
                            color = if (currencySymbol == sym) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable { currencySymbol = sym }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = sym,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (currencySymbol == sym) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = receiptFooter,
                    onValueChange = { receiptFooter = it },
                    label = { Text("Receipt Footer Note") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_footer_input")
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Signed in account", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = userEmail ?: "Google Account",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign out")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (shopName.trim().isEmpty()) {
                        nameError = "Shop name is required"
                    } else {
                        val tax = (taxPercentText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)
                        onSave(shopName, shopPhone, shopAddress, currencySymbol.ifBlank { "$" }, tax, receiptFooter)
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_settings_button")
            ) {
                Text("Save Settings")
            }
        }
    )
}
