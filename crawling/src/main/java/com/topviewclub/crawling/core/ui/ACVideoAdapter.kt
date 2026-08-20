package com.topviewclub.crawling.core.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.topviewclub.crawling.core.R
import com.topviewclub.crawling.wechat.auto.room.acv.ACVideo
import java.text.SimpleDateFormat
import java.util.*

class ACVideoAdapter(
    private val videos: List<ACVideo>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ACVideoAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val textView = v.findViewById<TextView>(R.id.tv_list_text)!!
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_acvideo_list, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = videos[position].let {
            """
                前缀 | ${it.requestType}
                后缀 | ${it.requestCode}
                微信名 | ${it.nameOfWechat}
                微信号 | ${it.numberOfWechat}
                时间 | ${formatterYMD.format(Date(it.time))}
            """.trimIndent()
        }
        holder.textView.setOnClickListener {
            onClick(position)
        }
    }

    private val formatterYMD = SimpleDateFormat(
        "yyyy年M月d日HH:mm:ss.SSS",
        Locale.getDefault()
    )

    override fun getItemCount(): Int {
        return videos.size
    }

}