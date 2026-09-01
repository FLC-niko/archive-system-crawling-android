package com.topviewclub.crawling.service.wechat.action

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.common.log.logI
import com.topviewclub.common.bean.TaskResultType
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.action.ActionException

/**
 * 进入公众号主页
 * */
internal class EnterOfficialHome : Action {

    private companion object {
        private const val NETWORK_ERROR = "当前网络不可用"
        private const val NETWORK_CONNECT_ERROR = "无网络连接，请检查网络设置"
        private const val SCAN_COMPLETED = "扫描完成"
        private const val SETTINGS = "设置"
        private const val FOLLOW = "关注"
        private const val CHAT_INFO_X_RATIO = 0.93f
        private const val CHAT_INFO_Y_RATIO = 0.057f

        private val chatInfoLabels = setOf(
            "聊天信息",
            "公众号信息",
            "公众号详情",
            "更多",
        )
    }

    override val actionName: String = "EnterOfficialHome"

    private var waitCount = 0
    private val retryState = PickerRetryState(actionName, maxAttempts = 8)
    private var lastUiSignature: String? = null
    private var chattingContextSeen = false

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow
        val uiSignature = listOf(
            event.packageName?.toString().orEmpty(),
            event.uiClassName(),
            root?.className?.toString().orEmpty(),
            root?.childCount ?: -1,
        ).joinToString("|")
        if (uiSignature != lastUiSignature) {
            lastUiSignature = uiSignature
            logI(actionName, "等待公众号主页: $uiSignature")
        }

        val currentClassName = root?.className?.toString().orEmpty()
        val contactInfoVisible = event.uiClassName().contains("ContactInfoUI", ignoreCase = true) ||
                currentClassName.contains("ContactInfoUI", ignoreCase = true)
        if (contactInfoVisible) {
            retryState.reset()
            chattingContextSeen = false
            logI(actionName, "已确认进入公众号 ContactInfoUI")
            return "CheckTargetAccount"
        }

        val chattingVisible = event.uiClassName().contains("ChattingUI", ignoreCase = true) ||
                currentClassName.contains("ChattingUI", ignoreCase = true)
        if (chattingVisible) chattingContextSeen = true
        if (chattingVisible || chattingContextSeen) {
            val chatInfo = root?.findNodeOrNull {
                val label = contentDescription?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?: text?.toString().orEmpty()
                label in chatInfoLabels
            }
            val clicked = chatInfo?.let(::clickTarget) == true
            val dispatched = if (clicked) {
                retryState.scheduleProbe(service, event)
                true
            } else {
                // 新版微信的公众号 ChattingUI 右上角人形入口可能不暴露节点，
                // 使用无障碍服务按物理屏幕坐标点击其中心。
                retryState.dispatch(
                    service,
                    event,
                    CHAT_INFO_X_RATIO,
                    CHAT_INFO_Y_RATIO,
                )
            }
            logI(
                actionName,
                "打开公众号信息: accessibilityTarget=${chatInfo != null}, accepted=$dispatched",
            )
            return actionName
        }

        if (root == null) {
            retryState.scheduleProbe(service, event)
            return actionName
        }
        isMatchNetworkError(root)

        // 新版微信可能已经直接进入公众号资料页。资料页同时包含“公众号”和
        // 当前目标名称，无需再猜测性点击顶部菜单。
        if (root.findNodeOrNull { text?.toString() == "公众号" } != null &&
            root.findNodeOrNull { text?.toString() == service.target } != null
        ) {
            retryState.reset()
            logI(actionName, "已确认进入目标公众号资料页")
            return "CheckTargetAccount"
        }

        return if (match(root)) {
            retryState.reset()
            "CheckTargetAccount"
        } else {
            retryState.scheduleProbe(service, event)
            actionName
        }
    }

    private fun isMatchNetworkError(root: AccessibilityNodeInfo) {
        root.findNodeOrNull { text == NETWORK_ERROR }?.let {
            throw ActionException(TaskResultType.NETWORK_EXCEPTION)
        }
        root.findNodeOrNull { text == NETWORK_CONNECT_ERROR }?.let {
            throw ActionException(TaskResultType.NETWORK_EXCEPTION)
        }
        root.findNodeOrNull {
            val t = text?.toString() ?: return@findNodeOrNull false
            if (t.length < 4) return@findNodeOrNull false
            t.substring(0, 4) == SCAN_COMPLETED
        }?.let {
            if (waitCount >= 8) {
                throw ActionException(TaskResultType.NETWORK_EXCEPTION)
            } else {
                waitCount++
                Thread.sleep(1000L)
            }
        }
    }

    private fun match(root: AccessibilityNodeInfo): Boolean {
        val settings = root.findNodeOrNull {
            contentDescription == SETTINGS
        }

        // 已关注
        if (settings != null) {
            if (settings.click()) {
                return true
            }
            return false
        }

        // 未关注
        val follow = root.findNodeOrNull {
            text == FOLLOW
        } ?: return false

        // 关注
        follow.click()
        return false
    }

    private fun clickTarget(target: AccessibilityNodeInfo): Boolean =
        target.click() || target.parent?.click() == true || target.parent?.parent?.click() == true

}
