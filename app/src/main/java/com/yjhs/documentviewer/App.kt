package com.yjhs.documentviewer

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import com.ycbjie.webviewlib.X5LogUtils
import com.ycbjie.webviewlib.X5WebUtils
import java.io.File
import java.io.IOException
import kotlin.properties.Delegates

/**
 * author: Administrator
 * date: 2019-11-22
 * desc:
 */
class App : Application() {
    companion object {
        var CONTEXT: Context by Delegates.notNull()
        lateinit var FILE_DIR: String
    }
    override fun onCreate() {
        super.onCreate()
        CONTEXT = applicationContext
        // 初始化MultiDex
        MultiDex.install(CONTEXT)
        X5WebUtils.init(CONTEXT)
        X5LogUtils.setIsLog(true);
    }
}