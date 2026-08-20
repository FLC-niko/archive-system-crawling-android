@file:Suppress("NOTHING_TO_INLINE")

package com.topviewclub.common.log

import android.util.Log
import androidx.annotation.IntDef
import kotlin.annotation.AnnotationTarget.*

object Level {
    const val V = 1
    const val D = 2
    const val I = 4
    const val W = 8
    const val E = 16
    const val WTF = 32
    const val SILENT = 64
}

@PublishedApi
@LogLevel
internal const val LOG_LEVEL = Level.I

@Target(FIELD, LOCAL_VARIABLE, PROPERTY, VALUE_PARAMETER)
@IntDef(value = [Level.V, Level.D, Level.I, Level.W, Level.E, Level.WTF, Level.SILENT])
annotation class LogLevel

inline fun logV(tag: String = "", msg: String = "") {
    if (LOG_LEVEL <= Level.V) {
        Log.v(tag, "AAOS $msg")
    }
}

inline fun logD(tag: String = "", msg: String = "") {
    if (LOG_LEVEL <= Level.D) {
        Log.d(tag, "AAOS $msg")
    }
}

inline fun logI(tag: String = "", msg: String = "") {
    if (LOG_LEVEL <= Level.I) {
        Log.i(tag, "AAOS $msg")
    }
}

inline fun logW(tag: String = "", msg: String = "") {
    if (LOG_LEVEL <= Level.W) {
        Log.w(tag, "AAOS $msg")
    }
}

inline fun logE(tag: String = "", msg: String = "") {
    if (LOG_LEVEL <= Level.E) {
        Log.e(tag, "AAOS $msg")
    }
}
inline fun logHEART(tag: String = "", msg: String = "") {
    if (LOG_LEVEL <= Level.SILENT) {
        Log.e(tag, "AAHEARTBEATOS $msg")
    }
}

inline fun logWTF(tag: String = "", msg: String = "") {
    if (LOG_LEVEL <= Level.WTF) {
        Log.wtf(tag, "AAOS $msg")
    }
}

inline fun logRabbit( msg: String = "") {
    if (LOG_LEVEL <= Level.E) {
        Log.e("Rabbit", "Rabbit $msg")
    }

}

