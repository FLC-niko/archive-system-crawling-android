package com.topviewclub.crawling.service.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService

interface Action {

    val actionName: String

    /**
     * 当前步骤被调度时，将会回调该函数，如果在当前步骤出错，请显式抛出异常 [ActionException]
     *
     * @param service 无障碍服务
     * @param event 接收到的目标事件
     *
     * @throws ActionException 当当前步骤出错，则抛出此异常
     *
     * @return 返回下一步骤的名称，如果是最后一个步骤，请返回 [AutoOperationService.ActionType.ActionSuccess]
     * */
    @Throws(ActionException::class)
    fun execute(service: AutoOperationService, event: AccessibilityEvent): String

    /**
     * 任务开始时重置动作内部状态（失败计数、页面标记、OCR 状态等）。
     *
     * 无障碍服务实例与动作链会被多条任务复用；上一次任务失败留下的计数如果不清理，
     * 下一次任务可能在第一个事件就判定"重试超限"而连锁失败。
     */
    fun reset() {}
}