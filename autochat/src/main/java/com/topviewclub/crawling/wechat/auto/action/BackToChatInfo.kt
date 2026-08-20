package com.topviewclub.crawling.wechat.auto.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.common.base.wechatVideoCacheCaptor
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.back

class BackToChatInfo : Action {

    override val actionName: String = "BackToChatInfo"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        service.back()
        Thread.sleep(1000L)
        wechatVideoCacheCaptor.removeAllVideosFromWechat()
        return "EnterVideo"
    }

}