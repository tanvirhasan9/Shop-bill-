package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PaymentMethod(val label: String) {
    CASH("Cash"),
    CARD("Card"),
    UPI("UPI / Wallet"),
    BANK("Bank"),
    CREDIT("Credit");

    companion object {
        fun fromRaw(raw: String?): PaymentMethod =
            entries.firstOrNull { it.name == raw } ?: CASH
    }
}

enum class BillStatus(val label: String) {
    PAID("Paid"),
    PARTIAL("Partial"),
    UNPAID("Unpaid"),
    VOID("Void");

    companion object {
        fun fromRaw(raw: String?): BillStatus =
            entries.firstOrNull { it.name == raw } ?: PAID
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class ShopProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val shopName: String = "My Shop",
    val shopPhone: String = "",
    val shopAddress: String = "",
    val currencySymbol: String = "$",
    val taxPercent: Double = 0.0,
    val receiptFooter: String = "Thank you for shopping with us!",
    val nextBillNumber: Long = 1001L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Customer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val notes: String = "",
    val totalSpent: Double = 0.0,
    val totalDue: Double = 0.0,
    val billCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ShopProduct(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val sku: String = "",
    val category: String = "",
    val unitPrice: Double = 0.0,
    val unitLabel: String = "pcs",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Bill(
    @DocumentId val id: String = "",
    val userId: String = "",
    val billNumber: String = "",
    val customerId: String = "",
    val customerName: String = "Walk-in Customer",
    val customerPhone: String = "",
    val subtotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val taxPercent: Double = 0.0,
    val taxAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val dueAmount: Double = 0.0,
    val paymentMethod: String = PaymentMethod.CASH.name,
    val status: String = BillStatus.PAID.name,
    val itemCount: Long = 1L,
    val itemsSummary: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedStatus(): BillStatus = BillStatus.fromRaw(status)
    fun parsedPaymentMethod(): PaymentMethod = PaymentMethod.fromRaw(paymentMethod)
}

@Keep
@IgnoreExtraProperties
data class BillLineItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val billId: String = "",
    val productId: String = "",
    val name: String = "",
    val quantity: Double = 1.0,
    val unitLabel: String = "pcs",
    val unitPrice: Double = 0.0,
    val lineTotal: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DraftLineItem(
    val localKey: String,
    val productId: String = "",
    val name: String,
    val quantity: Double,
    val unitLabel: String,
    val unitPrice: Double
) {
    val lineTotal: Double
        get() = (quantity * unitPrice).coerceAtLeast(0.0)
}
