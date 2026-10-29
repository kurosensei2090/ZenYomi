package com.lagradost.cloudstream3

import android.app.Application
import android.content.Context
import java.lang.ref.WeakReference

open class AcraApplication : Application() {
    companion object {
        private var _context: WeakReference<Context>? = null
        var context: Context?
            get() = _context?.get()
            set(value) {
                _context = if (value != null) WeakReference(value.applicationContext) else null
            }

        fun getKey(): ByteArray? = null
        fun setKey(key: ByteArray?) {}
    }

    override fun onCreate() {
        super.onCreate()
        context = this
    }
}

class CloudStreamApp {
    companion object {
        var context: Context?
            get() = AcraApplication.context
            set(value) {
                AcraApplication.context = value
            }
    }
}
