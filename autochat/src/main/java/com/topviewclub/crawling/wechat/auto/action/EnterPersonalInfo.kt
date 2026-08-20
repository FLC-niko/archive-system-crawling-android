package com.topviewclub.crawling.wechat.auto.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action

class EnterPersonalInfo : Action {

    private companion object {
        private const val IMAGE_ID = "com.tencent.mm:id/iwl"
    }

    override val actionName: String = "EnterPersonalInfo"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName

        val img = root.findNodeOrNull {
            viewIdResourceName == IMAGE_ID
                    && className == CLS_RELATIVE_LAYOUT
                    && isClickable
        } ?: return actionName

        img.click()

        return "GetWechatNumberAndName"
    }
}