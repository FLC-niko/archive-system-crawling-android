package com.topviewclub.crawling.service.wechat.action

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
    }

    override val actionName: String = "EnterOfficialHome"

    private var waitCount = 0

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName
        isMatchNetworkError(root)
        return if (match(root)) "CheckTargetAccount" else actionName
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
            while (true) {
                if (settings.click()) {
                    return true
                }
            }
        }

        // 未关注
        val follow = root.findNodeOrNull {
            text == FOLLOW
        } ?: return false

        // 关注
        follow.click()
        return false
    }

}