package com.lagradost.cloudstream3

import android.app.Activity
import android.widget.Toast
import java.lang.ref.WeakReference

object CommonActivity {
    var activityRef: WeakReference<Activity>? = null

    var activity: Activity?
        get() = activityRef?.get()
        set(value) {
            activityRef = if (value != null) WeakReference(value) else null
        }

    fun setActivityInstance(newActivity: Activity?) {
        activity = newActivity
    }

    fun showToast(targetActivity: Activity?, message: String?, duration: Int = Toast.LENGTH_SHORT) {
        val act = targetActivity ?: activity ?: return
        act.runOnUiThread {
            Toast.makeText(act, message ?: "", duration).show()
        }
    }

    fun showToast(message: String?, duration: Int = Toast.LENGTH_SHORT) {
        val act = activity ?: return
        act.runOnUiThread {
            Toast.makeText(act, message ?: "", duration).show()
        }
    }
}

