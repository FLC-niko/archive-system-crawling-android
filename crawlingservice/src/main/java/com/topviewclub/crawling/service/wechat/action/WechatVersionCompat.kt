package com.topviewclub.crawling.service.wechat.action

import android.content.Context
import android.os.Build

/**
 * 微信与 Android 系统版本兼容性感知工具。
 *
 * 基准对比：
 * - 老版本基线（AAOS 经典路线）：微信 < 8.0.40（典型如 8.0.18），Android SDK <= 29 (Android 7.0 ~ 10)。
 *   具有完整的原生 View 树（包含 com.tencent.mm:id/f5、QRCode 目录文本、"图片1" contentDescription）。
 * - 新版本基线（现代坐标穿透路线）：微信 >= 8.0.40（典型如 8.0.76），Android SDK >= 30 (Android 11 ~ 14 / HyperOS)。
 *   相册与授权页为自绘 View，原生树为空壳，依赖坐标手势与分区存储专属目录。
 */
object WechatVersionCompat {

    const val LEGACY_WECHAT_THRESHOLD = "8.0.40"
    const val LEGACY_SDK_THRESHOLD = 29 // Android 10 (Q) 及以下

    fun getWechatVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(WECHAT_PACKAGE_NAME, 0).versionName ?: ""
    }.getOrDefault("")

    fun getWechatVersionCode(context: Context): Long = runCatching {
        val pInfo = context.packageManager.getPackageInfo(WECHAT_PACKAGE_NAME, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pInfo.versionCode.toLong()
        }
    }.getOrDefault(0L)

    fun isLegacyWechat(context: Context): Boolean {
        val ver = getWechatVersionName(context)
        if (ver.isBlank()) {
            return isLegacySystem()
        }
        return compareVersion(ver, LEGACY_WECHAT_THRESHOLD) < 0
    }

    fun isLegacySystem(): Boolean = Build.VERSION.SDK_INT <= LEGACY_SDK_THRESHOLD

    fun isLegacyRoute(context: Context): Boolean = isLegacyWechat(context) || isLegacySystem()

    /**
     * 语义化版本号比较：
     * v1 < v2 返回负数，v1 == v2 返回 0，v1 > v2 返回正数。
     * 示例：
     * compareVersion("8.0.18", "8.0.40") < 0 (true)
     * compareVersion("8.0.76", "8.0.40") > 0 (true)
     */
    fun compareVersion(v1: String, v2: String): Int {
        val parts1 = v1.split(".").mapNotNull { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull()
        }
        val parts2 = v2.split(".").mapNotNull { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull()
        }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val num1 = parts1.getOrElse(i) { 0 }
            val num2 = parts2.getOrElse(i) { 0 }
            if (num1 != num2) {
                return num1.compareTo(num2)
            }
        }
        return 0
    }
}
