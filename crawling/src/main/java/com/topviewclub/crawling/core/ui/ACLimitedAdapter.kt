package com.topviewclub.crawling.core.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.topviewclub.crawling.core.R
import com.topviewclub.crawling.wechat.auto.room.acl.ACLimited
import java.text.SimpleDateFormat
import java.util.*

class ACLimitedAdapter(
    private val users: List<ACLimited>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ACLimitedAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val textView = v.findViewById<TextView>(R.id.tv_list_text)!!
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_aclimited_list, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = users[position].let {
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
        holder.textView.setOnClickListener {
            onClick(position)
        }
    }

    private val formatterYMD = SimpleDateFormat(
        "yyyy年M月d日HH:mm:ss.SSS",
        Locale.getDefault()
    )

    override fun getItemCount(): Int {
        return users.size
    }

}