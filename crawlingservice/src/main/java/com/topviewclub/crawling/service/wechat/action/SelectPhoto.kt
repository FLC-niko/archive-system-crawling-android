package com.topviewclub.crawling.service.wechat.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull

/**
 * 选择相册第一张图片
 * */
internal class SelectPhoto : Action {

    private companion object {
        private const val SELECT_PHOTO_DESCRIPTION = "图片1"
    }

    override val actionName: String = "SelectPhoto"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName
        val target = root.findNodeOrNull {
            contentDescription?.let {
                val length = SELECT_PHOTO_DESCRIPTION.length
                it.length > length
                        && it.subSequence(0, length) == SELECT_PHOTO_DESCRIPTION
            } ?: false
        } ?: return actionName
        val click = target.parent ?: return actionName
        click.click()
        return "EnterOfficialHome"
    }

}