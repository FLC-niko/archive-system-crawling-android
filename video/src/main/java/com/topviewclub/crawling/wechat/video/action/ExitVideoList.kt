package com.topviewclub.crawling.wechat.video.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.back

class ExitVideoList : Action {

    override val actionName: String = "ExitVideoList"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        // 返回上一级界面
        service.back()
        service.resumeServiceDelay(event, 250L)
        return "ReturnToWechatLauncher"
    }

}
