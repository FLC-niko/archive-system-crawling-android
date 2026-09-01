package com.topviewclub.crawling.service.wechat.action

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.common.log.logE
import com.topviewclub.common.log.logI
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull

/**
 * 直接打开微信扫一扫。
 *
 * 微信主页/扫一扫由显式 Intent 进入；进入微信后的相册、二维码和文章页面
 * 仍全部由无障碍节点和 dispatchGesture 完成，不依赖 adb 输入。
 */
class StartWechatScanActivity : Action {

    private companion object {
        private const val WECHAT_PACKAGE_NAME = "com.tencent.mm"
        private const val WECHAT_LAUNCHER_CLASS = "com.tencent.mm.ui.LauncherUI"
        private const val MIN_ACTION_INTERVAL_MS = 850L
        private const val PROBE_DELAY_MS = 450L
        private const val MAX_LAUNCH_ATTEMPTS = 3

        private val scanLabels = setOf("扫一扫", "扫描")
        private val moreLabels = setOf("更多功能", "更多")
    }

    override val actionName: String = "StartWechatScanActivity"

    private var probeScheduled = false
    private var launchAttempts = 0
    private var lastActionAt = 0L

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent,
    ): String {
        if (isScanWindow(service, event)) {
            logI(actionName, "已直接进入微信扫一扫页")
            reset()
            return "ClickAlbum"
        }

        val root = service.rootInActiveWindow
        val rootPackage = root?.packageName?.toString()
        // 延迟探针会携带旧的 AccessibilityEvent；它的 packageName 可能仍是微信，
        // 但当前 root 已经是 MIUI 桌面。此时不能按“微信主页”处理并反复 HOME，
        // 应继续执行显式 LauncherUI 跳转。
        if (rootPackage == WECHAT_PACKAGE_NAME) {
            if (handleWechat(service, event, root)) return actionName
        }

        if (launchAttempts >= MAX_LAUNCH_ATTEMPTS) {
            throw IllegalStateException("无法直接打开微信扫一扫")
        }
        if (canAct()) {
            launchAttempts++
            val started = runCatching {
                service.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    component = ComponentName(
                        WECHAT_PACKAGE_NAME,
                        WECHAT_LAUNCHER_CLASS,
                    )
                    putExtra("LauncherUI.From.Scaner.Shortcut", true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                })
            }.onFailure {
                logE(actionName, "直接启动微信失败: ${it.javaClass.simpleName}: ${it.message}")
            }.isSuccess
            lastActionAt = SystemClock.uptimeMillis()
            if (started) {
                logI(actionName, "已直接跳转微信 LauncherUI，等待扫一扫页面")
                // 直接跳转成功后把下一步交给 ClickAlbum；延迟探针只复用
                // 无障碍事件，不执行任何 adb 输入。
                service.resumeServiceDelay(event, 1000L)
                return "ClickAlbum"
            }
        }
        scheduleProbe(service, event)
        return actionName
    }

    private fun handleWechat(
        service: AutoOperationService,
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo?,
    ): Boolean {
        val scan = root?.findNodeOrNull {
            nodeLabel(this) in scanLabels && !isEditable
        }
        if (scan != null && canAct()) {
            if (clickTarget(scan)) {
                lastActionAt = SystemClock.uptimeMillis()
                scheduleProbe(service, event)
                return true
            }
        }

        // 如果当前微信版本没有直接打开扫一扫，则点击主页的“更多功能”入口。
        val more = root?.findNodeOrNull {
            nodeLabel(this) in moreLabels && !isEditable
        }
        if (more != null && canAct()) {
            if (clickTarget(more)) {
                lastActionAt = SystemClock.uptimeMillis()
                scheduleProbe(service, event)
                return true
            }
        }

        // 微信主页未暴露入口节点时也直接尝试显式 LauncherUI；不再发送 HOME，
        // 避免旧事件导致桌面与 LauncherUI 之间来回跳转。
        return false
    }

    private fun isScanWindow(service: AutoOperationService, event: AccessibilityEvent): Boolean {
        if (event.isScanUiEvent()) return true
        val rootClass = service.rootInActiveWindow?.className?.toString().orEmpty()
        return rootClass.contains("BaseScanUI", ignoreCase = true) ||
                rootClass.contains("scanner.ui", ignoreCase = true)
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String =
        node.text?.toString()?.takeIf { it.isNotBlank() }
            ?: node.contentDescription?.toString().orEmpty()

    private fun clickTarget(target: AccessibilityNodeInfo): Boolean =
        target.click() || target.parent?.click() == true || target.parent?.parent?.click() == true

    private fun canAct(): Boolean =
        SystemClock.uptimeMillis() - lastActionAt >= MIN_ACTION_INTERVAL_MS

    private fun scheduleProbe(service: AutoOperationService, event: AccessibilityEvent) {
        if (probeScheduled) return
        probeScheduled = true
        service.resumeServiceDelay(event, PROBE_DELAY_MS) {
            probeScheduled = false
        }
    }

    private fun reset() {
        probeScheduled = false
        launchAttempts = 0
        lastActionAt = 0L
    }
}
