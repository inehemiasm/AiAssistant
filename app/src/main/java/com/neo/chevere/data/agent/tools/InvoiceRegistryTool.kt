package com.neo.chevere.data.agent.tools

import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import com.neo.chevere.data.datasource.local.ConversationHistoryDao
import com.neo.chevere.data.datasource.local.InvoiceDao
import com.neo.chevere.data.datasource.local.InvoiceEntity
import com.neo.chevere.data.datasource.local.InvoiceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class InvoiceRegistryTool @Inject constructor(
    private val invoiceDao: InvoiceDao,
    private val conversationHistoryDao: ConversationHistoryDao
) : AgentTool {
    override val name: String = "invoice_registry"
    override val description: String =
        "Manages local invoices or receipts. Use to import/save scanned invoices, list all invoices, update status, or delete. If the current user request includes a scanned/attached invoice image, do NOT require the user to provide the image_uri parameter; the tool will automatically resolve the active image attachment."
    override val inputSchema: String =
        "action: One of 'import', 'list', 'update_status', 'delete'. vendor: Vendor or merchant name (required for 'import'). total_amount: Total bill amount (numeric, required for 'import'). date: Invoice date YYYY-MM-DD (optional). invoice_number: Invoice reference number (optional). currency: Currency code, e.g. USD, EUR (default USD). items: Brief summary of line items. status: One of 'paid', 'pending', 'overdue' (default pending). image_uri: Local file URI/path of the invoice image (optional). id: The numeric invoice ID (required for 'update_status' or 'delete')."

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val action = args["action"]?.trim()?.lowercase() ?: return@withContext ToolResult.Error("Missing 'action' argument")

        when (action) {
            "import" -> {
                val vendor = args["vendor"]?.trim() ?: return@withContext ToolResult.Error("Missing 'vendor' argument for import action")
                val totalAmountStr = args["total_amount"]?.trim() ?: return@withContext ToolResult.Error("Missing 'total_amount' argument for import action")
                val totalAmount = totalAmountStr.toDoubleOrNull() ?: return@withContext ToolResult.Error("Invalid 'total_amount' format (must be numeric)")
                
                val invoiceNumber = args["invoice_number"]?.trim()
                val date = args["date"]?.trim()
                val currency = args["currency"]?.trim() ?: "USD"
                val items = args["items"]?.trim()
                
                // Automatically fetch last active image URI from chat history if none provided
                var imageUri = args["image_uri"]?.trim()
                if (imageUri.isNullOrBlank()) {
                    imageUri = conversationHistoryDao.getLastImageUri()
                }
                
                val statusStr = args["status"]?.trim()?.uppercase()
                val status = when {
                    statusStr != null -> {
                        try {
                            InvoiceStatus.valueOf(statusStr)
                        } catch (e: Exception) {
                            InvoiceStatus.PENDING
                        }
                    }
                    // Auto-detect: zero balance means the invoice is paid
                    totalAmount == 0.0 -> InvoiceStatus.PAID
                    else -> InvoiceStatus.PENDING
                }

                val invoice = InvoiceEntity(
                    vendor = vendor,
                    invoiceNumber = invoiceNumber,
                    date = date,
                    totalAmount = totalAmount,
                    currency = currency,
                    items = items,
                    status = status,
                    imageUri = imageUri
                )
                val newId = invoiceDao.insertInvoice(invoice)
                ToolResult.Success("Invoice imported successfully with ID: $newId from vendor: '$vendor' for amount: $currency $totalAmount.")
            }

            "list" -> {
                val invoices = invoiceDao.getAllInvoices()
                if (invoices.isEmpty()) {
                    ToolResult.Success("No invoices found in the list.")
                } else {
                    val summary = invoices.joinToString("\n") { inv ->
                        "[ID: ${inv.id}] [${inv.status}] ${inv.vendor} - ${inv.currency} ${inv.totalAmount}${if (!inv.invoiceNumber.isNullOrBlank()) " (Inv #: ${inv.invoiceNumber})" else ""}${if (!inv.date.isNullOrBlank()) " on ${inv.date}" else ""}"
                    }
                    ToolResult.Success("Current invoices:\n$summary")
                }
            }

            "update_status" -> {
                val idStr = args["id"]?.trim() ?: return@withContext ToolResult.Error("Missing invoice 'id' argument for update_status action")
                val id = idStr.toIntOrNull() ?: return@withContext ToolResult.Error("Invalid 'id' format (must be numeric)")
                val invoice = invoiceDao.getInvoiceById(id) ?: return@withContext ToolResult.Error("Invoice with ID $id not found")

                val statusStr = args["status"]?.trim()?.uppercase() ?: return@withContext ToolResult.Error("Missing 'status' argument for update_status action")
                val newStatus = try {
                    InvoiceStatus.valueOf(statusStr)
                } catch (e: Exception) {
                    return@withContext ToolResult.Error("Invalid 'status' argument (must be 'paid', 'pending', or 'overdue')")
                }

                val updatedInvoice = invoice.copy(status = newStatus)
                invoiceDao.updateInvoice(updatedInvoice)
                ToolResult.Success("Invoice $id updated status successfully to: $newStatus.")
            }

            "delete" -> {
                val idStr = args["id"]?.trim() ?: return@withContext ToolResult.Error("Missing invoice 'id' argument for delete action")
                val id = idStr.toIntOrNull() ?: return@withContext ToolResult.Error("Invalid 'id' format (must be numeric)")
                val invoice = invoiceDao.getInvoiceById(id) ?: return@withContext ToolResult.Error("Invoice with ID $id not found")

                invoiceDao.deleteInvoice(id)
                ToolResult.Success("Invoice $id ('${invoice.vendor}') deleted successfully.")
            }

            else -> ToolResult.Error("Unsupported action '$action'. Must be one of 'import', 'list', 'update_status', 'delete'.")
        }
    }
}
