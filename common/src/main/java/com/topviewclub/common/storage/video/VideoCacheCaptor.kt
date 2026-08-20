package com.topviewclub.common.storage.video

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.topviewclub.common.log.logE
import com.topviewclub.common.log.logI
import com.topviewclub.common.log.logRabbit
import com.topviewclub.common.storage.DocumentFileUtils
import com.topviewclub.common.util.className

const val WECHAT_CACHE_FOLDER = "/storage/emulated/0/Android/data/com.tencent.mm/cache"

class WechatVideoCacheCaptor(private val context: Context) {

    /**
     * 移除微信视频缓存目录下的所有文件
     * */
    fun removeAllVideosFromWechat() {
        Thread {
            // 寻找视频文件
            val videoFiles = findVideos()
            logRabbit("remove Videos ${videoFiles}")
            videoFiles.map { it.delete() }
        }.start()
    }

    private fun findVideos(): List<DocumentFile> {
        val docFile = DocumentFile.fromTreeUri(
            context, Uri.parse(
                DocumentFileUtils.changeToUri(
                    WECHAT_CACHE_FOLDER
                )
            )
        )!!
        // 遍历微信 cache 文件夹下的所有文件
        val caches = docFile.listFiles()
        val cacheTargetFolders = mutableListOf<DocumentFile>()
        for (i in caches.indices) {
            val target = caches[i]
            // 找到文件名长度为 32 的文件夹
            if (target.name?.length == 32) {
                cacheTargetFolders.add(target)
            }
        }

        val finders = mutableListOf<DocumentFile>()
        // 找 finder 文件夹
        cacheTargetFolders.map {
            it.findFile("finder")?.let { target ->
                finders.add(target)
            }
        }
//        // 找 finderbsy 文件夹
//        cacheTargetFolders.map {
//            it.findFile("finderbsy")?.let { target ->
//                finders.add(target)
//            }
//        }

        val videoFolders = mutableListOf<DocumentFile>()
        // 找 video 文件夹
        finders.map {
            it.findFile("video")?.let { target ->
                videoFolders.add(target)
            }
        }

        val videoFiles = mutableListOf<DocumentFile>()
        // 找 mp4 文件
        videoFolders.map {
            videoFiles.addAll(it.listFiles())
        }

        // 根据修改时间升序排序
//        videoFiles.sortWith { file1, file2 ->
//            (file1.lastModified() - file2.lastModified()).toInt()
//        }

        return videoFiles
    }

}