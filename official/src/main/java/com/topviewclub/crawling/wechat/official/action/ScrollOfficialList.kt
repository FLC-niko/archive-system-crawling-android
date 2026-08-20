package com.topviewclub.crawling.wechat.official.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action

class ScrollOfficialList : Action {

    override val actionName: String = "ScrollOfficialList"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName
        val recyclerView = root.findNodeOrNull {
            className == CLS_RECYCLER_VIEW
        } ?: return actionName
        recyclerView.scrollForward()
        Thread.sleep(200L)
        service.resumeServiceDelay(event, 0L)
        return "CheckOfficialEndDate"
    }

}