package com.hwb.aianswerer.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hwb.aianswerer.db.entities.KbChunk

/**
 * KbChunk 表 DAO。
 *
 * - 批量插入使用 List 形式以提升导入吞吐。
 * - getAll() 在启动 / 导入完成后由 RetrievalEngine 一次性载入内存。
 */
@Dao
interface KbChunkDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(chunk: KbChunk): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(chunks: List<KbChunk>): List<Long>

    @Query("DELETE FROM kb_chunk WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM kb_chunk WHERE docId = :docId")
    suspend fun deleteByDocId(docId: Long)

    @Query("DELETE FROM kb_chunk WHERE kbId = :kbId")
    suspend fun deleteByKbId(kbId: Long)

    @Query("SELECT * FROM kb_chunk ORDER BY kbId ASC, docId ASC, orderIdx ASC")
    suspend fun getAll(): List<KbChunk>

    @Query("SELECT * FROM kb_chunk WHERE kbId = :kbId ORDER BY docId ASC, orderIdx ASC")
    suspend fun getByKbId(kbId: Long): List<KbChunk>

    @Query("SELECT * FROM kb_chunk WHERE docId = :docId ORDER BY orderIdx ASC")
    suspend fun getByDocId(docId: Long): List<KbChunk>

    @Query("SELECT COUNT(*) FROM kb_chunk WHERE kbId = :kbId")
    suspend fun countByKbId(kbId: Long): Int

    @Query("SELECT COUNT(*) FROM kb_chunk")
    suspend fun count(): Int
}
