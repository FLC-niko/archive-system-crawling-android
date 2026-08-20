@file:Suppress("FunctionName")

package com.topviewclub.common.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

fun Shizuku_checkPermission(): Boolean =
    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

fun Shizuku_requestPermission(code: Int) = Shizuku.requestPermission(code)

fun Shizuku_killApplication(packageName: String) {
//    ShizukuSystemServerApi.ActivityManager_forceStopPackage(packageName, 0)
    throw NotImplementedError()
}
