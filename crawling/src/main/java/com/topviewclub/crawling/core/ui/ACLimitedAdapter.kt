package com.topviewclub.crawling.core.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.topviewclub.crawling.core.databinding.ItemAclimitedListBinding
import com.topviewclub.crawling.wechat.auto.room.acl.ACLimited
import java.text.SimpleDateFormat
import java.util.*

class ACLimitedAdapter(
    private val users: List<ACLimited>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ACLimitedAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemAclimitedListBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemAclimitedListBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        with(holder.binding) {
            tvUserName.text = if (user.nameOfWechat.isBlank()) "微信用户" else user.nameOfWechat
            tvUserId.text = "微信号: ${user.numberOfWechat}"
            tvTodaySuccess.text = "今日成功: ${user.requestCount}"
            tvTodayError.text = "今日错误: ${user.errorCount}"
            tvTotalRequest.text = "累计成功: ${user.totalRequest}"
            tvTotalError.text = "累计错误: ${user.totalError}"
            tvUpdateTime.text = "更新时间: ${formatterYMD.format(Date(user.updateTime))}"

            root.setOnClickListener {
                onClick(position)
            }
        }
    }

    private val formatterYMD = SimpleDateFormat(
        "yyyy年M月d日 HH:mm:ss",
        Locale.getDefault()
    )

    override fun getItemCount(): Int {
        return users.size
    }

}