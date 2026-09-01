package com.topviewclub.crawling.service.wechat.action

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.common.log.logI
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.back
import com.topviewclub.crawling.service.click

/**
 * 采集完成后把微信状态收口到非扫描页面。
 *
 * 旧实现从无障碍服务直接 startActivity 打开 LauncherUI，Android 14/MIUI 会把
 * 这类后台启动拦截掉。这里复用 StartWechatScanActivity 的 Launcher 节点导航，
 * 到达扫一扫后再用无障碍节点或全局返回关闭它。
 */
internal class ReturnToWechatLauncher : Action {

    private companion object {
        private const val MAX_CLOSE_ATTEMPTS = 12
        private const val PROBE_DELAY_MS = 450L
        private val CLOSE_LABELS = setOf("关闭", "取消")
    }

    override val actionName: String = "ReturnToWechatLauncher"

    private val startScanner = StartWechatScanActivity()
    private var scannerReached = false
    private var closeAttempts = 0

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent,
    ): String {
        if (!scannerReached) {
            // StartWechatScanActivity 只通过无障碍节点、全局动作和 dispatchGesture
            // 导航；返回 ClickAlbum 仅表示已经确认到达扫一扫。
            val next = startScanner.execute(service, event)
            if (next != "ClickAlbum") return actionName
            scannerReached = true
            closeAttempts = 0
        }

        if (!isScanPage(service, event)) {
            reset()
            logI(actionName, "已通过无障碍离开扫一扫页")
            return AutoOperationService.ActionType.ActionSuccess
        }

        closeAttempts++
        if (closeAttempts > MAX_CLOSE_ATTEMPTS) {
            throw IllegalStateException("无法通过无障碍关闭微信扫一扫")
        }

        val close = service.findWechatNodeOrNull {
            (text?.toString() in CLOSE_LABELS || contentDescription?.toString() in CLOSE_LABELS) &&
                    !isEditable
        }
        val closed = (close?.let { closeTarget(it) } == true) || service.back()
        logI(actionName, "关闭扫一扫: accessibilityTarget=${close != null}, accepted=$closed")
        service.resumeServiceDelay(event, PROBE_DELAY_MS)
        return actionName
    }

    private fun isScanPage(
        service: AutoOperationService,
        event: AccessibilityEvent,
    ): Boolean {
        val rootClass = service.rootInActiveWindow?.className?.toString().orEmpty()
        if (rootClass.isNotBlank()) {
            return rootClass.contains("BaseScanUI", ignoreCase = true) ||
                    rootClass.contains("scanner.ui", ignoreCase = true)
        }
        return event.isScanUiEvent()
    }

    private fun closeTarget(target: AccessibilityNodeInfo): Boolean =
        target.click() || target.parent?.click() == true || target.parent?.parent?.click() == true

    private fun reset() {
        scannerReached = false
        closeAttempts = 0
    }
}
