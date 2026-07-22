package com.neo.chevere.data.datasource.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY id DESC")
    fun getAllInvoicesFlow(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices ORDER BY id DESC")
    suspend fun getAllInvoices(): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getInvoiceById(id: Int): InvoiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Update
    suspend fun updateInvoice(invoice: InvoiceEntity)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteInvoice(id: Int)
}
