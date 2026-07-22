package com.neo.chevere.ui.invoices

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.neo.chevere.core.BaseViewModel
import com.neo.chevere.data.datasource.local.InvoiceDao
import com.neo.chevere.data.datasource.local.InvoiceEntity
import com.neo.chevere.data.datasource.local.InvoiceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InvoicesViewModel @Inject constructor(
    application: Application,
    private val invoiceDao: InvoiceDao
) : BaseViewModel<InvoicesState, InvoicesIntent, InvoicesEffect>(application, InvoicesState()) {

    init {
        viewModelScope.launch {
            invoiceDao.getAllInvoicesFlow().collectLatest { invoiceList ->
                setState { copy(invoices = invoiceList) }
            }
        }
    }

    override suspend fun handleIntent(intent: InvoicesIntent) {
        when (intent) {
            is InvoicesIntent.AddInvoice -> {
                if (intent.vendor.isBlank()) {
                    sendEffect { InvoicesEffect.ShowToast("Vendor name cannot be empty") }
                    return
                }
                val invoice = InvoiceEntity(
                    vendor = intent.vendor,
                    invoiceNumber = intent.number,
                    date = intent.date,
                    totalAmount = intent.amount,
                    currency = intent.currency.ifBlank { "USD" },
                    items = intent.items,
                    status = InvoiceStatus.PENDING,
                    imageUri = null
                )
                invoiceDao.insertInvoice(invoice)
            }

            is InvoicesIntent.UpdateInvoiceStatus -> {
                val invoice = invoiceDao.getInvoiceById(intent.id)
                if (invoice != null) {
                    invoiceDao.updateInvoice(invoice.copy(status = intent.status))
                }
            }

            is InvoicesIntent.DeleteInvoice -> {
                invoiceDao.deleteInvoice(intent.id)
            }
        }
    }
}
