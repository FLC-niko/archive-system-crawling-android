package com.topviewclub.crawling.xuexi.action

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.xuexi.xueXiTimeFormat

class EnterXueXiArticle : Action {

    companion object {
        private const val RELEASE_INFO_ID = "cn.xuexi.android:id/st_feeds_card_bottom"
        private const val LIST_ITEM_TITLE_ID = "cn.xuexi.android:id/general_card_title_id"

        internal var isBottom = false
    }

    override val actionName: String = "EnterXueXiArticle"

    private var stepInternal = 0

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName

        if (isBottom) return "WriteXueXiArticle"

        val itemTitle = root.findNodeOrNull {
            viewIdResourceName == LIST_ITEM_TITLE_ID
        } ?: return actionName

        val listView = itemTitle.findSuperNodeOrNull {
            className == CLS_LIST_VIEW
        } ?: return actionName

        val targets = listView.findNodes {
            className == CLS_FRAME_LAYOUT
                    && findNodeOrNull { viewIdResourceName == LIST_ITEM_TITLE_ID } != null
        }

        if (targets.isEmpty()) {
            return actionName
        } else if (stepInternal < targets.size) {
            val target = targets[stepInternal]

            if (!isAfterPublishDate(service.endDate, target, service.target)) {
                stepInternal++
                try {
                    return "Empty0"
                } finally {
                    service.resumeServiceDelay(event, 10L)
                }
            }
            if (!isBeforePublishDate(service.startDate, target, service.target)) {
                return "WriteXueXiArticle"
            }

            if (!target.click()) return actionName
            stepInternal++
            return "GetXueXiArticleInfoCompat"
        } else {
            if (!listView.scrollForward()) {
                return "WriteXueXiArticle"
            }
            Thread.sleep(1000L)
            stepInternal = 0
            return actionName
        }
    }

    private fun isBeforePublishDate(
        startDate: Long,
        itemView: AccessibilityNodeInfo,
        targetAccount: String
    ): Boolean {
        if (startDate == Long.MIN_VALUE) return true

        val releaseInfo = itemView.findNodeOrNull {
            viewIdResourceName == RELEASE_INFO_ID
        } ?: return true

        val target = releaseInfo.findNodes {
            className == CLS_TEXT_VIEW
        }.lastOrNull { node ->
            node.text?.let { char ->
                return@lastOrNull char.toString() != targetAccount
            }
            return@lastOrNull false
        } ?: return true

        return xueXiTimeFormat(target.text.toString()) >= startDate
    }

    private fun isAfterPublishDate(
        endDate: Long,
        itemView: AccessibilityNodeInfo,
        targetAccount: String
    ): Boolean {
        // 大于当前时间
        if (endDate >= System.currentTimeMillis()) return true

        val releaseInfo = itemView.findNodeOrNull {
            viewIdResourceName == RELEASE_INFO_ID
        } ?: return false

        val target = releaseInfo.findNodes {
            className == CLS_TEXT_VIEW
        }.lastOrNull { node ->
            node.text?.let { char ->
                return@lastOrNull char.toString() != targetAccount
            }
            return@lastOrNull false
        } ?: return true

        target.text?.toString()?.let {
            return xueXiTimeFormat(it) <= endDate
        }

        return false
    }

}