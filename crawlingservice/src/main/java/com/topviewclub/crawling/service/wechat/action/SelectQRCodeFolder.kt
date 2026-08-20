package com.topviewclub.crawling.service.wechat.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull

class SelectQRCodeFolder : Action {

    private companion object {
        private const val QRCODE_TEXT = "QRCode"
        private const val CLOSE_FOLDER_ID = "com.tencent.mm:id/f5"
    }

    override val actionName: String = "SelectQRCodeFolder"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName

        val target = root.findNodeOrNull {
            text?.toString() == QRCODE_TEXT
        } ?: return actionName

        target.parent.parent.click()

        Thread.sleep(1000L)

//        val close = root.findNodeOrNull {
//            viewIdResourceName == CLOSE_FOLDER_ID
//        } ?: return step
//
//        close.click()

        return "SelectPhoto"
    }
}