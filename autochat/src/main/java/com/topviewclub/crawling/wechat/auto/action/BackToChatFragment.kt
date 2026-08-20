package com.topviewclub.crawling.wechat.auto.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull

class BackToChatFragment : Action {

    private companion object {
        private const val BACK_ID = "com.tencent.mm:id/fz"
    }

    override val actionName: String = "BackToChatFragment"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val back = service.rootInActiveWindow ?.findNodeOrNull {
            viewIdResourceName == BACK_ID
        } ?: return actionName

        back.click()
        Thread.sleep(1000L)
        return "EnterPersonalChat"
    }
}