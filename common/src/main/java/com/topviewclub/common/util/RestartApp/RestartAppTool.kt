package com.topviewclub.common.util.RestartApp

import android.content.Context
import android.content.Intent
import android.os.Process

object RestartAppTool {
 fun restartApp(context: Context , delayed: Long = 2000){
     val intent = Intent(context,KillSelfService::class.java)
     intent.putExtra("PackageName",context.packageName);
     intent.putExtra("Delayed",delayed);
     context.startService(intent);
     Process.killProcess(Process.myPid())
 }

}