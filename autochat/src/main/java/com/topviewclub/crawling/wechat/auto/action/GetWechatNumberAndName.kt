package com.topviewclub.crawling.wechat.auto.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.wechat.auto.AutoChatOperationService

class GetWechatNumberAndName : Action {

    private companion object {
        private const val WECHAT_NICK_NAME_ID = "com.tencent.mm:id/bq0"

        private const val WECHAT_NAME_TEXT = "昵称: "
        private const val WECHAT_NAME_ID = "com.tencent.mm:id/bpz"

        private const val WECHAT_NUMBER_TEXT = "微信号: "
        private const val WECHAT_NUMBER_ID = "com.tencent.mm:id/bq8"
    }

    override val actionName: String = "GetWechatNumberAndName"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName

        val numberTextView = root.findNodeOrNull {
            text?.toString()?.let {
                it.length >= 5 && it.substring(0, 5) == WECHAT_NUMBER_TEXT
            } ?: false
                    && viewIdResourceName == WECHAT_NUMBER_ID
        } ?: return actionName

        val number = numberTextView.text.toString().substring(6)

        val nameTextView = root.findNodeOrNull {
            text?.toString()?.let {
                it.length >= 4 && it.substring(0, 4) == WECHAT_NAME_TEXT
            } ?: false
                    && viewIdResourceName == WECHAT_NAME_ID
        }
        val name = if (nameTextView != null) {
            nameTextView.text.toString().substring(5)
        } else {
            val nickNameTextView = root.findNodeOrNull {
                viewIdResourceName == WECHAT_NICK_NAME_ID
            }
            nickNameTextView?.text?.toString() ?: "未知昵称"
        }

        AutoChatOperationService.nameOfWechat = name
        AutoChatOperationService.numberOfWechat = number

        service.back()
        Thread.sleep(1000L)

        return "BackToChatInfo"
    }

}