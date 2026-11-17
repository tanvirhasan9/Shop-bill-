package com.example.data

import android.content.Context
import com.example.R
import com.example.model.Bill
import com.example.model.BillLineItem
import com.example.model.BillStatus
import com.example.model.Customer
import com.example.model.DraftLineItem
import com.example.model.PaymentMethod
import com.example.model.ShopProduct
import com.example.model.ShopProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToLong

class ShopRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDocRef(uid: String) = db.collection("users").document(uid)
    private fun customersCol(uid: String) = userDocRef(uid).collection("customers")
    private fun productsCol(uid: String) = userDocRef(uid).collection("products")
    private fun billsCol(uid: String) = userDocRef(uid).collection("bills")

    // --- Real-Time Observations ---

    fun observeShopProfile(userId: String): Flow<ShopProfile> = flow {
        val docRef = userDocRef(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        ShopProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: ShopProfile(id = userId, userId = userId)
                }
                .catch { error -> handleStreamError(error, OperationType.GET, docRef.path, userId) }
        )
    }

    fun observeCustomers(userId: String): Flow<List<Customer>> = flow {
        val colRef = customersCol(userId)
        emitAll(
            colRef.orderBy("name", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Customer::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, colRef.path, userId) }
        )
    }

    fun observeProducts(userId: String): Flow<List<ShopProduct>> = flow {
        val colRef = productsCol(userId)
        emitAll(
            colRef.orderBy("name", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ShopProduct::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, colRef.path, userId) }
        )
    }

    fun observeBills(userId: String): Flow<List<Bill>> = flow {
        val colRef = billsCol(userId)
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Bill::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, colRef.path, userId) }
        )
    }

    fun observeBill(userId: String, billId: String): Flow<Bill?> = flow {
        val docRef = billsCol(userId).document(billId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            Bill::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error -> handleStreamError(error, OperationType.GET, docRef.path, userId) }
        )
    }

    fun observeBillItems(userId: String, billId: String): Flow<List<BillLineItem>> = flow {
        val colRef = billsCol(userId).document(billId).collection("items")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BillLineItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, colRef.path, userId) }
        )
    }

    private fun handleStreamError(
        error: Throwable,
        op: OperationType,
        path: String,
        expectedUid: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    // --- Shop Profile Mutations ---

    suspend fun ensureShopProfile(defaultShopName: String = "My Shop"): Result<Unit> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            val docRef = userDocRef(uid)
            docPath = docRef.path
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val safeName = defaultShopName.trim().ifBlank { "My Shop" }.take(80)
                val payload = mapOf(
                    "userId" to uid,
                    "shopName" to safeName,
                    "shopPhone" to "",
                    "shopAddress" to "",
                    "currencySymbol" to "$",
                    "taxPercent" to 0.0,
                    "receiptFooter" to "Thank you for shopping with us!",
                    "nextBillNumber" to 1001L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateShopProfile(
        shopName: String,
        shopPhone: String,
        shopAddress: String,
        currencySymbol: String,
        taxPercent: Double,
        receiptFooter: String
    ): Result<Unit> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            val docRef = userDocRef(uid)
            docPath = docRef.path
            val cleanName = shopName.trim().take(80)
            require(cleanName.isNotEmpty()) { "Shop name cannot be empty" }
            val cleanCurrency = currencySymbol.trim().ifBlank { "$" }.take(6)
            val cleanTax = taxPercent.coerceIn(0.0, 100.0)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "shopName" to cleanName,
                        "shopPhone" to shopPhone.trim().take(30),
                        "shopAddress" to shopAddress.trim().take(150),
                        "currencySymbol" to cleanCurrency,
                        "taxPercent" to cleanTax,
                        "receiptFooter" to receiptFooter.trim().take(120),
                        "nextBillNumber" to 1001L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "shopName" to cleanName,
                        "shopPhone" to shopPhone.trim().take(30),
                        "shopAddress" to shopAddress.trim().take(150),
                        "currencySymbol" to cleanCurrency,
                        "taxPercent" to cleanTax,
                        "receiptFooter" to receiptFooter.trim().take(120),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- Customer CRUD ---

    suspend fun saveCustomer(
        customerId: String?,
        name: String,
        phone: String,
        email: String,
        address: String,
        notes: String
    ): Result<String> {
        var docPath = "users/unknown/customers"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Customer name is required" }

            if (customerId.isNullOrBlank()) {
                val docRef = customersCol(uid).document()
                docPath = docRef.path
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to cleanName,
                        "phone" to phone.trim().take(30),
                        "email" to email.trim().take(100),
                        "address" to address.trim().take(150),
                        "notes" to notes.trim().take(200),
                        "totalSpent" to 0.0,
                        "totalDue" to 0.0,
                        "billCount" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(docRef.id)
            } else {
                val docRef = customersCol(uid).document(customerId)
                docPath = docRef.path
                docRef.update(
                    mapOf(
                        "name" to cleanName,
                        "phone" to phone.trim().take(30),
                        "email" to email.trim().take(100),
                        "address" to address.trim().take(150),
                        "notes" to notes.trim().take(200),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(customerId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomer(customerId: String): Result<Unit> {
        var docPath = "users/unknown/customers/$customerId"
        return try {
            val uid = requireUserId()
            require(customerId.isNotBlank()) { "Customer ID is required" }
            val docRef = customersCol(uid).document(customerId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Product / Catalog CRUD ---

    suspend fun saveProduct(
        productId: String?,
        name: String,
        sku: String,
        category: String,
        unitPrice: Double,
        unitLabel: String
    ): Result<String> {
        var docPath = "users/unknown/products"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Item name is required" }
            val cleanPrice = roundCurrency(unitPrice.coerceAtLeast(0.0))
            val cleanUnit = unitLabel.trim().ifBlank { "pcs" }.take(20)

            if (productId.isNullOrBlank()) {
                val docRef = productsCol(uid).document()
                docPath = docRef.path
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to cleanName,
                        "sku" to sku.trim().take(32),
                        "category" to category.trim().take(40),
                        "unitPrice" to cleanPrice,
                        "unitLabel" to cleanUnit,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(docRef.id)
            } else {
                val docRef = productsCol(uid).document(productId)
                docPath = docRef.path
                docRef.update(
                    mapOf(
                        "name" to cleanName,
                        "sku" to sku.trim().take(32),
                        "category" to category.trim().take(40),
                        "unitPrice" to cleanPrice,
                        "unitLabel" to cleanUnit,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(productId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(productId: String): Result<Unit> {
        var docPath = "users/unknown/products/$productId"
        return try {
            val uid = requireUserId()
            require(productId.isNotBlank()) { "Product ID is required" }
            val docRef = productsCol(uid).document(productId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Bill & Receipt Creation / Payment / Void ---

    suspend fun createBill(
        profile: ShopProfile,
        customer: Customer?,
        walkInName: String,
        walkInPhone: String,
        items: List<DraftLineItem>,
        discountAmount: Double,
        taxPercent: Double,
        paidAmount: Double,
        paymentMethod: PaymentMethod,
        notes: String
    ): Result<String> {
        var docPath = "users/unknown/bills"
        return try {
            val uid = requireUserId()
            require(items.isNotEmpty()) { "Add at least one item to create a bill." }

            // Ensure shop profile doc exists before advancing counter in batch
            val profileRef = userDocRef(uid)
            val profileSnap = profileRef.get().await()
            val currentNextNumber = if (profileSnap.exists()) {
                (profileSnap.getLong("nextBillNumber") ?: profile.nextBillNumber).coerceAtLeast(1L)
            } else {
                profile.nextBillNumber.coerceAtLeast(1001L)
            }

            val billRef = billsCol(uid).document()
            docPath = billRef.path

            val subtotal = roundCurrency(items.sumOf { it.lineTotal })
            val cleanDiscount = roundCurrency(discountAmount.coerceIn(0.0, subtotal))
            val taxableBase = (subtotal - cleanDiscount).coerceAtLeast(0.0)
            val cleanTaxPct = taxPercent.coerceIn(0.0, 100.0)
            val taxAmount = roundCurrency(taxableBase * (cleanTaxPct / 100.0))
            val totalAmount = roundCurrency(taxableBase + taxAmount)
            val cleanPaid = roundCurrency(paidAmount.coerceIn(0.0, totalAmount))
            val dueAmount = roundCurrency((totalAmount - cleanPaid).coerceAtLeast(0.0))

            val status = when {
                dueAmount <= 0.009 -> BillStatus.PAID
                cleanPaid <= 0.009 -> BillStatus.UNPAID
                else -> BillStatus.PARTIAL
            }

            val billNumber = "INV-$currentNextNumber"
            val customerId = customer?.id?.take(128) ?: ""
            val customerName = (customer?.name ?: walkInName.ifBlank { "Walk-in Customer" })
                .trim()
                .ifBlank { "Walk-in Customer" }
                .take(80)
            val customerPhone = (customer?.phone ?: walkInPhone).trim().take(30)

            val summaryFirst = items.take(2).joinToString(", ") {
                val qtyStr = if (it.quantity % 1.0 == 0.0) it.quantity.toInt().toString() else String.format("%.1f", it.quantity)
                "${qtyStr}x ${it.name.trim()}"
            }
            val summary = if (items.size > 2) {
                "$summaryFirst +${items.size - 2} more"
            } else {
                summaryFirst
            }.take(200)

            val batch = db.batch()

            // 1. Create or advance shop profile
            if (!profileSnap.exists()) {
                batch.set(
                    profileRef,
                    mapOf(
                        "userId" to uid,
                        "shopName" to profile.shopName.ifBlank { "My Shop" }.take(80),
                        "shopPhone" to profile.shopPhone.take(30),
                        "shopAddress" to profile.shopAddress.take(150),
                        "currencySymbol" to profile.currencySymbol.ifBlank { "$" }.take(6),
                        "taxPercent" to profile.taxPercent.coerceIn(0.0, 100.0),
                        "receiptFooter" to profile.receiptFooter.take(120),
                        "nextBillNumber" to currentNextNumber + 1L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.update(
                    profileRef,
                    mapOf(
                        "nextBillNumber" to currentNextNumber + 1L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            // 2. Create Bill header
            batch.set(
                billRef,
                mapOf(
                    "userId" to uid,
                    "billNumber" to billNumber,
                    "customerId" to customerId,
                    "customerName" to customerName,
                    "customerPhone" to customerPhone,
                    "subtotal" to subtotal,
                    "discountAmount" to cleanDiscount,
                    "taxPercent" to cleanTaxPct,
                    "taxAmount" to taxAmount,
                    "totalAmount" to totalAmount,
                    "paidAmount" to cleanPaid,
                    "dueAmount" to dueAmount,
                    "paymentMethod" to paymentMethod.name,
                    "status" to status.name,
                    "itemCount" to items.size.toLong(),
                    "itemsSummary" to summary,
                    "notes" to notes.trim().take(200),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            // 3. Create each BillLineItem in subcollection
            items.forEach { draft ->
                val itemRef = billRef.collection("items").document()
                val cleanItemName = draft.name.trim().ifBlank { "Item" }.take(80)
                val cleanQty = draft.quantity.coerceAtLeast(0.01)
                val cleanUnitPrice = roundCurrency(draft.unitPrice.coerceAtLeast(0.0))
                val cleanLineTotal = roundCurrency(cleanQty * cleanUnitPrice)
                batch.set(
                    itemRef,
                    mapOf(
                        "userId" to uid,
                        "billId" to billRef.id,
                        "productId" to draft.productId.take(128),
                        "name" to cleanItemName,
                        "quantity" to cleanQty,
                        "unitLabel" to draft.unitLabel.trim().ifBlank { "pcs" }.take(20),
                        "unitPrice" to cleanUnitPrice,
                        "lineTotal" to cleanLineTotal,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            // 4. Update linked Customer totals if applicable
            if (customer != null && customer.id.isNotBlank()) {
                val custRef = customersCol(uid).document(customer.id)
                batch.update(
                    custRef,
                    mapOf(
                        "totalSpent" to roundCurrency((customer.totalSpent + totalAmount).coerceAtLeast(0.0)),
                        "totalDue" to roundCurrency((customer.totalDue + dueAmount).coerceAtLeast(0.0)),
                        "billCount" to (customer.billCount + 1L).coerceAtLeast(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(billRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordBillPayment(
        bill: Bill,
        customer: Customer?,
        additionalPayment: Double,
        paymentMethod: PaymentMethod
    ): Result<Unit> {
        var docPath = "users/unknown/bills/${bill.id}"
        return try {
            val uid = requireUserId()
            require(bill.id.isNotBlank()) { "Bill ID cannot be blank" }
            require(bill.parsedStatus() != BillStatus.VOID) { "Cannot record payment on a voided bill" }
            require(additionalPayment > 0.0) { "Payment amount must be greater than zero" }

            val billRef = billsCol(uid).document(bill.id)
            docPath = billRef.path

            val appliedPayment = roundCurrency(additionalPayment.coerceIn(0.0, bill.dueAmount))
            val newPaid = roundCurrency((bill.paidAmount + appliedPayment).coerceIn(0.0, bill.totalAmount))
            val newDue = roundCurrency((bill.totalAmount - newPaid).coerceAtLeast(0.0))
            val newStatus = when {
                newDue <= 0.009 -> BillStatus.PAID
                newPaid <= 0.009 -> BillStatus.UNPAID
                else -> BillStatus.PARTIAL
            }

            val batch = db.batch()
            batch.update(
                billRef,
                mapOf(
                    "paidAmount" to newPaid,
                    "dueAmount" to newDue,
                    "status" to newStatus.name,
                    "paymentMethod" to paymentMethod.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            if (bill.customerId.isNotBlank() && customer != null) {
                val custRef = customersCol(uid).document(bill.customerId)
                batch.update(
                    custRef,
                    mapOf(
                        "totalSpent" to roundCurrency(customer.totalSpent.coerceAtLeast(0.0)),
                        "totalDue" to roundCurrency((customer.totalDue - appliedPayment).coerceAtLeast(0.0)),
                        "billCount" to customer.billCount.coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun voidBill(bill: Bill, customer: Customer?): Result<Unit> {
        var docPath = "users/unknown/bills/${bill.id}"
        return try {
            val uid = requireUserId()
            require(bill.id.isNotBlank()) { "Bill ID cannot be blank" }
            require(bill.parsedStatus() != BillStatus.VOID) { "Bill is already voided" }

            val billRef = billsCol(uid).document(bill.id)
            docPath = billRef.path

            val batch = db.batch()
            batch.update(
                billRef,
                mapOf(
                    "status" to BillStatus.VOID.name,
                    "dueAmount" to 0.0,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            if (bill.customerId.isNotBlank() && customer != null) {
                val custRef = customersCol(uid).document(bill.customerId)
                batch.update(
                    custRef,
                    mapOf(
                        "totalSpent" to roundCurrency((customer.totalSpent - bill.totalAmount).coerceAtLeast(0.0)),
                        "totalDue" to roundCurrency((customer.totalDue - bill.dueAmount).coerceAtLeast(0.0)),
                        "billCount" to (customer.billCount - 1L).coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteBill(billId: String): Result<Unit> {
        var docPath = "users/unknown/bills/$billId"
        return try {
            val uid = requireUserId()
            require(billId.isNotBlank()) { "Bill ID cannot be blank" }
            val billRef = billsCol(uid).document(billId)
            docPath = billRef.path
            val itemDocs = billRef.collection("items").get().await()
            val batch = db.batch()
            itemDocs.documents.forEach { batch.delete(it.reference) }
            batch.delete(billRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private fun roundCurrency(value: Double): Double =
        (value * 100.0).roundToLong() / 100.0
}
