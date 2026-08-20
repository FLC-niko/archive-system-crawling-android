package com.topviewclub.crawling.service

import android.os.Process
import android.os.Handler
import android.os.HandlerThread
import android.os.Message

/**
 * 用于执行定时任务的线程
 *
 * @param name 线程名
 * @param interval 定时任务的间隔时间
 * @param timerTask 要定期执行的任务，返回 true 表示任务继续执行，否则任务停止执行
 * */
class TimerThread(name: String, interval: Long, timerTask: () -> Boolean) {

    private companion object {
        private const val ALIVE = 1
        private const val KILL = 2
    }

    private val handlerThread = HandlerThread(name, Process.THREAD_PRIORITY_FOREGROUND)

    private val handler: Handler

    private var dead = false

    init {
        handlerThread.start()
        handler = object : Handler(handlerThread.looper) {
            override fun handleMessage(msg: Message) {
                if (msg.what == ALIVE && timerTask()) {
                    sendEmptyMessageDelayed(ALIVE, interval)
                } else {
                    dead = true
                    handlerThread.quit()
                }
            }
        }
        handler.sendEmptyMessageDelayed(ALIVE, interval)
    }

    fun post(runnable: Runnable) {
        handler.post(runnable)
    }

    fun quit() {
        if (!dead) handler.sendEmptyMessage(KILL)
    }

}