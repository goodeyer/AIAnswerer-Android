package com.hwb.aianswerer.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 知识库 chunk 实体
 *
 * 切片后的文本片段及其向量；向量以 ByteArray 直接存储以节省空间（4 bytes/dim）。
 * 1024 维 bge-m3 → 每条 4KB。检索阶段一次性载入内存做余弦计算。
 *
 * @property id 主键
 * @property kbId 所属知识库 id（冗余索引，便于按 kb 维度快速筛选）
 * @property docId 所属文档 id（外键，CASCADE 删除）
 * @property text chunk 原文（用于命中后回填到 prompt）
 * @property embedding 向量 BLOB（FloatArray 通过 Converters 序列化，小端序）
 * @property orderIdx 在原文档内的顺序索引（0-based）
 */
@Entity(
    tableName = "kb_chunk",
    foreignKeys = [
        ForeignKey(
            entity = KbDocument::class,
            parentColumns = ["id"],
            childColumns = ["docId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("kbId"), Index("docId")]
)
data class KbChunk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val kbId: Long,
    val docId: Long,
    val text: String,
    val embedding: ByteArray,
    val orderIdx: Int
) {
    // ByteArray 需要手写 equals/hashCode 才能让 data class 与 Room 比对正常工作
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as KbChunk
        if (id != other.id) return false
        if (kbId != other.kbId) return false
        if (docId != other.docId) return false
        if (text != other.text) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (orderIdx != other.orderIdx) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + kbId.hashCode()
        result = 31 * result + docId.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + orderIdx
        return result
    }
}
