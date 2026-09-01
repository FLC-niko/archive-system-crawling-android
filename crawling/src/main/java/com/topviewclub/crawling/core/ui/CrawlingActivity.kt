package com.topviewclub.crawling.core.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import com.topviewclub.common.bean.TaskStat
import com.topviewclub.common.log.logI
import com.topviewclub.common.shizuku.*
import com.topviewclub.common.storage.DocumentFileUtils
import com.topviewclub.common.storage.video.WECHAT_CACHE_FOLDER
import com.topviewclub.common.util.setStatusBarTextColor
import com.topviewclub.common.util.toast
import com.topviewclub.common.wirebare.prepareProxy
import com.topviewclub.crawling.core.control.TaskDispatcher
import com.topviewclub.crawling.core.databinding.ActivityCrawlingBinding
import org.github.kokomi.wirebare.common.WireBare
import org.github.kokomi.wirebare.util.Level

class CrawlingActivity : AppCompatActivity() {

    private val permissions21 = arrayOf(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )

    // 这里会报错，是 IDEA 的 BUG ，不用管，直接编译运行就可以
    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            results.map { result ->
                if (!result.value) {
                    toast("授权失败")
                    return@registerForActivityResult
                }
            }
            requestFileAccess()
        }

    private lateinit var binding: ActivityCrawlingBinding

    companion object{
        var activity: CrawlingActivity? = null
       fun getActivity(activity: CrawlingActivity) {
           this.activity = activity
       }
    }

    @Suppress("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        // 调节 WireBare 的日志等级
        WireBare.logLevel = Level.SILENT
        super.onCreate(savedInstanceState)
        binding = ActivityCrawlingBinding.inflate(layoutInflater)


        //设置activity常量
        getActivity(this)

        setStatusBarTextColor(false)

//        updateQRCode()

        with(binding) {
            setContentView(root)

            TaskStat.successfulTaskList.observe(this@CrawlingActivity) {
                tvStatSuccess.text = "${it.size}"
            }

            TaskStat.completedTaskList.observe(this@CrawlingActivity) {
                tvStatCompleted.text = "${it.size}"
            }

            TaskStat.enqueuingTaskList.observe(this@CrawlingActivity) {
                tvStatEnqueuing.text = "${it.size}"
            }

            TaskStat.successfulRate.observe(this@CrawlingActivity) {
                tvStatRate.text = "$it %"
            }

            btnVpnPermission.setOnClickListener {
                if (!prepareProxy()) {
                    toast("请允许应用启动 VPN 服务")
                } else {
                    toast("已授权启动 VPN 服务")
                }
            }

            btnSystemPermission.setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    requestFileAccess()
                } else {
                    requestPermissions.launch(permissions21)
                }
            }

            btnShizukuPermission.setOnClickListener {
                toast("当前版本未支持 Shizuku 服务，无需授权")
                btnShizukuPermission.isEnabled = false
//                runCatching {
//                    if (!Shizuku_checkPermission()) {
//                        Shizuku_requestPermission(4964)
//                    }
//                }.onFailure {
//                    toast("Shizuku 服务连接出错")
//                }
            }

            btnAcVideoRoom.setOnClickListener {
                startActivity(
                    Intent(
                        this@CrawlingActivity, ACVideoListActivity::class.java
                    )
                )
            }

            btnAcLimitedRoom.setOnClickListener {
                startActivity(
                    Intent(
                        this@CrawlingActivity, ACLimitedListActivity::class.java
                    )
                )
            }

            btnStartAaos.setOnClickListener {
                btnStartAaos.isEnabled = false
                TaskDispatcher.init()
                toast("AAOS 已自动启动，正在等待公众号任务")
            }
        }

    }

    private fun requestFileAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                toast("已获得共享文件权限；Android 11+ 仍禁止授权微信 Android/data，公众号采集可直接继续")
                return
            }

            toast("Android 11+ 无法在文件选择器授权微信 Android/data；此权限不影响公众号采集")
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName"),
                    ),
                )
            }.onFailure {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            return
        }

        toast("请授权微信缓存文件夹管理权限")
        DocumentFileUtils.startForPermission(WECHAT_CACHE_FOLDER, this)
    }

    //返回授权状态
    @Suppress("Deprecation", "WrongConstant")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        var uri: Uri? = null
        data ?: return
        if (requestCode == 1024 && data.data.also { uri = it } != null) {
            // 保存请求访问目录的访问权限
            contentResolver.takePersistableUriPermission(
                uri!!,
                data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            )
            toast("授权成功，权限已保存")
        }
    }
}
