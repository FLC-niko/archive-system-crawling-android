package com.topviewclub.crawling.core.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.topviewclub.crawling.core.databinding.ItemAcvideoListBinding
import com.topviewclub.crawling.wechat.auto.room.acv.ACVideo
import java.text.SimpleDateFormat
import java.util.*

class ACVideoAdapter(
    private val videos: List<ACVideo>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ACVideoAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemAcvideoListBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemAcvideoListBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = videos[position]
        with(holder.binding) {
            tvWechatName.text = if (video.nameOfWechat.isBlank()) "微信用户" else video.nameOfWechat
            tvWechatId.text = "微信号: ${video.numberOfWechat}"
            tvRequestType.text = "前缀: ${video.requestType}"
            tvRequestCode.text = "后缀: ${video.requestCode}"
            tvVideoUrl.text = video.url
            tvTime.text = formatterYMD.format(Date(video.time))

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
        return videos.size
    }

}