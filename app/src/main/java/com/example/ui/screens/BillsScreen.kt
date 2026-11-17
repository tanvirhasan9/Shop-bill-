package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.Bill
import com.example.model.BillStatus
import com.example.model.ShopProfile
import com.example.model.UiState
import com.example.ui.components.BillSummaryCard
import com.example.ui.components.EmptyStateCard

@Composable
fun BillsScreen(
    profileState: UiState<ShopProfile>,
    billsState: UiState<List<Bill>>,
    onOpenBill: (String) -> Unit,
    onNewBill: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currency = (profileState as? UiState.Success)?.data?.currencySymbol?.ifBlank { "$" } ?: "$"
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<BillStatus?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Bills & Receipts",
                    style = MaterialTheme.typography.headlineMedium
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search bill # or customer...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bills_search_input")
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedFilter == null,
                            onClick = { selectedFilter = null },
                            label = { Text("All") },
                            modifier = Modifier.testTag("filter_bills_all")
                        )
                    }
                    items(BillStatus.entries) { status ->
                        FilterChip(
                            selected = selectedFilter == status,
                            onClick = {
                                selectedFilter = if (selectedFilter == status) null else status
                            },
                            label = { Text(status.label) },
                            modifier = Modifier.testTag("filter_bills_${status.name.lowercase()}")
                        )
                    }
                }
            }

            when (billsState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = billsState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                is UiState.Success -> {
                    val filtered = remember(billsState.data, searchQuery, selectedFilter) {
                        val q = searchQuery.trim().lowercase()
                        billsState.data.filter { bill ->
                            val matchesFilter = selectedFilter == null || bill.parsedStatus() == selectedFilter
                            val matchesQuery = q.isEmpty() ||
                                bill.billNumber.lowercase().contains(q) ||
                                bill.customerName.lowercase().contains(q) ||
                                bill.customerPhone.lowercase().contains(q) ||
                                bill.itemsSummary.lowercase().contains(q)
                            matchesFilter && matchesQuery
                        }
                    }

                    if (filtered.isEmpty()) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            EmptyStateCard(
                                title = if (billsState.data.isEmpty()) "No bills issued yet" else "No matching receipts",
                                subtitle = if (billsState.data.isEmpty()) {
                                    "Create your first customer bill to view and share receipts here."
                                } else {
                                    "Try clearing your search or status filter."
                                },
                                actionLabel = "New Bill",
                                onAction = onNewBill,
                                actionTag = "bills_empty_new_bill_button",
                                illustrationRes = if (billsState.data.isEmpty()) R.drawable.img_empty_bills else null
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${filtered.size} receipt${if (filtered.size == 1) "" else "s"}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            items(filtered, key = { it.id }) { bill ->
                                BillSummaryCard(
                                    bill = bill,
                                    currencySymbol = currency,
                                    onClick = { onOpenBill(bill.id) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}
