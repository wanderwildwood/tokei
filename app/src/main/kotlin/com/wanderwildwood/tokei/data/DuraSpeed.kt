package com.wanderwildwood.tokei.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import com.wanderwildwood.tokei.BuildConfig
import java.io.File

/**
 * DuraSpeed, MediaTek's background manager on the Kompakt, force-stops installed apps a few
 * minutes after the screen goes dark, and a force-stop cancels every alarm an app has set:
 * the reminders do not ring, and nothing starts the app again until somebody opens it. Apps
 * switched on in its list are left alone, but that list cannot be read by another app, and
 * Settings has no way into it. Its App info page can be opened, and has an Open button, so
 * that is where the button goes; whether it was done is the person's word.
 *
 * A stop by the system takes that word back: DuraSpeed does not stop apps on its list.
 * Android records such a stop as "stop <package> due to from pid N". A Force stop pressed by
 * hand in Settings reads the same, and is the one case this gets wrong.
 */
object DuraSpeed {

    private const val PACKAGE = "com.mediatek.duraspeed"

    /**
     * A Kompakt, or, in a debug build only, an emulator told to act as one by a file named
     * `pretend-kompakt` in the app's files, so the setup step can be seen without the phone.
     */
    fun isKompakt(context: Context): Boolean =
        Build.MANUFACTURER.equals("Mudita", ignoreCase = true) ||
            (BuildConfig.DEBUG && File(context.filesDir, "pretend-kompakt").exists())

    /** DuraSpeed's own switch; null where the phone does not say. */
    private fun isOn(context: Context): Boolean? = runCatching {
        val cr = context.contentResolver
        (AndroidSettings.Global.getString(cr, "setting.duraspeed.enabled")
            ?: AndroidSettings.System.getString(cr, "setting.duraspeed.enabled"))?.let { it != "0" }
    }.getOrNull()

    /**
     * Notes the newest stop by the system that has not been seen yet, and returns its time, or
     * null. Any phone can be stopped from Settings, so this is not only for a Kompakt.
     */
    fun newStop(context: Context, settings: Settings): Long? {
        val am = context.getSystemService(ActivityManager::class.java)
        val stop = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 0) }
            .getOrDefault(emptyList())
            .filter {
                it.reason == ApplicationExitInfo.REASON_USER_REQUESTED &&
                    it.description?.contains("due to from pid") == true
            }
            .maxOfOrNull { it.timestamp } ?: return null
        if (stop <= settings.stopSeen) return null
        settings.stopSeen = stop
        // Only a stop while DuraSpeed is on is DuraSpeed's.
        if (isKompakt(context) && isOn(context) != false) settings.duraSpeedAllowed = false
        return stop
    }

    /** Whether the one setup step is still to do: shown on the first screen while it is. */
    fun atRisk(context: Context, settings: Settings): Boolean =
        isKompakt(context) && isOn(context) != false && !settings.duraSpeedAllowed

    fun appInfo(): Intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:$PACKAGE"))
}
