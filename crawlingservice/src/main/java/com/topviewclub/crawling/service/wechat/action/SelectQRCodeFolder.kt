package com.topviewclub.crawling.service.wechat.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.common.log.logI

class SelectQRCodeFolder : Action {

    private companion object {
        private const val QRCODE_TEXT = "QRCode"
        private const val CLOSE_FOLDER_ID = "com.tencent.mm:id/f5"
        private const val FOLDER_X_RATIO = 0.500f
        private const val FOLDER_Y_RATIO = 0.185f
    }

    override val actionName: String = "SelectQRCodeFolder"

    private val retryState = PickerRetryState(actionName)

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val folderTitle = service.pickerFolderTitleOrNull()
        // 只有顶部标题变成 aaos/QRCode，才能证明目录切换成功。仅有图片网格
        // 不足以证明它不是“所有图片”，否则会误选历史二维码。
        if (folderTitle == "aaos" || folderTitle == QRCODE_TEXT) {
            retryState.reset()
            logI(actionName, "已确认进入二维码文件夹: $folderTitle")
            return "SelectPhoto"
        }

        // 文件夹列表可能和当前标题“所有图片”共存；此时只查找并点击可见的
        // aaos/QRCode 行，不能把标题节点本身当成目录行。
        if (!service.isFolderListVisible(event) && folderTitle != "所有图片") {
            // 仍在刚打开相册的过渡页，等微信把目录标题/列表稳定下来。
            retryState.scheduleProbe(service, event)
            return actionName
        }

        // 优先进入任务专用目录；旧版本目录名为 QRCode，新版本为 aaos。
        val target = service.findPickerNodeOrNull {
            isVisibleToUser &&
                    (text?.toString() == "aaos" || contentDescription?.toString() == "aaos")
        } ?: service.findPickerNodeOrNull {
            isVisibleToUser &&
                    (text?.toString() == QRCODE_TEXT || contentDescription?.toString() == QRCODE_TEXT)
        }

        if (target != null) {
            val clicked = target.click() ||
                    target.parent?.click() == true ||
                    target.parent?.parent?.click() == true ||
                    service.tapPickerNodeCenter(target)
            if (clicked) {
                retryState.scheduleProbe(service, event)
                return actionName
            }
        }

        // 标题仍是“所有图片”时，先打开微信原生的目录下拉；之前的实现直接
        // 在网格/过渡页点击 0.185，实际命中的是“所有图片”而不是 aaos。
        if (folderTitle == "所有图片" || service.isPhotoGridVisible(event)) {
            val dropdown = service.findPickerNodeOrNull {
                text?.toString() == "所有图片" ||
                        contentDescription?.toString() == "所有图片" ||
                        viewIdResourceName == CLOSE_FOLDER_ID
            }
            val clicked = dropdown?.let {
                it.click() || it.parent?.click() == true || it.parent?.parent?.click() == true
            } == true
            if (clicked) {
                retryState.scheduleProbe(service, event)
                logI(actionName, "已通过微信目录标题打开文件夹列表")
                return actionName
            }
            val dispatched = retryState.dispatch(
                service,
                event,
                FOLDER_X_RATIO,
                0.056f,
            )
            logI(actionName, "微信目录标题不可见，提交打开下拉手势 accepted=$dispatched")
            return actionName
        }

        // 文件夹列表也是微信自绘界面时，aaos 行位于工具栏下的第一行任务目录。
        val dispatched = retryState.dispatch(
            service,
            event,
            FOLDER_X_RATIO,
            FOLDER_Y_RATIO,
        )
        logI(actionName, "文件夹节点不可见，提交无障碍坐标手势 accepted=$dispatched")
        return actionName

//        val close = root.findNodeOrNull {
//            viewIdResourceName == CLOSE_FOLDER_ID
//        } ?: return step
//
//        close.click()
    }
}
