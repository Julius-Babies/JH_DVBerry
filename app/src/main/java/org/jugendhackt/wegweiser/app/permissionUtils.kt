package org.jugendhackt.wegweiser.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat.checkSelfPermission

fun checkPermission(context: Context, permission: String): Boolean {
    return checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}