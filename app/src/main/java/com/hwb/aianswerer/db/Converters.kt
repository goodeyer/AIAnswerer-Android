package com.hwb.aianswerer.db

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Room TypeConverter：在 FloatArray 与 ByteArray 之间转换。
 *
 * 出于体积与内存效率考虑，bge-m3 1024 维向量直接以小端字节序存为 BLOB，
 * 而非 JSON / String 形式。1024×4 = 4096 字节/chunk。
 *
 * 当前 entities 已用 ByteArray 字段（KbChunk.embedding），因此 Converters 不
 * 强制需要参与 Room 列映射；保留 TypeConverter 仅作为业务层显式调用工具，
 * 便于在 Repository 层做 FloatArray ↔ ByteArray 互转。
 */
object Converters {

    private const val BYTES_PER_FLOAT = 4

    @TypeConverter
    @JvmStatic
    fun floatArrayToBytes(array: FloatArray?): ByteArray? {
        if (array == null) return null
        val buffer = ByteBuffer.allocate(BYTES_PER_FLOAT * array.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (v in array) {
            buffer.putFloat(v)
        }
        return buffer.array()
    }

    @TypeConverter
    @JvmStatic
    fun bytesToFloatArray(bytes: ByteArray?): FloatArray? {
        if (bytes == null) return null
        require(bytes.size % BYTES_PER_FLOAT == 0) {
            "Embedding ByteArray size ${bytes.size} not aligned to $BYTES_PER_FLOAT bytes"
        }
        val n = bytes.size / BYTES_PER_FLOAT
        val out = FloatArray(n)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) {
            out[i] = buffer.float
        }
        return out
    }
}
