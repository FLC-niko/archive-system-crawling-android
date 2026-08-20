package com.topviewclub.crawling.xuexi.action

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull

class EnterSearchActivity : Action {

    private companion object {
        private const val SEARCH_ID = "cn.xuexi.android:id/tv_search_marquee"
    }

    override val actionName: String = "EnterSearchActivity"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName
        return if (match(root)) "ScanTargetAccount" else actionName
    }

    private fun match(root: AccessibilityNodeInfo): Boolean {
        val target = root.findNodeOrNull {
            viewIdResourceName == SEARCH_ID
        } ?: return false
        return target.click()
    }

}