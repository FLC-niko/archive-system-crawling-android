package com.topviewclub.crawling.core.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.topviewclub.common.util.toast
import com.topviewclub.crawling.core.databinding.ActivityAclimitedListBinding
import com.topviewclub.crawling.wechat.auto.room.ACLimitedDao
import com.topviewclub.crawling.wechat.auto.room.acl.ACLimited
import kotlinx.coroutines.*

class ACLimitedListActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private lateinit var binding: ActivityAclimitedListBinding

    private val acLimiteds = mutableListOf<ACLimited>()

    private lateinit var adapter: ACLimitedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAclimitedListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ACLimitedAdapter(acLimiteds) {
            startActivity(Intent(this, ACVideoListActivity::class.java).apply {
                putExtra("number_of_wechat", acLimiteds[it].numberOfWechat)
            })
        }

        val layoutManager = LinearLayoutManager(this).apply {
            orientation = RecyclerView.VERTICAL
        }

        with(binding) {
            rvAclList.layoutManager = layoutManager
            rvAclList.adapter = adapter
        }

        launch {
            withContext(Dispatchers.IO) {
                acLimiteds.addAll(ACLimitedDao.selectAllLimited())
            }
            toast("展示所有用户列表")
            @Suppress("NotifyDataSetChanged")
            adapter.notifyDataSetChanged()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

}