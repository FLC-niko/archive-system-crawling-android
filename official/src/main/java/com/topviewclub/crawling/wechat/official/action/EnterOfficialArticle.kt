package com.topviewclub.crawling.wechat.official.action

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.crawling.wechat.official.officialTimeFormat
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action

class EnterOfficialArticle : Action {

    companion object {
        private const val PUBLISH_DATE_ID = "com.tencent.mm:id/ac5"
        private const val SEARCH_DATE_ID = "com.tencent.mm:id/eo"
        private const val SEARCH_DESC = "搜索"
    }

    override val actionName: String = "EnterOfficialArticle"

    private var stepInternal = 0

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName
        val recyclerView = root.findNodeOrNull {
            className == CLS_RECYCLER_VIEW
        } ?: return actionName
        // 识别公众号列表中能点击的 ViewGroup
        val targets = recyclerView.findNodes {
            className == CLS_VIEW_GROUP && isClickable
        }

        service.resumeServiceDelay(event, 0L)
        return if (targets.isEmpty()) {
            // 理论上不会走这里，除非这个公众号压根没有文章
            "WriteOfficialArticle"
        } else if (stepInternal < targets.size) {
            if (stepInternal == 0 && !isBeforePublishDate(service.startDate, root)) {
                // 已经到达目标日期
                return "WriteOfficialArticle"
            }

            // 当前页面还有结点没被按
            if (targets[stepInternal].click()) {
                stepInternal++
                "OpenMoreEnum"
            } else {
                actionName
            }
        } else {
            // 当前页面结点已全部按完
            if (!recyclerView.scrollForward()) {
                return "WriteOfficialArticle"
            }
            Thread.sleep(1000L)
            stepInternal = 0
            return actionName
        }


    }

    private fun isBeforePublishDate(startDate: Long, root: AccessibilityNodeInfo): Boolean {
        // 非正值，一直抓
        if (startDate == Long.MIN_VALUE) return true

        val targets = root.findNodes {
            className == CLS_TEXT_VIEW
                    && viewIdResourceName == PUBLISH_DATE_ID
        }

        // 屏幕上刚好没有发布时间，则继续获取
        if (targets.isEmpty()) return true

        targets.first().text?.toString()?.let {
            val time = officialTimeFormat(it)
            return time >= startDate
        }

        return true
    }

}