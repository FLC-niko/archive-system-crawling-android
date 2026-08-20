package com.topviewclub.crawling.service.wechat.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.common.util.startWechatScanActivityOnly
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action

/**
 * 打开微信扫一扫
 * */
class StartWechatScanActivity : Action {

    override val actionName: String = "StartWechatScanActivity"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        service.startWechatScanActivityOnly()
        Thread.sleep(1000L)
        service.resumeServiceDelay(event, 100L)
        return "ClickAlbum"
    }

}