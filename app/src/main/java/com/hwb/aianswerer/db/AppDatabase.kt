package com.hwb.aianswerer.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hwb.aianswerer.db.dao.KbChunkDao
import com.hwb.aianswerer.db.dao.KbDocumentDao
import com.hwb.aianswerer.db.dao.KnowledgeBaseDao
import com.hwb.aianswerer.db.entities.KbChunk
import com.hwb.aianswerer.db.entities.KbDocument
import com.hwb.aianswerer.db.entities.KnowledgeBase

/**
 * 应用级 Room 数据库（RAG 知识库）
 *
 * - exportSchema=false：当前未启用 schema 演进追踪，保持仓库精简。
 * - fallbackToDestructiveMigration：首版迭代期允许结构变更后清库，
 *   后续稳定后应替换为正式 Migration（详见 plan TODO）。
 * - 单例：onCreate 阶段在 MyApplication 中提前 warmup。
 */
@Database(
    entities = [KnowledgeBase::class, KbDocument::class, KbChunk::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun knowledgeBaseDao(): KnowledgeBaseDao
    abstract fun kbDocumentDao(): KbDocumentDao
    abstract fun kbChunkDao(): KbChunkDao

    companion object {
        private const val DB_NAME = "ai_answerer_rag.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
