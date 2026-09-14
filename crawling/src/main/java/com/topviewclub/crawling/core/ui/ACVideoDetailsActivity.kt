package com.topviewclub.crawling.core.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.topviewclub.common.util.isDarkMode
import com.topviewclub.common.util.saveClipboardContent
import com.topviewclub.common.util.setStatusBarTextColor
import com.topviewclub.common.util.toast
import com.topviewclub.crawling.core.databinding.ActivityAcvideoDetailsBinding
import com.topviewclub.crawling.wechat.auto.room.ACLimitedDao
import com.topviewclub.crawling.wechat.auto.room.acl.ACLimited
import com.topviewclub.crawling.wechat.auto.room.acv.ACVideo
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class ACVideoDetailsActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private lateinit var binding: ActivityAcvideoDetailsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAcvideoDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setStatusBarTextColor(!isDarkMode)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        val video = intent.getSerializableExtra("ac_video") as ACVideo

        binding.btnCopyUrl.setOnClickListener {
            saveClipboardContent(video.url)
            toast("复制视频链接成功")
        }

        binding.tvVideoDetails.text = video.let {
            """
                任务前缀：${it.requestType}
                任务识别码：${it.requestCode}
                请求微信号：${it.numberOfWechat}
                抓取时间：${formatterYMD.format(Date(it.time))}

                视频直链：
                ${it.url}
            """.trimIndent()
        }

        launch {
            val user = withContext(Dispatchers.IO) {
                ACLimitedDao.selectLimited(
                    video.numberOfWechat
                ).firstOrNull() ?: ACLimited(
                    "未找到该用户",
                    video.numberOfWechat,
                    0,
                    0,
                    0,
                    0,
                    0L
                )
            }

            binding.tvUserDetails.text = user.let {
                """
                    微信昵称：${it.nameOfWechat}
                    微信号：${it.numberOfWechat}
                    今日统计：成功 ${it.requestCount} 次 / 失败 ${it.errorCount} 次
                    累计统计：成功 ${it.totalRequest} 次 / 失败 ${it.totalError} 次
                    更新时间：${formatterYMD.format(Date(it.updateTime))}
                """.trimIndent()
            }

            binding.tvUserDetails.setOnClickListener {
                startActivity(
                    Intent(
                        this@ACVideoDetailsActivity,
                        ACVideoListActivity::class.java
                    ).apply {
                        putExtra("number_of_wechat", user.numberOfWechat)
                    })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    private val formatterYMD = SimpleDateFormat(
        "yyyy年M月d日 HH:mm:ss.SSS",
        Locale.getDefault()
    )
}