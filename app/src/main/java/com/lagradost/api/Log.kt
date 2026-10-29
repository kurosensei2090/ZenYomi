package com.lagradost.api

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

object Log {
    fun d(tag: String, msg: String) = logcat(LogPriority.DEBUG) { "[$tag] $msg" }
    fun i(tag: String, msg: String) = logcat(LogPriority.INFO) { "[$tag] $msg" }
    fun w(tag: String, msg: String) = logcat(LogPriority.WARN) { "[$tag] $msg" }
    fun e(tag: String, msg: String) = logcat(LogPriority.ERROR) { "[$tag] $msg" }
    fun e(tag: String, msg: String, tr: Throwable) = logcat(LogPriority.ERROR, tr) { "[$tag] $msg" }
}
