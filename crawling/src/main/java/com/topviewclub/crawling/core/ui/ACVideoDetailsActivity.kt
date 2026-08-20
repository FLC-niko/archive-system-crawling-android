package com.topviewclub.crawling.core.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.topviewclub.common.util.saveClipboardContent
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

        val video = intent.getSerializableExtra("ac_video") as ACVideo

        binding.btnCopyUrl.setOnClickListener {
            saveClipboardContent(video.url)
            toast("复制视频链接成功")
        }

        binding.tvVideoDetails.text = video.let {
            """
                前缀 | ${it.requestType}
                后缀 | ${it.requestCode}
                用户 | ${it.numberOfWechat}
                链接 | ${it.url}
                时间 | ${formatterYMD.format(Date(it.time))}
            """.trimIndent()
        }

        launch {
            val user = withContext(Dispatchers.IO) {
                ACLimitedDao.selectLimited(
                    video.numberOfWechat
                ).firstOrNull() ?: ACLimited(
                    "未找到该用户",
                    "未找到该用户",
                    -1,
                    -1,
                    -1,
                    -1,
                    0L
                )
            }

            binding.tvUserDetails.text = user.let {
                """
                    微信名 | ${it.nameOfWechat}
                    微信号 | ${it.numberOfWechat}
                    今天请求成功数 | ${it.requestCount}
                    今天请求错误数 | ${it.errorCount}
                    总共请求成功数 | ${it.totalRequest}
                    总共请求错误数 | ${it.totalError}
                    更新时间 | ${formatterYMD.format(Date(it.updateTime))}
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
        "yyyy年M月d日HH:mm:ss.SSS",
        Locale.getDefault()
    )
}