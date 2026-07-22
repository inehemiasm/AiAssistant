package com.neo.chevere.ui.invoices

import com.neo.chevere.core.UiEffect
import com.neo.chevere.core.UiIntent
import com.neo.chevere.core.UiState
import com.neo.chevere.data.datasource.local.InvoiceEntity
import com.neo.chevere.data.datasource.local.InvoiceStatus

data class InvoicesState(
    val invoices: List<InvoiceEntity> = emptyList()
) : UiState

sealed class InvoicesIntent : UiIntent {
    data class AddInvoice(
        val vendor: String,
        val amount: Double,
        val currency: String,
        val number: String?,
        val date: String?,
        val items: String?
    ) : InvoicesIntent()

    data class UpdateInvoiceStatus(val id: Int, val status: InvoiceStatus) : InvoicesIntent()
    data class DeleteInvoice(val id: Int) : InvoicesIntent()
}

sealed class InvoicesEffect : UiEffect {
    data class ShowToast(val message: String) : InvoicesEffect()
}
