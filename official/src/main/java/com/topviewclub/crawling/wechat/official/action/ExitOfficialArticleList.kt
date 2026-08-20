package com.topviewclub.crawling.wechat.official.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.common.util.startWechatScanActivityOnly
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.back

internal class ExitOfficialArticleList : Action {

    override val actionName: String = "ExitOfficialArticleList"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        // 返回上一级界面
        service.back()
        service.startWechatScanActivityOnly()
        service.resumeServiceDelay(event, 0L)
        return "EnterWechatLauncher"
    }

}