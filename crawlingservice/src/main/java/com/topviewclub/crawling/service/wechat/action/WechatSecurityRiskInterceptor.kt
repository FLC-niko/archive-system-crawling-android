package com.topviewclub.crawling.service.wechat.action

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.topviewclub.common.log.logI
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.crawling.service.back

/**
 * 微信安全风险/短信验证弹窗及系统弹窗拦截器。
 * 1. 当遇到“当前账号存在安全风险，需完成短信验证后才能继续使用微信”等界面时，
 *    点击左上角关闭按钮退出该界面。
 * 2. 当遇到小米双开应用选择器时，自动选中主微信继续。
 */
object WechatSecurityRiskInterceptor {

    private const val TAG = "SecurityRiskInterceptor"
    private const val WECHAT_PACKAGE_NAME = "com.tencent.mm"

    // 屏幕 1080x2460 时，左上角“X”关闭按钮精确中心为 (67, 158)，比率为 (0.062f, 0.064f)
    private const val TOP_LEFT_CLOSE_X_RATIO = 0.062f
    private const val TOP_LEFT_CLOSE_Y_RATIO = 0.064f
    private const val MIN_ACTION_INTERVAL_MS = 1000L

    private var lastActionAt = 0L
    private var dismissAttempts = 0

    fun isSecurityRiskVisible(service: AutoOperationService, event: AccessibilityEvent): Boolean {
        val root = service.rootInActiveWindow
        val rootPkg = root?.packageName?.toString().orEmpty()
        val eventPkg = event.packageName?.toString().orEmpty()

        // 仅在微信包名或事件内生效，避免误伤其他应用或系统桌面
        if (rootPkg != WECHAT_PACKAGE_NAME && eventPkg != WECHAT_PACKAGE_NAME) {
            return false
        }

        val eventClass = event.className?.toString().orEmpty()
        val rootClass = root?.className?.toString().orEmpty()

        if (eventClass.contains("WxaLiteAppLiteUI", ignoreCase = true) ||
            rootClass.contains("WxaLiteAppLiteUI", ignoreCase = true)
        ) {
            return true
        }

        if (root != null) {
            val hasSecurityNode = root.findNodeOrNull {
                val t = text?.toString() ?: contentDescription?.toString() ?: ""
                t.contains("安全风险") || t.contains("需完成短信验证") || t == "开始验证"
            } != null
            if (hasSecurityNode) return true

            val hasFrequencyLimit = root.findNodeOrNull {
                val t = text?.toString() ?: contentDescription?.toString() ?: ""
                t.contains("操作太过于频繁") || t.contains("操作过于频繁") || (t.contains("频繁") && t.contains("稍后再试"))
            } != null
            if (hasFrequencyLimit) return true
        }

        return false
    }

    fun handle(service: AutoOperationService, event: AccessibilityEvent): Boolean {
        val eventClass = event.className?.toString().orEmpty()
        val root = service.rootInActiveWindow
        val rootClass = root?.className?.toString().orEmpty()

        // 兼容小米应用双开选择弹窗
        val isXSpace = eventClass.contains("XSpaceResolveActivity", ignoreCase = true) ||
                rootClass.contains("XSpaceResolveActivity", ignoreCase = true)
        if (isXSpace) {
            val now = SystemClock.uptimeMillis()
            if (now - lastActionAt > MIN_ACTION_INTERVAL_MS) {
                lastActionAt = now
                logI(TAG, "检测到小米双开选择弹窗，自动选中双开微信")
                val rememberBox = root?.findNodeOrNull {
                    val t = text?.toString() ?: contentDescription?.toString() ?: ""
                    t.contains("下次默认") || t.contains("不再提示")
                }
                rememberBox?.click() ?: service.tapWechatRatio(0.14f, 0.85f) // 勾选不再提示
                // 双开微信图标位于右侧 (0.69f, 0.73f)
                service.tapWechatRatio(0.69f, 0.73f)
                service.resumeServiceDelay(event, 800L)
            }
            return true
        }

        // 兼容微信大图预览/头像查看界面 (ProfileHdHeadImg)
        val isProfileHdHeadImg = eventClass.contains("ProfileHdHeadImg", ignoreCase = true) ||
                rootClass.contains("ProfileHdHeadImg", ignoreCase = true) ||
                service.currentWechatActivity?.contains("ProfileHdHeadImg", ignoreCase = true) == true
        if (isProfileHdHeadImg) {
            val now = SystemClock.uptimeMillis()
            if (now - lastActionAt > MIN_ACTION_INTERVAL_MS) {
                lastActionAt = now
                logI(TAG, "检测到微信大图预览界面 (ProfileHdHeadImg)，执行关闭/返回")
                val closeNode = root?.findNodeOrNull {
                    val desc = contentDescription?.toString().orEmpty()
                    val txt = text?.toString().orEmpty()
                    desc == "关闭" || desc == "返回" || txt == "关闭" || txt == "取消"
                }
                if (closeNode != null && (closeNode.click() || closeNode.parent?.click() == true)) {
                    logI(TAG, "已通过无障碍节点点击关闭大图预览")
                } else {
                    logI(TAG, "点击左上角关闭或执行返回")
                    service.tapWechatRatio(TOP_LEFT_CLOSE_X_RATIO, TOP_LEFT_CLOSE_Y_RATIO)
                    service.back()
                }
                service.currentWechatActivity = null
                service.resumeServiceDelay(event, 600L)
            }
            return true
        }

        // 兼容公众号高频扫码风控弹窗（“操作太过于频繁，请稍后再试”）
        val freqNode = root?.findNodeOrNull {
            val t = text?.toString() ?: contentDescription?.toString() ?: ""
            t.contains("操作太过于频繁") || t.contains("操作过于频繁") || (t.contains("频繁") && t.contains("稍后再试"))
        }
        if (freqNode != null) {
            val now = SystemClock.uptimeMillis()
            if (now - lastActionAt > MIN_ACTION_INTERVAL_MS) {
                lastActionAt = now
                logI(TAG, "检测到高频扫码风控弹窗 [${freqNode.text}]，点击确定/消除弹窗")
                val confirmNode = root.findNodeOrNull {
                    val t = text?.toString() ?: contentDescription?.toString() ?: ""
                    t == "确定" || t == "我知道了" || t == "好的"
                }
                if (confirmNode != null && (confirmNode.click() || confirmNode.parent?.click() == true)) {
                    logI(TAG, "已通过无障碍节点点击确认关闭风控弹窗")
                } else {
                    logI(TAG, "未找到确定按钮，执行全局返回消除弹窗")
                    service.back()
                }
                service.currentWechatActivity = null
                service.resumeServiceDelay(event, 800L)
            }
            return true
        }

        if (!isSecurityRiskVisible(service, event)) {
            dismissAttempts = 0
            return false
        }

        val now = SystemClock.uptimeMillis()
        if (now - lastActionAt < MIN_ACTION_INTERVAL_MS) {
            return true
        }
        lastActionAt = now
        dismissAttempts++

        logI(TAG, "检测到微信安全风险/短信验证界面，尝试关闭 (attempt=$dismissAttempts)")

        // 1. 优先尝试无障碍节点关闭
        val closeNode = root?.findNodeOrNull {
            val desc = contentDescription?.toString().orEmpty()
            val txt = text?.toString().orEmpty()
            desc == "关闭" || desc == "返回" || txt == "关闭" || txt == "取消"
        }
        if (closeNode != null && (closeNode.click() || closeNode.parent?.click() == true)) {
            logI(TAG, "已通过无障碍节点点击关闭")
        } else {
            // 2. 自绘界面点击左上角“X”关闭按钮
            logI(TAG, "点击左上角关闭按钮 ($TOP_LEFT_CLOSE_X_RATIO, $TOP_LEFT_CLOSE_Y_RATIO)")
            service.tapWechatRatio(TOP_LEFT_CLOSE_X_RATIO, TOP_LEFT_CLOSE_Y_RATIO)
        }

        // 若多次点击后仍未退出（或触发二次确认弹窗），使用系统全局返回兜底
        if (dismissAttempts >= 2) {
            logI(TAG, "多次点击未跳出，执行全局返回键")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }

        // 重置微信 Activity 缓存，避免因关闭 LiteApp 未触发新 UI 事件而误判
        service.currentWechatActivity = null
        service.resumeServiceDelay(event, 600L)
        return true
    }
}
