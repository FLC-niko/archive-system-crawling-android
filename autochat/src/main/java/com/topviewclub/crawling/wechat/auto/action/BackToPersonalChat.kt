package com.topviewclub.crawling.wechat.auto.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.common.base.wechatVideoCacheCaptor
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.wechat.auto.AutoChatOperationService

class BackToPersonalChat : Action {

    private companion object {
        private const val BACK_ID = "com.tencent.mm:id/a33"
        private const val COMMENT_ID = "com.tencent.mm:id/bjp"
        private const val TITLE_ID = "com.tencent.mm:id/bga"
        private const val BACK_COMMENT = "com.tencent.mm:id/be_"

    }

    override val actionName: String = "BackToPersonalChat"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName

        val comment = root.findNodeOrNull {
            viewIdResourceName == COMMENT_ID && isClickable
        }?: return actionName

        Thread.sleep(1000L)
        comment.click()

        val title = root.findNodeOrNull {
            viewIdResourceName == TITLE_ID && className == CLS_TEXT_VIEW
        } ?: return actionName

        val commentBack = root.findNodeOrNull {
            viewIdResourceName == BACK_COMMENT && isClickable
        } ?: return actionName

        commentBack.click()

        val back = root.findNodeOrNull {
            viewIdResourceName == BACK_ID && isClickable
        } ?: return actionName
        AutoChatOperationService.title = title.text.let {
            if (it == null) {
                ""
            } else {
                if (it.length > 200) {
                    it.substring(0, 200)
                } else {
                    it.toString()
                }
            }
        }

        Thread.sleep(1000L)
        back.click()
        return "ScanRequestCode"
    }

}