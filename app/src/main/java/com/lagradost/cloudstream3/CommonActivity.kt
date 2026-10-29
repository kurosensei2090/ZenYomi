package com.lagradost.cloudstream3

import android.app.Activity
import android.widget.Toast
import java.lang.ref.WeakReference

object CommonActivity {
    var activity: WeakReference<Activity>? = null

    fun showToast(activity: Activity?, message: String?, duration: Int = Toast.LENGTH_SHORT) {
        val act = activity ?: this.activity?.get() ?: return
        act.runOnUiThread {
            Toast.makeText(act, message ?: "", duration).show()
        }
    }

    fun showToast(message: String?, duration: Int = Toast.LENGTH_SHORT) {
        val act = activity?.get() ?: return
        act.runOnUiThread {
            Toast.makeText(act, message ?: "", duration).show()
        }
    }
}
