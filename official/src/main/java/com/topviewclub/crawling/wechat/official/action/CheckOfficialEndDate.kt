package com.topviewclub.crawling.wechat.official.action

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.wechat.official.officialTimeFormat

class CheckOfficialEndDate : Action {

    companion object {
        private const val PUBLISH_DATE_ID = "com.tencent.mm:id/ac5"
        private const val THE_END_TEXT = "已无更多订阅消息"
    }

    override val actionName: String = "CheckOfficialEndDate"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        service.resumeServiceDelay(event, 100L)
        val root = service.rootInActiveWindow ?: return actionName
        val end = root.findNodeOrNull {
            text?.toString() == THE_END_TEXT
        }
        // 已经到达推文页末尾
        if (end != null) {
            try {
                return AutoOperationService.ActionType.ActionSuccess
            } finally {
                service.resumeServiceDelay(event, 0L)
            }
        }
        try {
            return if (isAfterPublishDate(service.endDate, root)) "EnterOfficialArticle"
            else "ScrollOfficialList"
        } finally {
            service.resumeServiceDelay(event, 10L)
        }
    }

    private fun isAfterPublishDate(endDate: Long, root: AccessibilityNodeInfo): Boolean {
        // 大于当前时间
        if (endDate >= System.currentTimeMillis()) return true

        val targets = root.findNodes {
            className == CLS_TEXT_VIEW
                    && viewIdResourceName == PUBLISH_DATE_ID
        }

        // 屏幕上刚好没有发布时间，则继续下滚动
        if (targets.isEmpty()) return false

        targets.last().text?.toString()?.let {
            return officialTimeFormat(it) <= endDate
        }

        return false
    }

}