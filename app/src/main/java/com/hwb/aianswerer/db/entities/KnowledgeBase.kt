package com.hwb.aianswerer.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 知识库实体
 *
 * 每个知识库代表一组独立的文档集合，可单独设置优先级权重、
 * Embedding 模型与启用状态。检索时根据 priority 与 cosine 分数加权混合。
 *
 * @property id 主键，自增
 * @property name 知识库名称（唯一）
 * @property priority 优先级权重 0-100；得分 = cosine × (priority/100)
 * @property embedModel 该知识库使用的 Embedding 模型标识（如 BAAI/bge-m3）
 * @property enabled 是否启用；关闭时检索阶段跳过其全部 chunk
 * @property createdAt 创建时间戳（epoch ms）
 */
@Entity(
    tableName = "knowledge_base",
    indices = [Index(value = ["name"], unique = true)]
)
data class KnowledgeBase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val priority: Int = 50,
    val embedModel: String = "BAAI/bge-m3",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
