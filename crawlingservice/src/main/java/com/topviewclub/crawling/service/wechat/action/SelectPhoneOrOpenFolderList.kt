package com.topviewclub.crawling.service.wechat.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.common.log.logI

class SelectPhoneOrOpenFolderList : Action {

    override val actionName: String = "SelectPhoneOrOpenFolderList"

    private val retryState = PickerRetryState(actionName)

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        // 已经是微信 AlbumPreviewUI 时直接选择当前网格，跳过“所有图片”及目录列表。
        // 先检查窗口类名，避免对大图片树做全量无障碍遍历。
        if ((event.packageName?.toString() == WECHAT_PACKAGE_NAME && event.isGalleryUiEvent()) ||
            service.isWechatPhotoPickerContext()
        ) {
            retryState.reset()
            logI(actionName, "已确认微信原生 AlbumPreviewUI，跳过目录入口，直接选择首张图片")
            // 自绘图片网格不一定再次产生 TYPE_WINDOW_STATE_CHANGED，主动唤起
            // SelectPhoto 进行一次无障碍手势点击。
            retryState.scheduleProbe(service, event)
            return "SelectPhoto"
        }

        val folderTitle = service.pickerFolderTitleOrNull()
        // “aaos/QRCode” 是已经选中的目录，后续才允许选择图片。不能因为树中
        // 残留了一个 aaos 节点就把“所有图片”网格当成文件夹列表。
        if (folderTitle == "aaos" || folderTitle == "QRCode") {
            retryState.reset()
            logI(actionName, "已确认当前微信相册目录: $folderTitle")
            retryState.scheduleProbe(service, event)
            return "SelectPhoto"
        }

        if (!service.isGalleryPickerVisible(event)) {
            retryState.scheduleProbe(service, event)
            return actionName
        }

        // 微信扫一扫右下角“相册”打开的就是微信自己的 AlbumPreviewUI。
        // 当前任务二维码通常已经位于“拍摄照片”右侧第一格，不需要再点击
        // 顶部“所有图片”去展开目录，也不应该跳转到 MIUI 图库。
        if (folderTitle == "所有图片" || service.isPhotoGridVisible(event)) {
            retryState.reset()
            logI(actionName, "已确认微信原生图片网格，跳过‘所有图片’目录入口，直接选择首张图片")
            retryState.scheduleProbe(service, event)
            return "SelectPhoto"
        }

        // 页面仍在切换动画中时等待下一次无障碍事件；不对顶部控件做猜测性点击。
        retryState.scheduleProbe(service, event)
        return actionName
    }

}
