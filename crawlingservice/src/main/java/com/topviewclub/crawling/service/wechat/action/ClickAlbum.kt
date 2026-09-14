package com.topviewclub.crawling.service.wechat.action

import android.view.accessibility.AccessibilityEvent
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.common.log.logI

/**
 * 点击相册按钮
 * */
class ClickAlbum : Action {

    private companion object {
        private const val CLICK_ALBUM_DESCRIPTION = "相册，按钮"
        // Xiaomi 22041216C / 微信 8.0.76 的自绘相册按钮中心。
        // 正常带有底部标签栏的 BaseScanUI 上中心为 (942, 2097)，对应比率 0.872f, 0.853f。
        // 无底部标签栏时中心为 (938, 2284)，对应比率 0.869f, 0.928f。
        private const val ALBUM_X_RATIO = 0.872f
        private const val ALBUM_Y_RATIO = 0.853f
        private const val ALBUM_Y_FALLBACK_RATIO = 0.928f
        private const val SCAN_PAGE_SETTLE_MS = 1000L
    }

    override val actionName: String = "ClickAlbum"

    private val retryState = PickerRetryState(actionName)
    private var lastUiSignature: String? = null
    private var lastSourceSignature: String? = null
    private var scanPageConfirmed = false
    private var albumClickReadyAt = 0L

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val currentRoot = service.rootInActiveWindow
        val uiSignature = listOf(
            event.packageName?.toString().orEmpty(),
            event.uiClassName(),
            currentRoot?.packageName?.toString().orEmpty(),
            currentRoot?.className?.toString().orEmpty(),
        ).joinToString("|")
        if (uiSignature != lastUiSignature) {
            lastUiSignature = uiSignature
            logI(actionName, "无障碍页面: $uiSignature")
            val root = currentRoot
            if (root == null) {
                logI(actionName, "当前窗口根节点: null")
            } else {
                val rootBounds = Rect()
                root.getBoundsInScreen(rootBounds)
                logI(
                    actionName,
                    "当前窗口根节点: class=${root.className} childCount=${root.childCount} " +
                            "bounds=${rootBounds.toShortString()} visible=${root.isVisibleToUser} " +
                            "clickable=${root.isClickable} actions=${root.actionList}",
                )
            }
        }

        // BaseScanUI 的窗口根节点在这台 Xiaomi 上经常是空壳，但事件 source
        // 仍然会直接指向底部相册 ImageView。优先使用 source，避免把这个真实
        // 的无障碍节点丢掉后再退回到无法命中的坐标手势。
        // 应用内任务唤醒使用合成 AccessibilityEvent，它没有系统 sealed 标记，
        // 读取 source 会抛 UnsupportedOperationException；这类事件没有节点，
        // 只需忽略 source 并等待真实微信事件。
        val eventSource = runCatching { event.source }.getOrNull()
        if (event.packageName?.toString() == WECHAT_PACKAGE_NAME && eventSource != null) {
            val bounds = Rect()
            eventSource.getBoundsInScreen(bounds)
            val sourceSignature = listOf(
                eventSource.className?.toString().orEmpty(),
                eventSource.viewIdResourceName.orEmpty(),
                eventSource.text?.toString().orEmpty(),
                eventSource.contentDescription?.toString().orEmpty(),
                bounds.toShortString(),
                eventSource.isClickable,
            ).joinToString("|")
            if (sourceSignature != lastSourceSignature) {
                lastSourceSignature = sourceSignature
                logI(actionName, "事件节点: $sourceSignature")
            }
            if (isAlbumNode(eventSource) && clickTarget(eventSource)) {
                retryState.scheduleProbe(service, event)
                logI(actionName, "已通过事件 source 点击相册节点")
                return actionName
            }
        }

        if ((event.packageName?.toString() == WECHAT_PACKAGE_NAME && event.isGalleryUiEvent()) ||
            service.isWechatPhotoPickerContext() ||
            service.isGalleryPickerVisible(event)
        ) {
            scanPageConfirmed = false
            albumClickReadyAt = 0L
            retryState.reset()
            logI(actionName, "已确认进入微信相册页")
            // AlbumPreviewUI 切换完成后可能不再发送新的无障碍事件，主动复用
            // 当前事件唤起下一步，避免责任链停在 ClickAlbum。
            service.resumeServiceDelay(event, 350L)
            return "SelectPhoneOrOpenFolderList"
        }

        // 显式启动微信时会先经过 LauncherUI。只有当前活动窗口是微信，
        // 且 AccessibilityEvent 或当前根节点明确报告 BaseScanUI/scanner.ui，
        // 才能开始相册点击计时。
        val rootClassName = currentRoot?.className?.toString().orEmpty()
        val isWechatActive = currentRoot?.packageName?.toString() == WECHAT_PACKAGE_NAME ||
                event.packageName?.toString() == WECHAT_PACKAGE_NAME ||
                service.isWechatScanWindowVisible()
        val scanPageVisible = isWechatActive && (
            event.isWechatScanContext() ||
            rootClassName.contains("BaseScanUI", ignoreCase = true) ||
            rootClassName.contains("scanner.ui", ignoreCase = true) ||
            service.isWechatScanWindowVisible() ||
            !service.isWechatPhotoPickerContext()
        )

        // 如果已经提交过相册点击手势，且当前已离开 BaseScanUI，直接进入选择器链路
        if (retryState.currentAttempts > 0 && !scanPageVisible && isWechatActive) {
            scanPageConfirmed = false
            albumClickReadyAt = 0L
            retryState.reset()
            logI(actionName, "点击相册后已离开 BaseScanUI，进入选择器责任链")
            service.resumeServiceDelay(event, 350L)
            return "SelectPhoneOrOpenFolderList"
        }

        if (!scanPageConfirmed) {
            if (!scanPageVisible) {
                retryState.scheduleProbe(service, event)
                return actionName
            }

            scanPageConfirmed = true
            retryState.reset()
            albumClickReadyAt = SystemClock.uptimeMillis() + SCAN_PAGE_SETTLE_MS
            logI(actionName, "已确认 BaseScanUI，等待 ${SCAN_PAGE_SETTLE_MS}ms 后点击相册")
            service.resumeServiceDelay(event, SCAN_PAGE_SETTLE_MS)
            return actionName
        }

        val settleRemaining = albumClickReadyAt - SystemClock.uptimeMillis()
        if (settleRemaining > 0L) {
            service.resumeServiceDelay(event, settleRemaining)
            return actionName
        }

        // 绝不点击非微信界面（如桌面或系统相机）
        if (!isWechatActive) {
            logI(actionName, "当前活动窗口非微信 (pkg=${currentRoot?.packageName})，跳过手势并等待")
            retryState.scheduleProbe(service, event)
            return actionName
        }

        val root = currentRoot
        val target = root?.findNodeOrNull {
            val description = contentDescription?.toString().orEmpty()
            val label = text?.toString().orEmpty()
            description == CLICK_ALBUM_DESCRIPTION ||
                    description.contains("相册") ||
                    label == "相册"
        }
        if (target != null && clickTarget(target)) {
            retryState.scheduleProbe(service, event)
            return actionName
        }

        // BaseScanUI 可能只暴露一个空根节点，使用无障碍服务手势兜底；
        // dispatchGesture=true 仅表示系统接收，不能代表页面已切换。
        val targetY = if (retryState.currentAttempts % 2 == 0) ALBUM_Y_FALLBACK_RATIO else ALBUM_Y_RATIO
        val dispatched = retryState.dispatch(service, event, ALBUM_X_RATIO, targetY) {
            logI(actionName, "相册无障碍手势完成: $it (yRatio=$targetY)")
        }
        logI(actionName, "相册节点不可见，提交无障碍坐标手势 accepted=$dispatched (yRatio=$targetY)")
        return actionName
    }

    private fun isAlbumNode(node: AccessibilityNodeInfo): Boolean {
        val description = node.contentDescription?.toString().orEmpty()
        val label = node.text?.toString().orEmpty()
        return description == CLICK_ALBUM_DESCRIPTION ||
                description.contains("相册") ||
                label == "相册"
    }

    private fun clickTarget(target: AccessibilityNodeInfo): Boolean {
        if (target.click()) return true
        val parent = target.parent ?: return false
        if (parent.click()) return true
        return parent.parent?.click() == true
    }

}
