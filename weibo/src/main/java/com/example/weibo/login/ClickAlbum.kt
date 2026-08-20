package com.example.weibo.login

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull

internal class ClickAlbum : Action {
    private companion object {
        private const val CLICK_ALBUM_DESCRIPTION = "相册，按钮"
    }

    override val actionName: String = "ClickAlbum"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName
        val target = root.findNodeOrNull {
            contentDescription == CLICK_ALBUM_DESCRIPTION
        } ?: return actionName
        target.click()
        Thread.sleep(1000L)
        return "SelectPhone"
    }
}