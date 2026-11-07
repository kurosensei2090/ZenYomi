package com.lagradost.cloudstream3

import android.app.Activity
import android.widget.Toast
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

object CommonActivity {
    private val activityStack = CopyOnWriteArrayList<WeakReference<Activity>>()

    var activity: Activity?
        get() = activityStack.asReversed()
            .mapNotNull { it.get() }
            .firstOrNull { !it.isFinishing && !it.isDestroyed }
        set(value) {
            if (value != null) {
                activityStack.removeAll { it.get() == null || it.get() === value }
                activityStack.add(WeakReference(value))
            }
        }

    fun removeActivity(act: Activity) {
        activityStack.removeAll { it.get() == null || it.get() === act }
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


