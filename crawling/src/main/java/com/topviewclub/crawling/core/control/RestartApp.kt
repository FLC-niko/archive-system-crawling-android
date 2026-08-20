package com.topviewclub.crawling.core.control

import android.content.Context
import android.content.Intent
import android.os.Process
import com.topviewclub.crawling.core.ui.CrawlingActivity

fun Context.restartApp(){
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    startActivity(intent)
}
fun Context.restartAppTwo(){
    val intent = Intent(this, CrawlingActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    startActivity(intent)
    Process.killProcess(Process.myPid())
}