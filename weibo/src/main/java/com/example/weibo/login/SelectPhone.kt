package com.example.weibo.login

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.crawling.service.wechat.action.SelectPhoneOrOpenFolderList

internal class SelectPhone : Action {
    private companion object {
        private const val OPEN_FOLDER_ID = "com.tencent.mm:id/f5"
        private const val SELECT_PHOTO_DESCRIPTION = "图片1"
    }
    override val actionName: String = "SelectPhone"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName

        val target = root.findNodeOrNull {
            viewIdResourceName == OPEN_FOLDER_ID
        } ?: return actionName

        val photo = root.findNodeOrNull {
            contentDescription?.let {
                val length = SELECT_PHOTO_DESCRIPTION.length
                it.length > length
                        && it.subSequence(0, length) == SELECT_PHOTO_DESCRIPTION
            } ?: false
        }

        if (photo != null) {
            photo.parent.click()
            return ""
        }

        target.click()
        Thread.sleep(1000L)
        return "SelectQRCodeFolder"
    }
}