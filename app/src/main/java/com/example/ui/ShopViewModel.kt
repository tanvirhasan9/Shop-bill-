package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.ShopRepository
import com.example.data.friendlyFirestoreMessage
import com.example.model.Bill
import com.example.model.BillLineItem
import com.example.model.BillStatus
import com.example.model.Customer
import com.example.model.DraftLineItem
import com.example.model.PaymentMethod
import com.example.model.ShopProduct
import com.example.model.ShopProfile
import com.example.model.UiState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class BillDraftState(
    val selectedCustomer: Customer? = null,
    val walkInName: String = "",
    val walkInPhone: String = "",
    val items: List<DraftLineItem> = emptyList(),
    val discountText: String = "",
    val taxPercentText: String = "0",
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val paidAmountText: String = "",
    val isPaidAutoSynced: Boolean = true,
    val notes: String = "",
    val isSaving: Boolean = false
) {
    val subtotal: Double
        get() = items.sumOf { it.lineTotal }

    val discountAmount: Double
        get() = (discountText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, subtotal)

    val taxPercent: Double
        get() = (taxPercentText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)

    val taxAmount: Double
        get() = ((subtotal - discountAmount).coerceAtLeast(0.0)) * (taxPercent / 100.0)

    val grandTotal: Double
        get() = ((subtotal - discountAmount).coerceAtLeast(0.0) + taxAmount).coerceAtLeast(0.0)

    val effectivePaidAmount: Double
        get() = if (isPaidAutoSynced) {
            if (paymentMethod == PaymentMethod.CREDIT) 0.0 else grandTotal
        } else {
            (paidAmountText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, grandTotal)
        }

    val dueAmount: Double
        get() = (grandTotal - effectivePaidAmount).coerceAtLeast(0.0)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShopViewModel(
    private val repository: ShopRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    val shopProfileState: StateFlow<UiState<ShopProfile>> = repository.observeShopProfile(currentUserId)
        .toUiState("Failed to load shop profile")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val customersState: StateFlow<UiState<List<Customer>>> = repository.observeCustomers(currentUserId)
        .toUiState("Failed to load customers")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val productsState: StateFlow<UiState<List<ShopProduct>>> = repository.observeProducts(currentUserId)
        .toUiState("Failed to load catalog")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val billsState: StateFlow<UiState<List<Bill>>> = repository.observeBills(currentUserId)
        .toUiState("Failed to load bills")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val activeReceiptBillId = MutableStateFlow<String?>(null)

    val selectedBillState: StateFlow<UiState<Bill?>> = activeReceiptBillId
        .flatMapLatest { billId ->
            if (billId.isNullOrBlank()) flowOf(UiState.Success(null))
            else repository.observeBill(currentUserId, billId).toUiState("Failed to load receipt")
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val selectedBillItemsState: StateFlow<UiState<List<BillLineItem>>> = activeReceiptBillId
        .flatMapLatest { billId ->
            if (billId.isNullOrBlank()) flowOf(UiState.Success(emptyList()))
            else repository.observeBillItems(currentUserId, billId).toUiState("Failed to load receipt items")
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _billDraft = MutableStateFlow(BillDraftState())
    val billDraft: StateFlow<BillDraftState> = _billDraft.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureShopProfile()
        }
    }

    fun openReceipt(billId: String) {
        activeReceiptBillId.value = billId
    }

    fun startNewBill(preselectedCustomer: Customer? = null) {
        val currentTax = (shopProfileState.value as? UiState.Success)?.data?.taxPercent ?: 0.0
        val taxStr = if (currentTax == 0.0) "0" else if (currentTax % 1.0 == 0.0) currentTax.toInt().toString() else currentTax.toString()
        _billDraft.value = BillDraftState(
            selectedCustomer = preselectedCustomer,
            taxPercentText = taxStr
        )
    }

    fun selectDraftCustomer(customer: Customer?) {
        _billDraft.update { it.copy(selectedCustomer = customer) }
    }

    fun updateWalkInInfo(name: String, phone: String) {
        _billDraft.update { it.copy(walkInName = name, walkInPhone = phone) }
    }

    fun addProductToDraft(product: ShopProduct) {
        _billDraft.update { state ->
            val existingIndex = state.items.indexOfFirst {
                it.productId.isNotBlank() && it.productId == product.id
            }
            val updatedItems = if (existingIndex >= 0) {
                state.items.mapIndexed { idx, item ->
                    if (idx == existingIndex) item.copy(quantity = item.quantity + 1.0) else item
                }
            } else {
                state.items + DraftLineItem(
                    localKey = UUID.randomUUID().toString(),
                    productId = product.id,
                    name = product.name,
                    quantity = 1.0,
                    unitLabel = product.unitLabel.ifBlank { "pcs" },
                    unitPrice = product.unitPrice
                )
            }
            state.copy(items = updatedItems)
        }
    }

    fun addCustomItemToDraft(name: String, quantity: Double, unitLabel: String, unitPrice: Double) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        _billDraft.update { state ->
            state.copy(
                items = state.items + DraftLineItem(
                    localKey = UUID.randomUUID().toString(),
                    productId = "",
                    name = cleanName,
                    quantity = quantity.coerceAtLeast(0.01),
                    unitLabel = unitLabel.trim().ifBlank { "pcs" },
                    unitPrice = unitPrice.coerceAtLeast(0.0)
                )
            )
        }
    }

    fun updateDraftItemQuantity(localKey: String, newQuantity: Double) {
        _billDraft.update { state ->
            if (newQuantity <= 0.0) {
                state.copy(items = state.items.filterNot { it.localKey == localKey })
            } else {
                state.copy(
                    items = state.items.map {
                        if (it.localKey == localKey) it.copy(quantity = newQuantity) else it
                    }
                )
            }
        }
    }

    fun removeDraftItem(localKey: String) {
        _billDraft.update { state ->
            state.copy(items = state.items.filterNot { it.localKey == localKey })
        }
    }

    fun updateDraftPricing(discountText: String, taxPercentText: String) {
        _billDraft.update { it.copy(discountText = discountText, taxPercentText = taxPercentText) }
    }

    fun updateDraftPaymentMethod(method: PaymentMethod) {
        _billDraft.update {
            it.copy(
                paymentMethod = method,
                isPaidAutoSynced = true,
                paidAmountText = ""
            )
        }
    }

    fun updateDraftPaidAmount(paidText: String) {
        _billDraft.update {
            it.copy(
                isPaidAutoSynced = false,
                paidAmountText = paidText
            )
        }
    }

    fun setDraftPayFull() {
        _billDraft.update {
            it.copy(
                isPaidAutoSynced = true,
                paidAmountText = "",
                paymentMethod = if (it.paymentMethod == PaymentMethod.CREDIT) PaymentMethod.CASH else it.paymentMethod
            )
        }
    }

    fun setDraftPayUnpaid() {
        _billDraft.update {
            it.copy(
                paymentMethod = PaymentMethod.CREDIT,
                isPaidAutoSynced = true,
                paidAmountText = "0"
            )
        }
    }

    fun updateDraftNotes(notes: String) {
        _billDraft.update { it.copy(notes = notes) }
    }

    fun submitBill(onBillCreated: (String) -> Unit) {
        val draft = _billDraft.value
        if (draft.isSaving) return
        if (draft.items.isEmpty()) {
            emitMessage("Please add at least one item before generating a receipt.")
            return
        }
        val profile = (shopProfileState.value as? UiState.Success)?.data
            ?: ShopProfile(id = currentUserId, userId = currentUserId)

        _billDraft.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = repository.createBill(
                profile = profile,
                customer = draft.selectedCustomer,
                walkInName = draft.walkInName,
                walkInPhone = draft.walkInPhone,
                items = draft.items,
                discountAmount = draft.discountAmount,
                taxPercent = draft.taxPercent,
                paidAmount = draft.effectivePaidAmount,
                paymentMethod = draft.paymentMethod,
                notes = draft.notes
            )
            _billDraft.update { it.copy(isSaving = false) }
            result.onSuccess { newBillId ->
                activeReceiptBillId.value = newBillId
                startNewBill()
                onBillCreated(newBillId)
            }.onFailure { err ->
                emitMessage(friendlyFirestoreMessage(err))
            }
        }
    }

    fun saveShopSettings(
        shopName: String,
        shopPhone: String,
        shopAddress: String,
        currencySymbol: String,
        taxPercent: Double,
        receiptFooter: String,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            repository.updateShopProfile(
                shopName = shopName,
                shopPhone = shopPhone,
                shopAddress = shopAddress,
                currencySymbol = currencySymbol,
                taxPercent = taxPercent,
                receiptFooter = receiptFooter
            ).onSuccess {
                emitMessage("Shop settings saved")
                onDone()
            }.onFailure { err ->
                emitMessage(friendlyFirestoreMessage(err))
            }
        }
    }

    fun saveCustomer(
        customerId: String?,
        name: String,
        phone: String,
        email: String,
        address: String,
        notes: String,
        onDone: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.saveCustomer(customerId, name, phone, email, address, notes)
                .onSuccess { id ->
                    emitMessage(if (customerId == null) "Customer added" else "Customer updated")
                    onDone(id)
                }
                .onFailure { err -> emitMessage(friendlyFirestoreMessage(err)) }
        }
    }

    fun deleteCustomer(customerId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteCustomer(customerId)
                .onSuccess {
                    emitMessage("Customer deleted")
                    onDone()
                }
                .onFailure { err -> emitMessage(friendlyFirestoreMessage(err)) }
        }
    }

    fun saveProduct(
        productId: String?,
        name: String,
        sku: String,
        category: String,
        unitPrice: Double,
        unitLabel: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.saveProduct(productId, name, sku, category, unitPrice, unitLabel)
                .onSuccess {
                    emitMessage(if (productId == null) "Item added to catalog" else "Item updated")
                    onDone()
                }
                .onFailure { err -> emitMessage(friendlyFirestoreMessage(err)) }
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            repository.deleteProduct(productId)
                .onSuccess { emitMessage("Item removed") }
                .onFailure { err -> emitMessage(friendlyFirestoreMessage(err)) }
        }
    }

    fun recordPayment(bill: Bill, amount: Double, method: PaymentMethod, onDone: () -> Unit = {}) {
        val customers = (customersState.value as? UiState.Success)?.data.orEmpty()
        val linkedCustomer = customers.firstOrNull { it.id == bill.customerId }
        viewModelScope.launch {
            repository.recordBillPayment(bill, linkedCustomer, amount, method)
                .onSuccess {
                    emitMessage("Payment recorded")
                    onDone()
                }
                .onFailure { err -> emitMessage(friendlyFirestoreMessage(err)) }
        }
    }

    fun voidBill(bill: Bill, onDone: () -> Unit = {}) {
        if (bill.parsedStatus() == BillStatus.VOID) return
        val customers = (customersState.value as? UiState.Success)?.data.orEmpty()
        val linkedCustomer = customers.firstOrNull { it.id == bill.customerId }
        viewModelScope.launch {
            repository.voidBill(bill, linkedCustomer)
                .onSuccess {
                    emitMessage("Bill ${bill.billNumber} voided")
                    onDone()
                }
                .onFailure { err -> emitMessage(friendlyFirestoreMessage(err)) }
        }
    }

    fun deleteBill(billId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteBill(billId)
                .onSuccess {
                    emitMessage("Bill deleted")
                    onDone()
                }
                .onFailure { err -> emitMessage(friendlyFirestoreMessage(err)) }
        }
    }

    private fun emitMessage(msg: String) {
        _messages.tryEmit(msg)
    }

    private fun <T> Flow<T>.toUiState(fallbackError: String): Flow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Stream error: $fallbackError", rootError)
                emit(UiState.Error(friendlyFirestoreMessage(rootError)))
            }

    companion object {
        private const val TAG = "ShopViewModel"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun provideFactory(currentUserId: String) = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                } as Application
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ShopViewModel(ShopRepository(db), currentUserId)
            }
        }
    }
}
