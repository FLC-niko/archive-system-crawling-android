package com.topviewclub.crawling.service.wechat.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.common.log.logI

class SelectPhoneOrOpenFolderList : Action {

    private companion object {
        private const val OPEN_FOLDER_ID = "com.tencent.mm:id/f5"
        private const val SELECT_PHOTO_DESCRIPTION = "图片1"
    }

    override val actionName: String = "SelectPhoneOrOpenFolderList"

    private val retryState = PickerRetryState(actionName)

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow

        // 1. 若当前相册已经处于 aaos 或 QRCode 目录，直接推进到选择图片
        val folderTitle = service.pickerFolderTitleOrNull()
        if (folderTitle == "aaos" || folderTitle == "QRCode") {
            retryState.reset()
            logI(actionName, "已确认当前微信相册目录: $folderTitle")
            retryState.scheduleProbe(service, event)
            return "SelectPhoto"
        }

        // 2. 老版本微信既定路线：通过原生节点（com.tencent.mm:id/f5 或 "所有图片"）展开相册目录列表
        val isLegacy = WechatVersionCompat.isLegacyRoute(service)
        val folderDropdown = root?.findNodeOrNull {
            viewIdResourceName == OPEN_FOLDER_ID ||
                (isLegacy && (text?.toString() == "所有图片" || contentDescription?.toString() == "所有图片"))
        }

        if (folderDropdown != null && isLegacy) {
            // 老版本既定路线：若当前网格已有“图片1”可见，无需展开目录直接选中
            val photo = root.findNodeOrNull {
                contentDescription?.let {
                    val length = SELECT_PHOTO_DESCRIPTION.length
                    it.length >= length && it.subSequence(0, length) == SELECT_PHOTO_DESCRIPTION
                } ?: false
            }
            if (photo != null) {
                logI(actionName, "老版本微信：首张图片直接可见，跳过目录展开")
                retryState.reset()
                retryState.scheduleProbe(service, event)
                return "SelectPhoto"
            }

            // 二维码不在当前屏，点击 f5 下拉展开文件夹列表，进入 SelectQRCodeFolder
            val clicked = folderDropdown.click() || folderDropdown.parent?.click() == true
            logI(actionName, "老版本微信：点击目录下拉按钮(f5) clicked=$clicked，进入 SelectQRCodeFolder")
            Thread.sleep(1000L)
            return "SelectQRCodeFolder"
        }

        // 3. 现代微信自绘 View 路线：AlbumPreviewUI 直接在当前网格选图
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
