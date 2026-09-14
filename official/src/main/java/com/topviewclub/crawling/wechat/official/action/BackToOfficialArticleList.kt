package com.topviewclub.crawling.wechat.official.action

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.back
import com.topviewclub.crawling.service.tap
import com.topviewclub.common.log.logI
import com.topviewclub.common.log.logW
import com.topviewclub.crawling.wechat.official.OfficialPageDetector
import android.os.Handler
import android.os.SystemClock

class BackToOfficialArticleList : Action {

    override val actionName: String = "BackToOfficialArticleList"

    @Volatile
    private var backRequested = false

    @Volatile
    private var earliestConfirmAt = 0L

    @Volatile
    private var confirmWakeScheduled = false

    @Volatile
    private var backAttemptTime = 0L

    @Volatile
    private var retryCount = 0

    @Volatile
    private var captureInFlight = false

    @Volatile
    private var pendingNextAction: String? = null

    companion object {
        private const val CONFIRM_SETTLE_DELAY_MS = 600L
        private const val BACK_TIMEOUT_MS = 2500L
        private const val MAX_RETRY_COUNT = 2

        // 微信左上角返回按钮常见物理坐标 (X: 75, Y: 155)
        private const val BACK_BUTTON_X = 75f
        private const val BACK_BUTTON_Y = 155f
    }

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        // 0. 如果已确认下一步，直接跳转目标 Action
        pendingNextAction?.let { next ->
            pendingNextAction = null
            resetState()
            logI(actionName, "已确认完成返回流程，下一步: $next")
            service.resumeCurrentAction()
            return next
        }

        val currentActivity = service.currentWechatActivity.orEmpty()
        val pageClass = event.className?.toString().orEmpty()
        val isContactInfo = currentActivity.contains("ContactInfoUI", ignoreCase = true) ||
                currentActivity.contains("BizContactInfoUI", ignoreCase = true) ||
                pageClass.contains("ContactInfoUI", ignoreCase = true) ||
                pageClass.contains("BizContactInfoUI", ignoreCase = true)
        val inWebView = currentActivity.contains("WebView", ignoreCase = true) ||
                currentActivity.contains("TmplWebViewMMUI", ignoreCase = true) ||
                pageClass.contains("WebView", ignoreCase = true) ||
                pageClass.contains("TmplWebViewMMUI", ignoreCase = true)

        if (isContactInfo && !inWebView) {
            logI(actionName, "当前已处于公众号主页/列表页 ($currentActivity / $pageClass)，无需/终止返回，直接进入 CheckOfficialEndDate")
            resetState()
            service.resumeCurrentAction()
            return "CheckOfficialEndDate"
        }

        val now = SystemClock.uptimeMillis()

        // 1. 还未发出返回请求，仅执行单次返回动作
        if (!backRequested) {
            backRequested = true
            backAttemptTime = now
            earliestConfirmAt = now + CONFIRM_SETTLE_DELAY_MS
            performBackAction(service)
            service.resumeServiceDelay(event, 150L)
            return actionName
        }

        // 2. 超时重试逻辑（受 MAX_RETRY_COUNT 严格限制，防止过度后退导致退到桌面）
        if (now - backAttemptTime > BACK_TIMEOUT_MS) {
            if (retryCount < MAX_RETRY_COUNT) {
                retryCount++
                logW(actionName, "返回等待超时 (尝试 $retryCount/$MAX_RETRY_COUNT)，重新尝试返回")
                backAttemptTime = now
                earliestConfirmAt = now + CONFIRM_SETTLE_DELAY_MS
                performBackAction(service)
                service.resumeServiceDelay(event, 200L)
                return actionName
            } else {
                logW(actionName, "返回重试达上限，直接转入 CheckOfficialEndDate 尝试恢复")
                resetState()
                service.resumeCurrentAction()
                return "CheckOfficialEndDate"
            }
        }

        // 3. 等待页面过渡稳定
        if (now < earliestConfirmAt) {
            if (!confirmWakeScheduled) {
                confirmWakeScheduled = true
                Handler(service.mainLooper).postDelayed({
                    confirmWakeScheduled = false
                    service.resumeCurrentAction()
                }, earliestConfirmAt - now)
            }
            return actionName
        }

        // 4. 快速检查 UI 节点与类名
        val root = service.rootInActiveWindow

        // 4.1 明确处于列表页
        val isContactInfoUI = pageClass.contains("ContactInfoUI", ignoreCase = true) ||
                pageClass.contains("BizContactInfoUI", ignoreCase = true) ||
                currentActivity.contains("ContactInfoUI", ignoreCase = true) ||
                currentActivity.contains("BizContactInfoUI", ignoreCase = true)
        val isOfficialListUI = root != null && root.childCount > 0 &&
                root.findNodeOrNull { className == "androidx.recyclerview.widget.RecyclerView" } != null

        if (isContactInfoUI || isOfficialListUI) {
            logI(actionName, "已通过类名/节点确认回到列表页: ContactInfoUI=$isContactInfoUI, ListUI=$isOfficialListUI")
            resetState()
            service.resumeServiceDelay(event, 0L)
            return "CheckOfficialEndDate"
        }

        // 4.2 如果节点树为空或处于 WebView/自绘，使用 OCR 进行精准判定
        if (!captureInFlight) {
            captureInFlight = true
            OfficialScreenReader.recognize(
                service = service,
                onSuccess = { lines ->
                    captureInFlight = false
                    val inArticle = OfficialPageDetector.isArticleDetailPage(lines, root, pageClass, currentActivity)
                    val inList = OfficialPageDetector.isOfficialListPage(lines, root, pageClass, currentActivity)

                    logI(actionName, "OCR 确认页面状态: inArticle=$inArticle, inList=$inList, lines=${lines.size}")

                    if (inList) {
                        // 确认回到列表，严禁再按任何返回键，直接设置下一步目标
                        logI(actionName, "OCR 确认已成功回到公众号列表页，转入 CheckOfficialEndDate")
                        pendingNextAction = "CheckOfficialEndDate"
                        service.resumeCurrentAction()
                    } else if (inArticle) {
                        // 确认仍停留在文章中，才按需触发重试
                        if (retryCount < MAX_RETRY_COUNT) {
                            retryCount++
                            logW(actionName, "OCR 确认仍停留在文章详情页 (尝试 $retryCount/$MAX_RETRY_COUNT)，发起再次返回")
                            backAttemptTime = SystemClock.uptimeMillis()
                            earliestConfirmAt = SystemClock.uptimeMillis() + CONFIRM_SETTLE_DELAY_MS
                            performBackAction(service)
                        } else {
                            logW(actionName, "文章返回重试已达上限，转入 CheckOfficialEndDate 兜底")
                            pendingNextAction = "CheckOfficialEndDate"
                        }
                        service.resumeCurrentAction()
                    } else {
                        // 未知/过渡页面：严禁盲目按返回键，保持观察并等待下一事件
                        service.resumeCurrentAction()
                    }
                },
                onFailure = {
                    captureInFlight = false
                    logW(actionName, "OCR 检测失败: ${it.message}，保持等待")
                    service.resumeServiceDelay(event, 300L)
                }
            )
        }

        return actionName
    }

    private fun performBackAction(service: AutoOperationService) {
        val rootPkg = service.rootInActiveWindow?.packageName?.toString()
        if (rootPkg != null && rootPkg != "com.tencent.mm") {
            logW(actionName, "当前前台包名为 $rootPkg，非微信，阻止发送返回键")
            return
        }
        // 双重返回机制：首选左上角返回按钮坐标，次选全局返回，杜绝过度返回退到桌面
        logI(actionName, "执行精准返回：模拟点击微信左上角返回按钮 (${BACK_BUTTON_X}, ${BACK_BUTTON_Y})")
        val tapped = service.tap(BACK_BUTTON_X, BACK_BUTTON_Y)
        if (!tapped) {
            logI(actionName, "左上角手势未接受，执行服务返回键 service.back()")
            service.back()
        }
    }

    private fun resetState() {
        backRequested = false
        earliestConfirmAt = 0L
        confirmWakeScheduled = false
        backAttemptTime = 0L
        retryCount = 0
        captureInFlight = false
        pendingNextAction = null
    }

    private fun AccessibilityNodeInfo.findNodeOrNull(
        predicate: AccessibilityNodeInfo.() -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate()) return this
        for (i in 0 until childCount) {
            val child = getChild(i) ?: continue
            val result = child.findNodeOrNull(predicate)
            if (result != null) return result
        }
        return null
    }
}