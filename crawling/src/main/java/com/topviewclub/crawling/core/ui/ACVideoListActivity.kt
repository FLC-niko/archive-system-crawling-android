package com.topviewclub.crawling.core.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.topviewclub.common.util.toast
import com.topviewclub.crawling.core.databinding.ActivityAcvideoListBinding
import com.topviewclub.crawling.wechat.auto.room.ACVideoDao
import com.topviewclub.crawling.wechat.auto.room.acv.ACVideo
import kotlinx.coroutines.*
import java.util.*

class ACVideoListActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private lateinit var binding: ActivityAcvideoListBinding

    private val acVideos = mutableListOf<ACVideo>()

    private lateinit var adapter: ACVideoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAcvideoListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ACVideoAdapter(acVideos) {
            startActivity(Intent(this, ACVideoDetailsActivity::class.java).apply {
                putExtra("ac_video", acVideos[it])
            })
        }

        val layoutManager = LinearLayoutManager(this).apply {
            orientation = RecyclerView.VERTICAL
        }

        with(binding) {
            rvAcvList.layoutManager = layoutManager
            rvAcvList.adapter = adapter
        }

        val numberOfWechat = intent.getStringExtra("number_of_wechat")

        launch {
            withContext(Dispatchers.IO) {
                if (numberOfWechat == null) {
                    acVideos.addAll(ACVideoDao.selectAllVideoDesc())
                } else {
                    acVideos.addAll(ACVideoDao.selectVideoDesc(numberOfWechat))
                }
            }
            if (numberOfWechat == null) {
                toast("展示所有视频列表")
            } else {
                toast("展示微信号 $numberOfWechat 请求的视频列表")
            }
            @Suppress("NotifyDataSetChanged")
            adapter.notifyDataSetChanged()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

}