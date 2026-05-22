package com.hwb.aianswerer.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hwb.aianswerer.db.entities.KbDocument

/**
 * KbDocument 表 DAO。
 */
@Dao
interface KbDocumentDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(doc: KbDocument): Long

    @Update
    suspend fun update(doc: KbDocument)

    @Query("DELETE FROM kb_document WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM kb_document WHERE kbId = :kbId")
    suspend fun deleteByKbId(kbId: Long)

    @Query("SELECT * FROM kb_document ORDER BY importedAt ASC")
    suspend fun getAll(): List<KbDocument>

    @Query("SELECT * FROM kb_document WHERE kbId = :kbId ORDER BY importedAt ASC")
    suspend fun getByKbId(kbId: Long): List<KbDocument>

    @Query("SELECT * FROM kb_document WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): KbDocument?

    @Query("SELECT COUNT(*) FROM kb_document WHERE kbId = :kbId")
    suspend fun countByKbId(kbId: Long): Int

    @Query("SELECT COUNT(*) FROM kb_document")
    suspend fun count(): Int
}
