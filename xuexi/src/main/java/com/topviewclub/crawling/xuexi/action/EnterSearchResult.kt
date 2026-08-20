package com.topviewclub.crawling.xuexi.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action

class EnterSearchResult : Action {

    private companion object {
        private const val RECYCLERVIEW_ID = "cn.xuexi.android:id/recyclerview"
    }

    override val actionName: String = "EnterSearchResult"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName
        val recyclerview = root.findNodeOrNull {
            viewIdResourceName == RECYCLERVIEW_ID
        } ?: return actionName
        if (recyclerview.childCount == 0) {
            return AutoOperationService.ActionType.ActionSuccess
        }
        val target = recyclerview
            .getChild(0) ?: return AutoOperationService.ActionType.ActionSuccess
        target.click()
        try {
            return "EnterAccountHome"
        } finally {
            service.resumeServiceDelay(event, 5000L)
        }
    }

}