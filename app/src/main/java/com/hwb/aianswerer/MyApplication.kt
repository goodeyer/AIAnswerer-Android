package com.hwb.aianswerer

import android.app.Application
import android.content.Context
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.db.AppDatabase
import com.hwb.aianswerer.utils.LanguageUtil

class MyApplication : Application() {

    override fun attachBaseContext(base: Context) {
        // 在attachBaseContext中初始化MMKV（必须在使用AppConfig之前）
        AppConfig.init(base)

        // 应用语言配置并获取新的Context
        val context = LanguageUtil.attachBaseContext(base)

        super.attachBaseContext(context)
    }

    override fun onCreate() {
        super.onCreate()

        // 保存Application实例
        instance = this

        // MMKV已在attachBaseContext中初始化
        // 语言配置已在attachBaseContext中应用

        // 预热 Room 数据库（RAG 知识库），首次访问触发同步建库，
        // 避免首次答题时主线程在 OpenHelper 上等待。
        AppDatabase.getInstance(this)
    }

    companion object {
        private lateinit var instance: MyApplication

        /**
         * 获取Application实例
         */
        fun getInstance(): MyApplication = instance

        /**
         * 获取Application Context
         * 使用 applicationContext 确保获取到最新的配置（包括语言切换后的）
         */
        fun getAppContext(): Context = instance.applicationContext

        /**
         * 便捷方法：获取字符串资源
         * @param resId 字符串资源ID
         * @return 字符串
         */
        fun getString(resId: Int): String {
            return getAppContext().getString(resId)
        }

        /**
         * 便捷方法：获取带格式化参数的字符串资源
         * @param resId 字符串资源ID
         * @param formatArgs 格式化参数
         * @return 格式化后的字符串
         */
        fun getString(resId: Int, vararg formatArgs: Any): String {
            return getAppContext().getString(resId, *formatArgs)
        }
    }
}

