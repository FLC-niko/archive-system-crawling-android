package com.topviewclub.common.util

import android.app.Activity
import android.content.*
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.core.view.WindowCompat
import com.topviewclub.common.base.appContext
import java.io.File
import java.io.PipedReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


//import io.appium.java_client.TouchAction
//import io.appium.java_client.android.AndroidDriver
//import io.appium.java_client.touch.WaitOptions
//import io.appium.java_client.touch.offset.PointOption
//import java.time.Duration

private var toast: Toast? = null

fun toast(msg: String, time: Int = Toast.LENGTH_LONG) {
    toast?.cancel()
    Toast.makeText(appContext, msg, time).apply {
        toast = this
        show()
    }
}

/**
 * 状态栏文字颜色
 */
fun Activity.setStatusBarTextColor(isStateBarTextBlack: Boolean) {
    WindowCompat.getInsetsController(
        window,
        window.decorView
    )!!.isAppearanceLightStatusBars = isStateBarTextBlack
}

/**
 * 获取当前是否为深色模式
 * @return true 为深色模式  false 为浅色模式
 */
val Context.isDarkMode: Boolean
    get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

/**
 * 获取当前最小子类的类名（不会包含包路径）
 * */
val Any.className: String
    get() = javaClass.name.let { name ->
        name.substring(name.lastIndexOf(".") + 1)
    }

/**
 * 若为空，返回空字符串，否则返回 [Any.toString]
 * */
fun Any?.toStringOrEmpty(): String = this?.toString() ?: ""

/**
 * 打开学习强国 APP
 * */
fun Context.startXueXiActivity() {
    // 回桌面一次，保证有打开微信的时间去触发无障碍服务
    startActivity(
        Intent(Intent.ACTION_MAIN).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addCategory(Intent.CATEGORY_HOME)
        }
    )
    // 延迟是给时间给手机缓一下
    Handler(Looper.getMainLooper()).postDelayed({
        startActivity(Intent().apply {
            component = ComponentName(
                "cn.xuexi.android",
                "com.alibaba.android.rimet.biz.SplashActivity"
            )
            @Suppress("WrongConstant")
            flags = 0x14000000
            action = Intent.ACTION_MAIN
        })
    }, 3000L)
}

/**
 * 默认的文件存储路径
 * */
@Suppress("DEPRECATION")
fun Context.defaultOutputDirectory(): File {
    val mediaDir = externalMediaDirs.firstOrNull()?.let {
        File(it, packageName).apply { mkdir() }
    }
    return if (mediaDir != null && mediaDir.exists())
        mediaDir else filesDir
}

/**
 * 获取剪切板内容
 */
val Context.clipboardContent: String
    get() {
        val clipboard = getSystemService<ClipboardManager>()!!
        val clipData = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val item = clipData.getItemAt(0)
            return item?.text?.toString() ?: ""
        }
        return ""
    }

/**
 * 复制内容到剪贴板
 */
fun Context.saveClipboardContent(value: String) {
    val clipboard = getSystemService<ClipboardManager>()!!
    clipboard.setPrimaryClip(ClipData.newPlainText(null, value))
}

@RequiresApi(Build.VERSION_CODES.O)
fun getCurrentTime():String{
    //获取当前日期
    val currentDateTime = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val data = currentDateTime.format(formatter)

    //获取当前时间
    val minute = currentDateTime.minute
    val formattedTime = currentDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

    return "$data $formattedTime"
}

//fun Context.restartApp(){
//    val intent = packageManager.getLaunchIntentForPackage(packageName)
//    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
//    startActivity(intent)
//}
//fun Context.restartAppTwo(){
//    val intent = Intent(this, CrawlingActivity::class.java)
//    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
//    startActivity(intent)
//    Process.killProcess(Process.myPid())
//}
