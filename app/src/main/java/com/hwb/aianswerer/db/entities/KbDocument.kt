package com.hwb.aianswerer.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 知识库文档实体
 *
 * 一份原始导入文件对应一条记录，承载 chunk 的归属与统计。
 * 删除 KnowledgeBase 会级联删除其下所有文档与 chunk。
 *
 * @property id 主键
 * @property kbId 所属知识库 id（外键，CASCADE 删除）
 * @property fileName 原始文件名
 * @property fileType 扩展名（txt/md/pdf/docx/xlsx），全小写
 * @property sizeBytes 文件字节数（用于 UI 展示）
 * @property chunkCount 切片后的 chunk 数量
 * @property importedAt 导入时间戳（epoch ms）
 */
@Entity(
    tableName = "kb_document",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeBase::class,
            parentColumns = ["id"],
            childColumns = ["kbId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("kbId")]
)
data class KbDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val kbId: Long,
    val fileName: String,
    val fileType: String,
    val sizeBytes: Long,
    val chunkCount: Int,
    val importedAt: Long = System.currentTimeMillis()
)
