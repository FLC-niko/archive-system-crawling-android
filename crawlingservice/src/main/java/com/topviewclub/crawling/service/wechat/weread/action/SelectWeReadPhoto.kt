package com.topviewclub.crawling.service.wechat.weread.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.common.log.logI
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.crawling.service.wechat.action.isGalleryPickerVisible
import com.topviewclub.crawling.service.wechat.action.isWechatPhotoPickerContext
import com.topviewclub.crawling.service.wechat.action.tapPickerRatio

/**
 * 微信读书专用相册选图 Action：
 * 1. 继承/重载 SelectPhoto 动作契约名，无缝接续 SelectPhoneOrOpenFolderList 与 SelectQRCodeFolder 的跳转；
 * 2. 严格校验前置相册选择器可见性，杜绝在过渡页盲点；
 * 3. 选图完成后跳转至 ConfirmWeReadLogin 授权确认环节。
 */
class SelectWeReadPhoto : Action {
    // 对齐公共选择器目录跳转的目标动作契约名，确保责任链状态转移平滑
    override val actionName: String = "SelectPhoto"

    private companion object {
        // AlbumPreviewUI 第一格为“拍摄照片”，第二格为首张二维码新鲜图片
        private const val FIRST_PHOTO_X_RATIO = 0.375f
        private const val FIRST_PHOTO_Y_RATIO = 0.148f
    }

    override fun execute(service: AutoOperationService, event: AccessibilityEvent): String {
        // 前置守卫：确保当前确实处于微信相册选择器页面中，防止在过渡动画或前序页面误触
        if (!service.isWechatPhotoPickerContext() && !service.isGalleryPickerVisible(event)) {
            return actionName
        }

        val root = service.rootInActiveWindow
        val photo = root?.findNodeOrNull {
            contentDescription?.toString()?.startsWith("图片1") == true ||
                contentDescription?.toString()?.contains("图片") == true
        }
        if (photo != null && (photo.click() || photo.parent?.click() == true || photo.parent?.parent?.click() == true)) {
            logI(actionName, "通过原生相册节点选中二维码图片: ${photo.contentDescription}")
            Thread.sleep(1500L)
            return "ConfirmWeReadLogin"
        }

        // 自绘 View 兜底：无障碍手势坐标点击相册第二格新鲜二维码
        logI(actionName, "通过自适应比率手势坐标点击相册第二格二维码")
        val tapped = service.tapPickerRatio(FIRST_PHOTO_X_RATIO, FIRST_PHOTO_Y_RATIO)
        if (tapped) {
            Thread.sleep(1500L)
            return "ConfirmWeReadLogin"
        }
        return actionName
    }
}