package com.topviewclub.crawling.wechat.official.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action

class HomingOfficialList : Action {

    private companion object {
        private const val TITLE_BAR_ID = "com.tencent.mm:id/dn"
        private const val OFFICIAL_INFO_ID = "com.tencent.mm:id/aal"
    }

    override val actionName: String = "HomingOfficialList"

    private val bound = Rect()

    private var isScrolling = false
    private var isCompleted = false

    private val gestureResultCallback = object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription) {
            isScrolling = false
            isCompleted = true
        }

        override fun onCancelled(gestureDescription: GestureDescription) {
            isScrolling = false
            isCompleted = false
        }
    }

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        // 还在滑动
        if (isScrolling) return actionName
        if (isCompleted) return "CheckOfficialEndDate"
        val root = service.rootInActiveWindow ?: return actionName
        // 开始匹配并滑动
        matchAndScroll(service, root)
        return actionName
    }

    private fun matchAndScroll(
        service: AutoOperationService,
        root: AccessibilityNodeInfo
    ) {
        val titleBar = root.findNodeOrNull {
            className == CLS_FRAME_LAYOUT
                    && viewIdResourceName == TITLE_BAR_ID
        } ?: return
        titleBar.getBoundsInScreen(bound)
        val top = bound.bottom
        val tabBar = root.findNodeOrNull {
            className == CLS_LINEAR_LAYOUT
                    && viewIdResourceName == OFFICIAL_INFO_ID
        } ?: return
        tabBar.getBoundsInScreen(bound)
        val bottom = bound.bottom
        // 要移动的距离
        val dest = bottom - top + 50
        val recyclerView = root.findNodeOrNull {
            className == CLS_RECYCLER_VIEW
        } ?: return
        recyclerView.getBoundsInScreen(bound)
        val width = bound.right - bound.left
        // 初始点击位置
        val startY = (bound.bottom + bound.top) / 2f
        // 移动的目标位置
        val endY = startY - dest
        isScrolling = true
        service.scroll(
            width / 2f, startY,
            width / 2f, endY,
            duration = 2000L,
            callback = gestureResultCallback
        )
    }

}