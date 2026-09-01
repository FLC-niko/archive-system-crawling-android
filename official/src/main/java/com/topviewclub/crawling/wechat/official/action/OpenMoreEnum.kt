package com.topviewclub.crawling.wechat.official.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.crawling.service.tap
import com.topviewclub.common.log.logI
import android.os.Handler
import android.os.SystemClock

class OpenMoreEnum : Action {

    private companion object {
        private const val MORE_INFO_DES = "更多信息"
        private const val MORE_INFO_ID = "com.tencent.mm:id/en"
        private const val ARTICLE_SETTLE_DELAY_MS = 1000L
        private const val MENU_SETTLE_DELAY_MS = 900L
    }

    override val actionName: String = "OpenMoreEnum"

    @Volatile
    private var moreRequested = false

    @Volatile
    private var menuReady = false

    @Volatile
    private var earliestOpenAt = 0L

    @Volatile
    private var settleWakeScheduled = false

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        if (menuReady) {
            resetState()
            service.resumeCurrentAction()
            return "CopyOfficialArticleURL"
        }

        // 微信刚发出 TmplWebViewMMUI 的窗口事件时，Activity 已切换，但顶部工具栏
        // 尚未稳定。先等待页面完成布局，否则坐标会落到文章正文上。
        val now = SystemClock.uptimeMillis()
        if (earliestOpenAt == 0L) {
            earliestOpenAt = now + ARTICLE_SETTLE_DELAY_MS
        }
        if (now < earliestOpenAt) {
            if (!settleWakeScheduled) {
                settleWakeScheduled = true
                Handler(service.mainLooper).postDelayed({
                    settleWakeScheduled = false
                    service.resumeCurrentAction()
                }, earliestOpenAt - now)
            }
            return actionName
        }

        if (moreRequested) return actionName

        val target = service.windows.asSequence()
            .mapNotNull { it.root }
            .mapNotNull { root ->
                root.findNodeOrNull {
                    isClickable &&
                            (contentDescription?.toString() == MORE_INFO_DES ||
                                    text?.toString() == MORE_INFO_DES) &&
                            (viewIdResourceName == MORE_INFO_ID ||
                                    viewIdResourceName.isNullOrEmpty())
                }
            }
            .firstOrNull()

        val nodeClicked = target != null &&
                (target.click() || target.parent?.click() == true)
        if (nodeClicked) {
            moreRequested = true
            logI(actionName, "通过无障碍节点打开文章菜单")
            scheduleMenuReady(service)
        } else {
            // TmplWebViewMMUI 在当前微信版本同样不暴露节点。右上角三点中心
            // 约为物理屏幕 (1002, 165)，由无障碍手势打开文章菜单。
            val dispatched = service.tap(1002f, 165f)
            moreRequested = dispatched
            logI(actionName, "打开文章菜单无障碍手势 accepted=$dispatched")
            if (dispatched) {
                scheduleMenuReady(service)
            } else {
                Handler(service.mainLooper).postDelayed(
                    { service.resumeCurrentAction() },
                    300L,
                )
            }
        }
        // 动作必须让出处理线程；找不到节点时依靠下一条事件/心跳继续或超时收口。
        return actionName
    }

    private fun scheduleMenuReady(service: AutoOperationService) {
        Handler(service.mainLooper).postDelayed({
            menuReady = true
            moreRequested = false
            service.resumeCurrentAction()
        }, MENU_SETTLE_DELAY_MS)
    }

    private fun resetState() {
        menuReady = false
        moreRequested = false
        earliestOpenAt = 0L
        settleWakeScheduled = false
    }
}
