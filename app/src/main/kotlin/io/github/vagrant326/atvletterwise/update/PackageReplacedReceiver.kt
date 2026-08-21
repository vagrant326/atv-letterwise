package io.github.vagrant326.atvletterwise.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.vagrant326.atvletterwise.settings.Preferences
import io.github.vagrant326.atvletterwise.settings.SettingsActivity

/**
 * Tries to reopen the app after it has updated itself.
 *
 * Android stops an app's processes when it replaces its APK, so the update screen dies
 * mid-install and the user has to go back to the home screen. This is an attempt to avoid
 * that, and an experiment rather than a feature: from Android 10 onwards starting an activity
 * from the background is blocked, and a broadcast receiver is background. It may well do
 * nothing.
 *
 * Which is why it records what happened. "Did the receiver even run" and "did the launch get
 * refused" are different problems with different answers, and after an update there is no log
 * to consult - the process that would have written one was killed.
 */
class PackageReplacedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val preferences = Preferences(context)
        val launch = Intent(context, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        preferences.lastRelaunch = runCatching { context.startActivity(launch) }.fold(
            onSuccess = { "accepted" },
            onFailure = { "refused: ${it.javaClass.simpleName}" },
        )
    }
}
