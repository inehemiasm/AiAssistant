package com.neo.chevere.ui.invoices

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.neo.chevere.R
import com.neo.chevere.data.datasource.local.InvoiceEntity
import com.neo.chevere.data.datasource.local.InvoiceStatus
import com.neo.chevere.ui.chat.components.FullscreenImagePreviewDialog
import com.neo.chevere.ui.common.ChevereHaptic
import com.neo.chevere.ui.common.performChevereHaptic
import com.neo.chevere.ui.designsystem.Typography

@Composable
fun InvoicesScreen(
    viewModel: InvoicesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    InvoicesContent(
        state = state,
        effects = viewModel.effect,
        onIntent = { viewModel.onIntent(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesContent(
    state: InvoicesState,
    effects: kotlinx.coroutines.flow.Flow<InvoicesEffect>,
    onIntent: (InvoicesIntent) -> Unit
) {
    val context = LocalContext.current
    val hapticView = LocalView.current
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf<InvoiceStatus?>(null) }
    var activePreviewImageUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is InvoicesEffect.ShowToast -> {
                    hapticView.performChevereHaptic(ChevereHaptic.Warning)
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val glassBackground = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f),
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.86f)
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.invoices_label).uppercase(),
                        style = Typography.titleLarge,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    hapticView.performChevereHaptic(ChevereHaptic.Selection)
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Invoice", modifier = Modifier.size(24.dp))
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(glassBackground)
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search & Filter Header Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by Vendor...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = selectedStatusFilter == null,
                            onClick = {
                                hapticView.performChevereHaptic(ChevereHaptic.Selection)
                                selectedStatusFilter = null
                            },
                            label = { Text("ALL") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        InvoiceStatus.entries.forEach { status ->
                            FilterChip(
                                selected = selectedStatusFilter == status,
                                onClick = {
                                    hapticView.performChevereHaptic(ChevereHaptic.Selection)
                                    selectedStatusFilter = status
                                },
                                label = { Text(status.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }

                val filteredInvoices = state.invoices.filter {
                    val matchesQuery = it.vendor.contains(searchQuery, ignoreCase = true) ||
                            (it.items?.contains(searchQuery, ignoreCase = true) == true)
                    val matchesStatus = selectedStatusFilter == null || it.status == selectedStatusFilter
                    matchesQuery && matchesStatus
                }

                if (filteredInvoices.isEmpty()) {
                    EmptyInvoicesState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredInvoices, key = { it.id }) { invoice ->
                            InvoiceRowItem(
                                invoice = invoice,
                                onUpdateStatus = { status ->
                                    hapticView.performChevereHaptic(ChevereHaptic.Success)
                                    onIntent(InvoicesIntent.UpdateInvoiceStatus(invoice.id, status))
                                },
                                onDelete = {
                                    hapticView.performChevereHaptic(ChevereHaptic.Warning)
                                    onIntent(InvoicesIntent.DeleteInvoice(invoice.id))
                                },
                                onPreviewImage = { uri ->
                                    hapticView.performChevereHaptic(ChevereHaptic.Selection)
                                    activePreviewImageUri = uri
                                }
                            )
                        }
                    }
                }
            }

            if (showAddDialog) {
                AddInvoiceDialog(
                    onDismiss = {
                        hapticView.performChevereHaptic(ChevereHaptic.Selection)
                        showAddDialog = false
                    },
                    onConfirm = { vendor, amount, currency, number, date, items ->
                        hapticView.performChevereHaptic(ChevereHaptic.Action)
                        onIntent(InvoicesIntent.AddInvoice(vendor, amount, currency, number, date, items))
                        showAddDialog = false
                    }
                )
            }

            activePreviewImageUri?.let { uri ->
                FullscreenImagePreviewDialog(
                    imageUri = uri,
                    onDismiss = { activePreviewImageUri = null }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InvoiceRowItem(
    invoice: InvoiceEntity,
    onUpdateStatus: (InvoiceStatus) -> Unit,
    onDelete: () -> Unit,
    onPreviewImage: (String) -> Unit
) {
    val hapticView = LocalView.current
    var showMenu by remember { mutableStateOf(false) }

    val statusColor = when (invoice.status) {
        InvoiceStatus.PAID -> Color(0xFF00C853)
        InvoiceStatus.PENDING -> Color(0xFFFFAB00)
        InvoiceStatus.OVERDUE -> Color(0xFFFF1744)
    }

    val strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)

    Box {
        Card(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, strokeColor),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        // Card single click does nothing (avoids interfering with other clickable elements inside)
                    },
                    onLongClick = {
                        hapticView.performChevereHaptic(ChevereHaptic.Selection)
                        showMenu = true
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Receipt image thumbnail if present
                var isImageError by remember(invoice.imageUri) { mutableStateOf(false) }

                if (!invoice.imageUri.isNullOrBlank() && !isImageError) {
                    AsyncImage(
                        model = invoice.imageUri,
                        contentDescription = "Invoice scan thumbnail",
                        contentScale = ContentScale.Crop,
                        onState = { state ->
                            if (state is coil.compose.AsyncImagePainter.State.Error) {
                                isImageError = true
                            }
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPreviewImage(invoice.imageUri) }
                    )
                    Spacer(Modifier.width(12.dp))
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = invoice.vendor,
                        style = Typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Build a descriptive subtitle from available fields
                    val description = buildInvoiceDescription(invoice)
                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            style = Typography.bodySmall,
                            maxLines = 2,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${invoice.currency} ${"%,.2f".format(invoice.totalAmount ?: 0.0)}",
                        style = Typography.bodyLarge.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                        color = statusColor.copy(alpha = 0.1f),
                        modifier = Modifier.clickable {
                            val nextStatus = when (invoice.status) {
                                InvoiceStatus.PENDING -> InvoiceStatus.PAID
                                InvoiceStatus.PAID -> InvoiceStatus.OVERDUE
                                InvoiceStatus.OVERDUE -> InvoiceStatus.PENDING
                            }
                            onUpdateStatus(nextStatus)
                        }
                    ) {
                        Text(
                            text = invoice.status.name,
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_invoice),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier
                .width(180.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(14.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            InvoiceStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text("Mark as ${status.name}") },
                    onClick = {
                        onUpdateStatus(status)
                        showMenu = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Delete Invoice", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    onDelete()
                    showMenu = false
                }
            )
        }
    }
}

/**
 * Builds a concise description from available invoice fields.
 * Prioritizes items/summary, then appends invoice number and date.
 * Falls back to a currency+amount summary if no other details exist.
 */
private fun buildInvoiceDescription(invoice: InvoiceEntity): String {
    val parts = mutableListOf<String>()

    if (!invoice.items.isNullOrBlank()) {
        parts += invoice.items.take(80)
    }

    if (!invoice.invoiceNumber.isNullOrBlank()) {
        parts += "Inv #${invoice.invoiceNumber}"
    }

    if (!invoice.date.isNullOrBlank()) {
        parts += invoice.date
    }

    if (parts.isEmpty()) {
        // Fallback: show the amount as a brief description
        val amount = invoice.totalAmount?.let { "%,.2f".format(it) } ?: "0.00"
        return "${invoice.currency} $amount invoice"
    }

    return parts.joinToString(" · ")
}

@Composable
private fun EmptyInvoicesState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
            contentColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.no_invoices),
            style = Typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AddInvoiceDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, String?, String?, String?) -> Unit
) {
    var vendor by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("USD") }
    var number by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var items by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.add_invoice_title),
                style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = vendor,
                    onValueChange = { vendor = it },
                    label = { Text(stringResource(R.string.invoice_vendor_hint)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text(stringResource(R.string.invoice_amount_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it },
                        label = { Text(stringResource(R.string.invoice_currency_hint)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text(stringResource(R.string.invoice_number_hint)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text(stringResource(R.string.invoice_date_hint)) },
                    placeholder = { Text("YYYY-MM-DD") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = items,
                    onValueChange = { items = it },
                    label = { Text(stringResource(R.string.invoice_items_hint)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                    onConfirm(vendor, amount, currency, number.takeIf { it.isNotBlank() }, date.takeIf { it.isNotBlank() }, items.takeIf { it.isNotBlank() })
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.add_invoice_button),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel_button),
                    color = MaterialTheme.colorScheme.primary,
                    style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.padding(16.dp)
    )
}
