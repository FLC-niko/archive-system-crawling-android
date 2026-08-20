package com.topviewclub.common.base

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.CallSuper
import com.topviewclub.common.storage.video.WechatVideoCacheCaptor
import org.lsposed.hiddenapibypass.HiddenApiBypass

lateinit var appContext: Context
val wechatVideoCacheCaptor by lazy { WechatVideoCacheCaptor(appContext) }


open class BaseApplication : Application() {
    @CallSuper
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }


    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }
}