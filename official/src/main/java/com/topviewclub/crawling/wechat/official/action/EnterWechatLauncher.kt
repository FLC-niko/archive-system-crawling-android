package com.topviewclub.crawling.wechat.official.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.crawling.service.back
import com.topviewclub.crawling.service.tap
import com.topviewclub.common.log.logI

internal class EnterWechatLauncher : Action {

    private companion object {
        private const val SCAN_CLOSE_X = 80f
        private const val SCAN_CLOSE_Y = 175f
        private const val MAX_EXIT_ATTEMPTS = 6
        private val BACK_IMAGE_DESCRIPTIONS = setOf("关闭", "返回", "Back", "Close")
    }

    override val actionName: String = "EnterWechatLauncher"
    private var exitAttempts = 0

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val currentActivity = service.currentWechatActivity.orEmpty()
        val pageClass = event.className?.toString().orEmpty()

        if (currentActivity.contains("LauncherUI", ignoreCase = true) ||
            pageClass.contains("LauncherUI", ignoreCase = true)
        ) {
            logI(actionName, "已成功回到微信 LauncherUI")
            exitAttempts = 0
            service.resumeServiceDelay(event, 0L)
            return AutoOperationService.ActionType.ActionSuccess
        }

        if (currentActivity.contains("BaseScanUI", ignoreCase = true) ||
            pageClass.contains("BaseScanUI", ignoreCase = true)
        ) {
            logI(actionName, "检测到 BaseScanUI，点击左上角关闭按钮 ($SCAN_CLOSE_X, $SCAN_CLOSE_Y)")
            service.tap(SCAN_CLOSE_X, SCAN_CLOSE_Y)
            service.resumeServiceDelay(event, 400L)
            return actionName
        }

        val target = service.rootInActiveWindow?.findNodeOrNull {
            val desc = contentDescription?.toString().orEmpty()
            desc in BACK_IMAGE_DESCRIPTIONS
        }
        if (target != null && target.click()) {
            service.resumeServiceDelay(event, 300L)
            return actionName
        }

        val inWechat = currentActivity.startsWith("com.tencent.mm") ||
                service.rootInActiveWindow?.packageName?.toString() == "com.tencent.mm"
        if (inWechat && exitAttempts < MAX_EXIT_ATTEMPTS) {
            exitAttempts++
            logI(actionName, "尚未回到 LauncherUI (当前 activity=$currentActivity)，执行返回键 (尝试 $exitAttempts/$MAX_EXIT_ATTEMPTS)")
            service.back()
            service.resumeServiceDelay(event, 350L)
            return actionName
        }

        exitAttempts = 0
        service.resumeServiceDelay(event, 0L)
        return AutoOperationService.ActionType.ActionSuccess
    }

}