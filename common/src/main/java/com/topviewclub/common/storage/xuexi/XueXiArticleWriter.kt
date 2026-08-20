package com.topviewclub.common.storage.xuexi

import com.topviewclub.common.base.appContext
import com.topviewclub.common.bean.XueXiArticle
import com.topviewclub.common.util.defaultOutputDirectory
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

object XueXiArticleWriter {

    fun writeXueXiArticleSet(articles: Set<XueXiArticle>, tag: String) {
        val file = File(appContext.defaultOutputDirectory(), "xuexi_${tag}.txt")
        if (!file.exists()) file.createNewFile()
        val out = OutputStreamWriter(FileOutputStream(file))
        val sb = StringBuilder()
        articles.map {
            sb.append(it).append("\n")
        }
        out.write(sb.toString())
        out.flush()
    }

}