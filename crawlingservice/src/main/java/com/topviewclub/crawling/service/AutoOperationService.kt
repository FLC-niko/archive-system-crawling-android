package com.topviewclub.crawling.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.CallSuper
import androidx.core.content.getSystemService
import com.topviewclub.common.bean.*
import com.topviewclub.common.log.logE
import com.topviewclub.common.log.logI
import com.topviewclub.common.log.logRabbit
import com.topviewclub.common.mq.RabbitMQClient
import com.topviewclub.common.network.sendHeartBeatToHostHeartbeatOnce
import com.topviewclub.common.network.sendMessageToHostError
import com.topviewclub.common.network.sendToNacosRegisterBeat
import com.topviewclub.common.util.className
import com.topviewclub.common.util.toStringOrEmpty
import com.topviewclub.crawling.service.handler.AccessibilityEventHandler
import com.topviewclub.crawling.service.handler.ImmediatelyProcessHandler
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.action.ActionException
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 封装好的专用于模拟点击的无障碍服务
 * <p>
 *
 * 如果要设计新的无障碍服务，可以继承此抽象类，并实现相应属性
 * <p>
 *
 * 最后，记得在清单文件中注册，否则服务无法启动，而且也不会有任何报错
 * */
abstract class AutoOperationService : AccessibilityService() {

    companion object {
        private val connectedServices =
            ConcurrentHashMap<String, WeakReference<AutoOperationService>>()

        /**
         * RabbitMQ 任务可能在微信静止且没有新无障碍事件时到达。主动唤醒已连接
         * 的对应服务，让它读取 TaskStat 并开始责任链。
         */
        fun wakeServiceForTask(taskType: String): Boolean {
            val service = connectedServices[taskType]?.get() ?: return false
            service.wakeForCurrentTask()
            return true
        }
    }

    object ActionType {
        const val ActionSuccess = "_%aaos_action_success%_"
        const val ActionDead = "_%aaos_action_dead%_"
        const val ActionNull = "_%aaos_action_null%_"
    }

    /**
     * 当前服务抓取的类型，详见 [TaskCrawlingType]
     * */
    @TaskType
    abstract val crawlServiceType: String

    abstract val aaosTask: AAOSTask

    /**
     * 需要被抓取的目标
     * */
    abstract val target: String

    /**
     * 标记
     * */
    open val serviceTag: String? = null

    /**
     * 操作集合
     * */
    private val actionMap = hashMapOf<String, Action>()

    abstract val actionList: List<Action>

    /**
     * 现在正在执行（或将要执行）的 [Action]
     * <p>
     *
     * 当其值为 [ActionType.ActionDead] 时，代表服务已经不再处于活动状态
     * <p>
     *
     * 初始应设为要执行的第一个 [Action]
     *
     * @see [ActionType]
     * @see [onServiceDestroy]
     * */
    abstract var targetActionName: String
        protected set

    /**
     * 方才执行（或刚刚执行完毕）的 [Action]
     */
    var lastTargetActionName: String = ActionType.ActionNull
        protected set

    /**
     * 爬取起始日期
     * */
    open val startDate: Long = Long.MIN_VALUE

    /**
     * 爬取结束日期
     * */
    open val endDate: Long = Long.MAX_VALUE

    /**
     * 无障碍事件处理器，默认为 [ImmediatelyProcessHandler] 立即执行处理器
     * */
    protected open val eventHandler: AccessibilityEventHandler =
        ImmediatelyProcessHandler()

    /**
     * true 表示需要保存服务的记录
     * <p>
     *
     * 保存记录表示：发送执行结果至主机；记录到技术统计 [TaskStat] 中
     * */
    protected open val allowRecords: Boolean = true

    /**
     * 服务未经过 [onServiceDestroy] 就被系统销毁时的兜底回调。
     * 子类可用它完成外部任务的失败收口，例如通知 RabbitMQ 任务处理方。
     */
    protected open fun onUnexpectedServiceDestroy() {
    }

    /**
     * 通知通道 ID
     * */
    protected open val channelId = "Auto_Crawling"

    /**
     * 通知 ID
     * */
    protected open val notificationId = 777

    /**
     * 存活标志位
     * */
    private var alive: Boolean = true @Synchronized set

    /**
     * 无障碍服务可能会在应用没有任务时先被系统绑定。此时不能启动任务线程，
     * 也不能把服务主动禁用，否则下一条任务到来时无法继续复用这次授权。
     */
    @Volatile
    private var taskInitialized = false
    private var activeTask: AAOSTask? = null

    /**
     * 标志服务是否正常退出，若没有正常退出，则该值为 false
     * */
    @Volatile
    private var reportFlag = false

    /**
     * 添加服务结束的回调，默认向主机发送执行报告，然后保留无障碍授权等待下一条任务。
     * */
    private val serviceDestroyList = LinkedList<(ServiceResult) -> Unit>().apply {
        add(0, { result: ServiceResult ->
            if (allowRecords) {
                updateTaskStat(result)
                logI(
                    this@AutoOperationService.className,
                    "${serviceTag.toStringOrEmpty()} ${result.msg}"
                )

//                // 直接重启，而不是发送错误报告
//                when(result.msg){
//                    TaskResultType.SERVICE_NO_RESPONSE->{
//                        com.topviewclub.common.network.restartApp(
//                            this@AutoOperationService.className,
//                            result.msg,
//                            serviceTag.toStringOrEmpty()
//                        )
//                        disableSelf()
//                    }
//                    TaskResultType.NETWORK_EXCEPTION ->{
//                        com.topviewclub.common.network.restartApp(
//                            this@AutoOperationService.className,
//                            result.msg,
//                            serviceTag.toStringOrEmpty()
//                        )
//                        disableSelf()
//                    }
//                }


                sendMessageToHostError(
                    this@AutoOperationService.className,
                    result.msg,
                    serviceTag.toStringOrEmpty()
                )
            }
            // 不调用 disableSelf()：该服务由 RabbitMQ 长期复用，主动禁用会清掉用户授权，
            // 下一条任务无法再自动进入无障碍动作链。
        })

    }

    /**
     * 添加服务监听
     *
     * @see [TaskResultType]
     * */
    fun addOnServiceDestroyListener(listener: (ServiceResult) -> Unit) {
        serviceDestroyList.add(0, listener)
    }

    /**
     * 在使用的调度者为 [ImmediatelyProcessHandler]
     * 时，有时候一个步骤进行完毕后，应用不会做出回应，不会主动激活无障碍服务，导致无障碍服务卡死，
     * 可以在步骤执行快要完毕时调用此函数稍后主动激活无障碍服务继续运作
     *
     * @param event 激活无障碍服务时传入的无障碍事件，一般可以传 [Action.execute] 函数中传入的无障碍事件
     * @param delay 延迟触发
     * @param before 在触发无障碍事件前要执行的内容
     * */
    fun resumeServiceDelay(
        event: AccessibilityEvent, delay: Long, before: () -> Unit = {}
    ) {
        // AccessibilityEvent 是系统对象池中的对象，回调返回后可能立即被回收；不能把
        // 收到的实例直接跨线程或延迟使用。
        val eventCopy = AccessibilityEvent.obtain(event)
        val posted = Handler(mainLooper).postDelayed({
            try {
                if (taskInitialized) {
                    before()
                    onAccessibilityEvent(eventCopy)
                }
            } finally {
                eventCopy.recycle()
            }
        }, delay)
        if (!posted) eventCopy.recycle()
    }

    private fun updateTaskStat(result: ServiceResult) {
        if (result is ServiceResult.Completed) {
            TaskStat.successfulTaskList.value!!.let {
                it.add(aaosTask)
                TaskStat.successfulTaskList.value = it
            }
        }
        TaskStat.completedTaskList.value!!.let {
            it.add(aaosTask)
            TaskStat.completedTaskList.value = it
        }
        TaskStat.successfulRate.value =
            ((TaskStat.successfulTaskList.value!!.size /
                    TaskStat.completedTaskList.value!!.size.toFloat()) * 100).toInt()
    }

    /**
     * 用于检测服务是否卡死在某一步骤的线程
     * */
    private var heartbeatHandler: TimerThread? = null

    private fun startHeartbeat() {
        heartbeatHandler = TimerThread("SNR Checker", 15000L) {
            if (!taskInitialized) return@TimerThread false

            // 如果 15 秒后仍然收不到存活信号，则认为服务已经卡住
            val live = alive
            if (!live) {
                // post 到操作执行线程内执行，保证线程安全
                eventHandler.post {
                    val actionName = actionMap[targetActionName]?.actionName ?: "None"
                    logE("SNR Check", actionName)
                    // 立即进入终止状态
                    targetActionName = ActionType.ActionDead
                    // 回报无响应错误
                    onServiceDestroy(
                        ServiceResult.Error(TaskResultType.SERVICE_NO_RESPONSE)
                    )
                }
            } else {
                alive = false
                // 向注册中心发送心跳
                sendToNacosRegisterBeat()
                //向主机发送心跳
                sendHeartBeatToHostHeartbeatOnce()

            }
            return@TimerThread live
        }
    }

    @CallSuper
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!initializeForCurrentTask()) return
        eventHandler.onAccessibilityEvent(this, event)
    }

    /**
     * 在收到事件时再初始化任务，兼容无障碍服务早于任务启动而被系统绑定的情况。
     */
    private fun initializeForCurrentTask(): Boolean {
        if (taskInitialized) return true
        val task = TaskStat.processingTask
        if (task.type != crawlServiceType) return false

        activeTask = task
        addActions()
        eventHandler.onServiceCreate(this)
        taskInitialized = true
        startForegroundNotification()
        startHeartbeat()
        logI(
            this@AutoOperationService.className,
            "Accessibility service ready for task: ${task.type}, tag=${task.tag}",
        )
        return true
    }

    private fun wakeForCurrentTask() {
        Handler(mainLooper).post {
            if (!initializeForCurrentTask()) return@post
            dispatchSyntheticEvent()
            logI(
                this@AutoOperationService.className,
                "Task wake event dispatched for ${activeTask?.type}",
            )
        }
    }

    /** 供异步截图/OCR 回调继续当前责任链，不依赖页面额外产生事件。 */
    fun resumeCurrentAction() {
        Handler(mainLooper).post {
            if (taskInitialized) dispatchSyntheticEvent()
        }
    }

    private fun dispatchSyntheticEvent() {
        val currentRoot = rootInActiveWindow
        val wakeEvent = AccessibilityEvent.obtain(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        ).apply {
            packageName = currentRoot?.packageName ?: applicationInfo.packageName
            className = currentRoot?.className ?: this@AutoOperationService.javaClass.name
        }
        try {
            onAccessibilityEvent(wakeEvent)
        } finally {
            wakeEvent.recycle()
        }
    }

    internal fun handleEvent(event: AccessibilityEvent): Boolean {
        if (!taskInitialized) return false
        runCatching {
            // 每次收到真实或延迟探针事件都证明动作线程仍在运行。一个动作在
            // 等待页面切换时可能连续返回自身，不能只靠动作名变化刷新心跳。
            alive = true
            // 说明服务已经停止
            if (targetActionName == ActionType.ActionDead) return false
            val nextActionName = actionMap[targetActionName]?.execute(
                this@AutoOperationService,
                event
            ) ?: throw NullPointerException("Unexpected target action name: $targetActionName")
            lastTargetActionName = targetActionName
            targetActionName = nextActionName
            if (targetActionName == ActionType.ActionSuccess) {
                // 任务完成
                targetActionName = ActionType.ActionDead
                onServiceDestroy(ServiceResult.Completed(TaskResultType.TASK_COMPLETED))
            }
        }.onFailure {
            it.printStackTrace()
            logI(
                this@AutoOperationService.className,
                "Cause = ${it.cause} , Message = ${it.message}"
            )
            // 服务异常，停止服务
            targetActionName = ActionType.ActionDead
            if (it is ActionException) {
                onServiceDestroy(
                    ServiceResult.Error(it.message ?: TaskResultType.UNKNOWN_EXCEPTION)
                )
            } else {
                onServiceDestroy(
                    ServiceResult.Error(TaskResultType.UNKNOWN_EXCEPTION)
                )
            }
        }
        return true
    }

    private fun onServiceDestroy(result: ServiceResult) {
        if (reportFlag) return
        reportFlag = true
        // 切换主线程
        if (Looper.myLooper() == Looper.getMainLooper()) {
            notifyServiceDestroyListeners(result)
        } else {
            Handler(Looper.getMainLooper()).post {
                notifyServiceDestroyListeners(result)
            }
        }
    }

    private fun notifyServiceDestroyListeners(result: ServiceResult) {
        serviceDestroyList.toList().forEach { listener ->
            runCatching { listener(result) }
                .onFailure {
                    logE(
                        this@AutoOperationService.className,
                        "服务结束回调失败: ${it.message}",
                    )
                }
        }
        resetAfterTask()
    }

    /**
     * 一条任务结束后释放动作线程，但不撤销系统中的无障碍授权。
     */
    private fun resetAfterTask() {
        if (!taskInitialized) return
        heartbeatHandler?.quit()
        heartbeatHandler = null
        eventHandler.onServiceDestroy(this)

        val finishedTask = activeTask
        targetActionName = actionList.firstOrNull()?.actionName ?: targetActionName
        lastTargetActionName = ActionType.ActionNull
        alive = true
        taskInitialized = false
        activeTask = null
        reportFlag = false
        if (TaskStat.processingTask == finishedTask) {
            TaskStat.processingTask = NullTask()
        }
    }

    @CallSuper
    override fun onDestroy() {
        connectedServices[crawlServiceType]?.get()?.let { connected ->
            if (connected === this) connectedServices.remove(crawlServiceType)
        }
        super.onDestroy()
        val unexpectedlyDestroyed = taskInitialized && !reportFlag
        if (unexpectedlyDestroyed) {
            reportFlag = true
            runCatching {
                if (allowRecords) {
                    updateTaskStat(
                        ServiceResult.Error(TaskResultType.SERVICE_DESTROY_UNEXPECTEDLY)
                    )
                }
            }.onFailure { logE(this@AutoOperationService.className, "记录意外销毁失败: ${it.message}") }
            runCatching { onUnexpectedServiceDestroy() }
                .onFailure { logE(this@AutoOperationService.className, "意外销毁回调失败: ${it.message}") }
        }
        runCatching {
            // 退出存活检测线程
            heartbeatHandler?.quit()
            heartbeatHandler = null
            if (taskInitialized) {
                eventHandler.onServiceDestroy(this)
                if (TaskStat.processingTask == activeTask) {
                    TaskStat.processingTask = NullTask()
                }
            }
            taskInitialized = false
            activeTask = null
        }
    }

    override fun onInterrupt() {
    }

    private fun addActions() {
        for (action in actionList) {
            actionMap[action.actionName] = action
        }
    }

    @CallSuper
    override fun onCreate() {
        super.onCreate()
        connectedServices[crawlServiceType] = WeakReference(this)
        if (!initializeForCurrentTask()) {
            logI(
                this@AutoOperationService.className,
                "No matching task yet; accessibility service is waiting",
            )
        }
    }

    // 开启通知（前台服务）
    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService<NotificationManager>())?.let {
                //没有创建
                if (it.getNotificationChannel(channelId) == null) {
                    //则先创建
                    it.createNotificationChannel(
                        NotificationChannel(
                            channelId, channelId, NotificationManager.IMPORTANCE_DEFAULT
                        )
                    )
                }
            }
            val builder = Notification.Builder(this, channelId).setContentTitle(channelId)
                .setContentText(channelId)
            val notification = builder.build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    notificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(notificationId, notification)
            }
        }
    }

}
