package com.topviewclub.crawling.xuexi.action

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.crawling.service.text

class ScanTargetAccount : Action {

    private companion object {
        private const val SCAN_BOX_ID = "android:id/search_src_text"
    }

    override val actionName: String = "ScanTargetAccount"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName
        try {
            return if (match(root, service.target)) "EnterSearchResult" else actionName
        } finally {
            service.resumeServiceDelay(event, 100L)
        }
    }

    private fun match(root: AccessibilityNodeInfo, targetAccount: String): Boolean {
        val target = root.findNodeOrNull {
            viewIdResourceName == SCAN_BOX_ID
        } ?: return false
        return target.text(targetAccount)
    }

}