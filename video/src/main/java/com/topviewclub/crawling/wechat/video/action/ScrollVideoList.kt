package com.topviewclub.crawling.wechat.video.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action

class ScrollVideoList : Action {

    private companion object {
        private const val BOTTOM_FLAG_ID = "com.tencent.mm:id/g2z"
    }

    override val actionName: String = "ScrollVideoList"

    private var isPreviousStep = false
    private var isScrolling = false

    private val gestureResultCallback = object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription) {
            isPreviousStep = true
            isScrolling = false
        }

        override fun onCancelled(gestureDescription: GestureDescription) {
            isPreviousStep = false
            isScrolling = false
        }
    }

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        // 正在滑动，不做处理
        if (isScrolling) return actionName
        // 拿到根节点
        val root = service.rootInActiveWindow ?: return actionName
        // 判断是否到底，到底说明抓取结束
        if (isBottom(root)) return "WriteVideoInfo"

        // 回到获取视频发布时间那一步（上一步）
        if (isPreviousStep) {
            isPreviousStep = false
            return "GetVideoInfo"
        }
        // 开始匹配并执行操作
        matchAndScroll(service, root)
        return actionName
    }

    private fun matchAndScroll(
        service: AutoOperationService,
        root: AccessibilityNodeInfo
    ) {
        val recyclerView = root.findNodeOrNull {
            className == CLS_RECYCLER_VIEW
        } ?: return

        if (recyclerView.scrollForward()) {
            isPreviousStep = true
        } else {
            // 获取 ItemView 的高度
            val bound = Rect()
            recyclerView.getBoundsInScreen(bound)
            // 滑动
            scroll(service, bound.right - bound.left, bound.bottom - bound.top)
        }
    }

    private fun scroll(
        service: AutoOperationService,
        width: Int,
        height: Int
    ) {
        isScrolling = true
        service.scroll(
            width * 0.5f, height * 0.9f,
            width * 0.5f, height * 0.1f,
            duration = 800L,
            callback = gestureResultCallback
        )
    }

    private fun isBottom(root: AccessibilityNodeInfo): Boolean {
        return root.findNodeOrNull { viewIdResourceName == BOTTOM_FLAG_ID } != null
    }

}