package com.example.weibo.login

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.common.util.startWeiboScanActivityOnly
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action

internal class StartWeiboScanActivity : Action {

    override val actionName: String = "StartWeiboScanActivity"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        service.startWeiboScanActivityOnly()
        Thread.sleep(1000L)
        return  "ClickAlbum"
    }

}