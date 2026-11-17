package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import androidx.core.content.FileProvider
import com.example.model.Bill
import com.example.model.BillLineItem
import com.example.model.BillStatus
import com.example.model.ShopProfile
import com.google.firebase.Timestamp
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MONEY_FORMAT = DecimalFormat("#,##0.00")
private val QTY_FORMAT = DecimalFormat("#,##0.##")
private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
private val SHORT_DATE_FORMAT = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

fun formatMoney(amount: Double, currencySymbol: String): String {
    return "$currencySymbol${MONEY_FORMAT.format(amount)}"
}

fun formatQty(qty: Double): String {
    return QTY_FORMAT.format(qty)
}

fun formatBillTimestamp(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: Date()
    return DATE_FORMAT.format(date)
}

fun formatShortDate(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: Date()
    return SHORT_DATE_FORMAT.format(date)
}

fun buildPlainTextReceipt(
    profile: ShopProfile,
    bill: Bill,
    items: List<BillLineItem>
): String {
    val currency = profile.currencySymbol.ifBlank { "$" }
    val sb = StringBuilder()
    sb.appendLine("================================")
    sb.appendLine(profile.shopName.ifBlank { "My Shop" }.uppercase(Locale.getDefault()))
    if (profile.shopAddress.isNotBlank()) sb.appendLine(profile.shopAddress)
    if (profile.shopPhone.isNotBlank()) sb.appendLine("Tel: ${profile.shopPhone}")
    sb.appendLine("--------------------------------")
    sb.appendLine("Receipt #: ${bill.billNumber}")
    sb.appendLine("Date:      ${formatBillTimestamp(bill.createdAt)}")
    sb.appendLine("Customer:  ${bill.customerName}")
    if (bill.customerPhone.isNotBlank()) sb.appendLine("Phone:     ${bill.customerPhone}")
    sb.appendLine("Status:    ${bill.parsedStatus().label} (${bill.parsedPaymentMethod().label})")
    sb.appendLine("--------------------------------")
    items.forEach { item ->
        sb.appendLine(item.name)
        sb.appendLine(
            "  ${formatQty(item.quantity)} ${item.unitLabel} x ${formatMoney(item.unitPrice, currency)} = ${formatMoney(item.lineTotal, currency)}"
        )
    }
    sb.appendLine("--------------------------------")
    sb.appendLine("Subtotal:  ${formatMoney(bill.subtotal, currency)}")
    if (bill.discountAmount > 0.0) {
        sb.appendLine("Discount: -${formatMoney(bill.discountAmount, currency)}")
    }
    if (bill.taxAmount > 0.0 || bill.taxPercent > 0.0) {
        sb.appendLine("Tax (${formatQty(bill.taxPercent)}%): ${formatMoney(bill.taxAmount, currency)}")
    }
    sb.appendLine("TOTAL:     ${formatMoney(bill.totalAmount, currency)}")
    sb.appendLine("Paid:      ${formatMoney(bill.paidAmount, currency)}")
    if (bill.dueAmount > 0.0) {
        sb.appendLine("Bal Due:   ${formatMoney(bill.dueAmount, currency)}")
    }
    if (bill.notes.isNotBlank()) {
        sb.appendLine("Note: ${bill.notes}")
    }
    sb.appendLine("================================")
    if (profile.receiptFooter.isNotBlank()) {
        sb.appendLine(profile.receiptFooter)
    }
    return sb.toString()
}

fun shareTextReceipt(context: Context, profile: ShopProfile, bill: Bill, items: List<BillLineItem>) {
    val text = buildPlainTextReceipt(profile, bill, items)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "${profile.shopName} Receipt ${bill.billNumber}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Receipt Text"))
}

fun shareReceiptImage(context: Context, profile: ShopProfile, bill: Bill, items: List<BillLineItem>) {
    val bitmap = renderReceiptBitmap(profile, bill, items)
    val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val safeBillNum = bill.billNumber.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val outFile = File(dir, "receipt_${safeBillNum}.png")
    FileOutputStream(outFile).use { fos ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
    }
    bitmap.recycle()

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        outFile
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "${profile.shopName} Receipt ${bill.billNumber}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Receipt Image"))
}

fun renderReceiptBitmap(
    profile: ShopProfile,
    bill: Bill,
    items: List<BillLineItem>
): Bitmap {
    val width = 800
    val padding = 56f
    val rowHeight = 46f
    val extraRows = (if (bill.discountAmount > 0) 1 else 0) +
        (if (bill.taxAmount > 0 || bill.taxPercent > 0) 1 else 0) +
        (if (bill.dueAmount > 0) 1 else 0) +
        (if (bill.notes.isNotBlank()) 2 else 0)
    val height = (540 + items.size * 68 + extraRows * 40).coerceAtLeast(700)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.parseColor("#FFFFFF"))

    drawReceiptOnCanvas(canvas, width.toFloat(), padding, profile, bill, items, rowHeight)
    return bitmap
}

fun printReceiptPdf(
    context: Context,
    profile: ShopProfile,
    bill: Bill,
    items: List<BillLineItem>
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "${profile.shopName} - ${bill.billNumber}"

    val adapter = object : PrintDocumentAdapter() {
        private var pdfDocument: PrintedPdfDocument? = null

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            pdfDocument = PrintedPdfDocument(context, newAttributes)
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder("${bill.billNumber}.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            val doc = pdfDocument ?: return
            val page = doc.startPage(0)
            if (cancellationSignal?.isCanceled == true) {
                callback.onWriteCancelled()
                doc.close()
                pdfDocument = null
                return
            }
            val canvas = page.canvas
            val pageWidth = canvas.width.toFloat()
            drawReceiptOnCanvas(canvas, pageWidth, 42f, profile, bill, items, 26f, scale = 0.72f)
            doc.finishPage(page)
            try {
                FileOutputStream(destination.fileDescriptor).use { out ->
                    doc.writeTo(out)
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            } finally {
                doc.close()
                pdfDocument = null
            }
        }
    }
    printManager.print(jobName, adapter, PrintAttributes.Builder().build())
}

private fun drawReceiptOnCanvas(
    canvas: Canvas,
    width: Float,
    padding: Float,
    profile: ShopProfile,
    bill: Bill,
    items: List<BillLineItem>,
    rowHeight: Float,
    scale: Float = 1f
) {
    val currency = profile.currencySymbol.ifBlank { "$" }
    val ink = Color.parseColor("#181D1B")
    val muted = Color.parseColor("#5A635E")
    val emerald = Color.parseColor("#0F5C4D")

    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = emerald
        textSize = 36f * scale
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        textSize = 21f * scale
        typeface = Typeface.SANS_SERIF
        textAlign = Paint.Align.CENTER
    }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        textSize = 22f * scale
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 23f * scale
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    val rightValuePaint = Paint(valuePaint).apply {
        textAlign = Paint.Align.RIGHT
    }
    val boldLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 26f * scale
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val boldRightPaint = Paint(boldLeftPaint).apply {
        textAlign = Paint.Align.RIGHT
    }
    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D5CEC0")
        strokeWidth = 2f * scale
    }

    var y = padding + 24f * scale
    val centerX = width / 2f
    val rightX = width - padding

    canvas.drawText(profile.shopName.ifBlank { "My Shop" }.uppercase(Locale.getDefault()), centerX, y, headerPaint)
    y += 30f * scale
    if (profile.shopAddress.isNotBlank()) {
        canvas.drawText(profile.shopAddress.take(48), centerX, y, subPaint)
        y += 26f * scale
    }
    if (profile.shopPhone.isNotBlank()) {
        canvas.drawText("Tel: ${profile.shopPhone}", centerX, y, subPaint)
        y += 26f * scale
    }
    y += 14f * scale
    canvas.drawLine(padding, y, rightX, y, dividerPaint)
    y += 34f * scale

    canvas.drawText("Receipt: ${bill.billNumber}", padding, y, valuePaint)
    canvas.drawText(bill.parsedStatus().label.uppercase(Locale.getDefault()), rightX, y, boldRightPaint)
    y += 30f * scale
    canvas.drawText("Date: ${formatBillTimestamp(bill.createdAt)}", padding, y, labelPaint)
    y += 28f * scale
    canvas.drawText("Customer: ${bill.customerName.take(30)}", padding, y, valuePaint)
    y += 28f * scale
    if (bill.customerPhone.isNotBlank()) {
        canvas.drawText("Phone: ${bill.customerPhone}", padding, y, labelPaint)
        y += 28f * scale
    }

    y += 10f * scale
    canvas.drawLine(padding, y, rightX, y, dividerPaint)
    y += 32f * scale

    items.forEach { item ->
        canvas.drawText(item.name.take(36), padding, y, valuePaint)
        canvas.drawText(formatMoney(item.lineTotal, currency), rightX, y, rightValuePaint)
        y += 26f * scale
        canvas.drawText(
            "  ${formatQty(item.quantity)} ${item.unitLabel} @ ${formatMoney(item.unitPrice, currency)}",
            padding,
            y,
            labelPaint
        )
        y += 34f * scale
    }

    canvas.drawLine(padding, y, rightX, y, dividerPaint)
    y += 34f * scale

    canvas.drawText("Subtotal", padding, y, labelPaint)
    canvas.drawText(formatMoney(bill.subtotal, currency), rightX, y, rightValuePaint)
    y += rowHeight * 0.72f

    if (bill.discountAmount > 0.0) {
        canvas.drawText("Discount", padding, y, labelPaint)
        canvas.drawText("-${formatMoney(bill.discountAmount, currency)}", rightX, y, rightValuePaint)
        y += rowHeight * 0.72f
    }

    if (bill.taxAmount > 0.0 || bill.taxPercent > 0.0) {
        canvas.drawText("Tax (${formatQty(bill.taxPercent)}%)", padding, y, labelPaint)
        canvas.drawText(formatMoney(bill.taxAmount, currency), rightX, y, rightValuePaint)
        y += rowHeight * 0.72f
    }

    y += 6f * scale
    canvas.drawText("GRAND TOTAL", padding, y, boldLeftPaint)
    canvas.drawText(formatMoney(bill.totalAmount, currency), rightX, y, boldRightPaint)
    y += rowHeight * 0.85f

    canvas.drawText("Paid (${bill.parsedPaymentMethod().label})", padding, y, valuePaint)
    canvas.drawText(formatMoney(bill.paidAmount, currency), rightX, y, rightValuePaint)
    y += rowHeight * 0.72f

    if (bill.dueAmount > 0.0 && bill.parsedStatus() != BillStatus.VOID) {
        canvas.drawText("Balance Due", padding, y, boldLeftPaint)
        canvas.drawText(formatMoney(bill.dueAmount, currency), rightX, y, boldRightPaint)
        y += rowHeight * 0.8f
    }

    if (bill.notes.isNotBlank()) {
        y += 10f * scale
        canvas.drawText("Note: ${bill.notes.take(44)}", padding, y, labelPaint)
        y += 30f * scale
    }

    y += 18f * scale
    canvas.drawLine(padding, y, rightX, y, dividerPaint)
    y += 34f * scale
    if (profile.receiptFooter.isNotBlank()) {
        canvas.drawText(profile.receiptFooter.take(54), centerX, y, subPaint)
    }
}
