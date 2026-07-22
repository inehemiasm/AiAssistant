package com.neo.chevere.data.datasource.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class InvoiceStatus {
    PAID, PENDING, OVERDUE
}

@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vendor: String,
    val invoiceNumber: String?,
    val date: String?, // Format: YYYY-MM-DD
    val totalAmount: Double?,
    val currency: String = "USD",
    val items: String?, // Short summary of items
    val status: InvoiceStatus = InvoiceStatus.PENDING,
    val imageUri: String?, // Path or Uri of the scanned invoice image
    val rawText: String? = null // Extracted raw OCR/multimodal text
)
