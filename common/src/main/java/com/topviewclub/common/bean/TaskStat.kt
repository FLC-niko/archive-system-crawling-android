package com.topviewclub.common.bean

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import java.util.*

object TaskStat {

    var startDate = Date()

    val successfulTaskList = MutableLiveData<MutableList<AAOSTask>>(mutableListOf())

    val completedTaskList = MutableLiveData<MutableList<AAOSTask>>(mutableListOf())

    val enqueuingTaskList = MutableLiveData<List<AAOSTask>>(emptyList())

    val successfulRate = MutableLiveData(0)

    /**
     * 正在处理的 [AAOSTask]
     * */
    var processingTask: AAOSTask = NullTask()
        set(value) {
            field = value
            Handler(Looper.getMainLooper()).post {
                processingTaskListeners.forEach { it.invoke(value) }
            }
        }

    private val processingTaskListeners = hashSetOf<(AAOSTask) -> Unit>()

    fun addProcessingTaskListener(listener: (AAOSTask) -> Unit) {
        listener(processingTask)
        processingTaskListeners.add(listener)
    }

    fun removeProcessingTaskListener(listener: (AAOSTask) -> Unit): Boolean {
        return processingTaskListeners.remove(listener)
    }

    fun clearProcessingTaskListener() {
        processingTaskListeners.clear()
    }

}