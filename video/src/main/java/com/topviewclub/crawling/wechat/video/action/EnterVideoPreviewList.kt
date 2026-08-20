package com.topviewclub.crawling.wechat.video.action

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action

/**
 * 进入视频预览列表
 * */
class EnterVideoPreviewList : Action {

    override val actionName: String = "EnterVideoPreviewList"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName
        return if (match(service, root)) "EnterVideoList" else actionName
    }

    private fun match(
        service: AutoOperationService,
        root: AccessibilityNodeInfo
    ): Boolean {
        val targetAccount = service.target
        val target = root.findNodeOrNull {
            className == CLS_BUTTON
                    && contentDescription?.toString() == "视频号：$targetAccount"
        }
        if (target != null) {
            while (true) {
                if (target.click()) break
            }
            return true
        }
        return false
    }

}