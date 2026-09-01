package com.topviewclub.crawling.service.wechat.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.common.log.logI

/**
 * 选择相册第一张图片
 * */
internal class SelectPhoto : Action {

    private companion object {
        // AlbumPreviewUI 第一格是“拍摄照片”，任务二维码位于其右侧第一格。
        // Xiaomi 22041216C / 微信 8.0.76 的四列网格中，该格中心约为屏幕宽度
        // 0.375、屏幕高度 0.125；这里只在图片节点没有暴露时使用此无障碍手势兜底。
        private const val FIRST_PHOTO_X_RATIO = 0.375f
        private const val FIRST_PHOTO_Y_RATIO = 0.125f
    }

    override val actionName: String = "SelectPhoto"

    private val retryState = PickerRetryState(actionName)
    private var photoSelected = false

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        // 选择图片后，只有收到返回 BaseScanUI 的事件才进入扫码结果处理。
        // 这样不会因为 dispatchGesture=true 就把相册页误交给 EnterOfficialHome。
        if (photoSelected && event.isScanUiEvent() && !service.isWechatPhotoPickerContext()) {
            photoSelected = false
            retryState.reset()
            logI(actionName, "已确认返回微信扫一扫页，进入扫码结果处理")
            return "EnterOfficialHome"
        }

        if (photoSelected) {
            retryState.scheduleProbe(service, event)
            return actionName
        }

        if (!service.isWechatPhotoPickerContext() && !service.isGalleryPickerVisible(event)) {
            retryState.scheduleProbe(service, event)
            return actionName
        }

        // 微信当前页面第一格固定是“拍摄照片”，任务二维码在其右侧第一格；
        // 直接用无障碍服务手势点击该格，不搜索节点，也不点击目录下拉入口。
        val dispatched = retryState.dispatch(
            service,
            event,
            FIRST_PHOTO_X_RATIO,
            FIRST_PHOTO_Y_RATIO,
        )
        photoSelected = dispatched
        logI(actionName, "微信图片节点不可见，提交无障碍坐标手势 accepted=$dispatched")
        return actionName
    }
}
