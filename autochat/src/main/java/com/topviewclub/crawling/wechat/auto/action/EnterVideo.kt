package com.topviewclub.crawling.wechat.auto.action

import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.wechat.auto.AutoChatOperationService
import com.topviewclub.crawling.wechat.auto.room.acv.REQUEST_TYPE_UNDEFINED
import com.topviewclub.crawling.wechat.auto.room.acv.randomWechatVideoType
import kotlinx.coroutines.delay

class EnterVideo : Action {

    private companion object {
        private const val ITEM_ID = "com.tencent.mm:id/b4_"
        private const val VIDEO_ID = "com.tencent.mm:id/b47"
        private const val IMAGE_ID = "com.tencent.mm:id/b6k"
    }

    override val actionName: String = "EnterVideo"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        AutoChatOperationService.requestWechatType = REQUEST_TYPE_UNDEFINED

        val root = service.rootInActiveWindow ?: return actionName
        val items = root.findNodes {
            isEnabled && viewIdResourceName == ITEM_ID
                    && className == CLS_LINEAR_LAYOUT
        }

        if (items.isEmpty()) return "ScanRequestCode"

        val target = items.last().findNodeOrNull {
            viewIdResourceName == VIDEO_ID
                    && isClickable
                    && findNodeOrNull { viewIdResourceName == IMAGE_ID && className == CLS_IMAGE_VIEW } != null
        } ?: return "ScanRequestCode"

        AutoChatOperationService.requestWechatType = randomWechatVideoType()
        target.click()
        val videoStartTime = SystemClock.uptimeMillis()
        while (true) {
            val videoIntervalTime = SystemClock.uptimeMillis() - videoStartTime
            val url = AutoChatOperationService.videoURL
            if (url != null || videoIntervalTime > 10000L) {
                return "BackToPersonalChat"
            }
        }

    }

}