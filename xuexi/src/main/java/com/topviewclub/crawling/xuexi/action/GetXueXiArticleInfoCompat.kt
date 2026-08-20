package com.topviewclub.crawling.xuexi.action

import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.xuexi.XueXiIMMActivityCompat

class GetXueXiArticleInfoCompat : Action {

    companion object {
        private const val TITLE_BAR_ID = "cn.xuexi.android:id/TOP_LAYER_VIEW_ID"
        private const val VIEW_PAGER_ID = "cn.xuexi.android:id/pager"

        private const val COPY_URL_TEXT = "复制链接"

        internal var isIMMActivityDestroyed = false
    }

    override val actionName: String = "GetXueXiArticleInfoCompat"

    private var stepInternal = 0

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName

        val res = when (stepInternal) {
            0 -> clickMoreInfo(root)
            1 -> scrollViewPager(root)
            2 -> copyUrl(service, event, root)
            3 -> backToList(service, event)
            else -> false
        }
        return if (res) "EnterXueXiArticle" else actionName
    }

    private fun clickMoreInfo(root: AccessibilityNodeInfo): Boolean {
        val titleBar = root.findNodeOrNull {
            viewIdResourceName == TITLE_BAR_ID
        } ?: return false

        val target = titleBar.findNodes {
            className == CLS_IMAGE_VIEW
        }.last()

        if (target.click()) stepInternal++

        return false
    }

    private fun scrollViewPager(root: AccessibilityNodeInfo): Boolean {
        val pager = root.findNodeOrNull {
            viewIdResourceName == VIEW_PAGER_ID
        } ?: return false

        if (pager.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) stepInternal++

        return false
    }

    private fun copyUrl(
        service: AutoOperationService,
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo
    ): Boolean {
        val target = root.findNodeOrNull {
            text?.toString() == COPY_URL_TEXT
        }?.findSuperNodeOrNull { isClickable } ?: return false
        if (target.click()) {
            Thread.sleep(500L)
            service.startActivity(Intent(service, XueXiIMMActivityCompat::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            stepInternal++
        }
        service.resumeServiceDelay(event, 500L)
        return false
    }

    private fun backToList(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): Boolean {
        if (!isIMMActivityDestroyed) {
            service.resumeServiceDelay(event, 500L)
            return false
        }
        if (service.back()) {
            isIMMActivityDestroyed = false
            stepInternal = 0
            return true
        }
        return false
    }

}