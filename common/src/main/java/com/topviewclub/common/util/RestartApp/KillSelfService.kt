package com.topviewclub.common.util.RestartApp

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder


class KillSelfService : Service() {

    private var stopDelayed: Long = 50
    private var handler: Handler? = null
    private var packageName: String? = null
    init {
        handler = Handler(mainLooper)
    }
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        stopDelayed = intent.getLongExtra("Delayed", 50)
        packageName = intent.getStringExtra("PackageName")
        handler?.postDelayed({
            val LaunchIntent = packageManager.getLaunchIntentForPackage(
                packageName!!
            )
            startActivity(LaunchIntent)
            this@KillSelfService.stopSelf()
        }, stopDelayed)
        return super.onStartCommand(intent, flags, startId)
    }
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

}