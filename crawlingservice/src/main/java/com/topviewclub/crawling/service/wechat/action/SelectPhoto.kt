package com.topviewclub.crawling.service.wechat.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.common.log.logI

/**
 * 选择相册第一张图片
 * */
internal class SelectPhoto : Action {

    private companion object {
        private const val SELECT_PHOTO_DESCRIPTION = "图片1"
        // AlbumPreviewUI 第一格是“拍摄照片”，任务二维码位于其右侧第一格。
        // Xiaomi 22041216C / 微信 8.0.76 的四列网格中，该格中心精确为 (405, 365)，
        // 对应 1080x2460 比率为 0.375f, 0.15f；这里只在图片节点没有暴露时使用此无障碍手势兜底。
        private const val FIRST_PHOTO_X_RATIO = 0.375f
        private const val FIRST_PHOTO_Y_RATIO = 0.15f
    }

    override val actionName: String = "SelectPhoto"

    private val retryState = PickerRetryState(actionName)
    private var photoSelected = false
    private var lastPhotoSelectedAt = 0L

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        // 选择图片后，只要离开相册页即进入扫码结果处理（兼容返回 BaseScanUI 或直接打开文章 WebView）。
        val isGallery = service.isWechatPhotoPickerContext() || service.isGalleryPickerVisible(event)
        if (photoSelected && !isGallery) {
            photoSelected = false
            lastPhotoSelectedAt = 0L
            retryState.reset()
            logI(actionName, "已确认离开微信相册页，进入扫码结果处理: event=${event.uiClassName()}")
            return "EnterOfficialHome"
        }

        if (photoSelected) {
            val elapsed = System.currentTimeMillis() - lastPhotoSelectedAt
            if (elapsed > 2000L) {
                logI(actionName, "选图点击已超时(${elapsed}ms)仍停留在相册页，重置状态允许重新分发点击")
                photoSelected = false
            } else {
                retryState.scheduleProbe(service, event)
                return actionName
            }
        }

        if (!service.isWechatPhotoPickerContext() && !service.isGalleryPickerVisible(event)) {
            retryState.scheduleProbe(service, event)
            return actionName
        }

        // 优先尝试原生无障碍节点匹配（兼容微信 8.0.18 等经典老版本及标准 View 层次）
        val root = service.rootInActiveWindow
        val target = root?.findNodeOrNull {
            contentDescription?.let {
                val length = SELECT_PHOTO_DESCRIPTION.length
                it.length >= length && it.subSequence(0, length) == SELECT_PHOTO_DESCRIPTION
            } ?: false
        }
        if (target != null) {
            val clicked = target.click() || target.parent?.click() == true || target.parent?.parent?.click() == true
            if (clicked) {
                photoSelected = true
                lastPhotoSelectedAt = System.currentTimeMillis()
                logI(actionName, "已通过原生节点选中第一张图片: ${target.contentDescription}")
                if (WechatVersionCompat.isLegacyRoute(service)) {
                    // 老版本微信（如 8.0.18）在原生节点点击后直接触发扫码跳转
                    Thread.sleep(1000L)
                    return "EnterOfficialHome"
                }
                retryState.scheduleProbe(service, event)
                return actionName
            }
        }

        // 自绘 View 兜底（微信 8.0.76 等新版本）：第一格固定是“拍摄照片”，任务二维码在其右侧第一格；
        // 使用无障碍服务手势点击该格。
        val dispatched = retryState.dispatch(
            service,
            event,
            FIRST_PHOTO_X_RATIO,
            FIRST_PHOTO_Y_RATIO,
        )
        photoSelected = dispatched
        if (dispatched) {
            lastPhotoSelectedAt = System.currentTimeMillis()
        }
        logI(actionName, "微信图片节点不可见，提交无障碍坐标手势 accepted=$dispatched")
        return actionName
    }
}
