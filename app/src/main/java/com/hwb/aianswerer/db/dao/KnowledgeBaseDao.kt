package com.hwb.aianswerer.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hwb.aianswerer.db.entities.KnowledgeBase

/**
 * KnowledgeBase 表 DAO。
 *
 * 命名约定：所有读操作返回纯数据（List/对象），不返回 Flow / LiveData，
 * 上层 UI 自行包一层 StateFlow（与项目现有 Compose 模式一致）。
 */
@Dao
interface KnowledgeBaseDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(kb: KnowledgeBase): Long

    @Update
    suspend fun update(kb: KnowledgeBase)

    @Query("DELETE FROM knowledge_base WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM knowledge_base ORDER BY createdAt ASC")
    suspend fun getAll(): List<KnowledgeBase>

    @Query("SELECT * FROM knowledge_base WHERE enabled = 1 ORDER BY priority DESC, createdAt ASC")
    suspend fun getAllEnabled(): List<KnowledgeBase>

    @Query("SELECT * FROM knowledge_base WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): KnowledgeBase?

    @Query("SELECT COUNT(*) FROM knowledge_base")
    suspend fun count(): Int

    @Query("UPDATE knowledge_base SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE knowledge_base SET priority = :priority WHERE id = :id")
    suspend fun setPriority(id: Long, priority: Int)
}
