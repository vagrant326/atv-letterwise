package io.github.vagrant326.atvletterwise.update

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.github.vagrant326.atvletterwise.BuildConfig
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

private sealed interface Check {
    data class Newer(val tag: String) : Check
    data object UpToDate : Check
    data object NoReleases : Check
    data class Failed(val detail: String) : Check
}

/**
 * The only component in this app that touches the network, and it runs in its own process
 * (`:updater`, see the manifest) so the component handling keystrokes contains no networking
 * and no install code at all.
 *
 * Nothing runs unless the user opens this screen and presses something: no background job, no
 * boot receiver, no periodic poll, no check when the keyboard starts. One request for the
 * version, one for the file, no payload, no device identifier, no analytics. Nothing typed on
 * this device is ever sent anywhere.
 *
 * Installing goes through [PackageInstaller] rather than an `ACTION_VIEW` intent. The intent
 * route cannot report anything: handing it a file succeeds even when the installer then shows
 * nothing, which is exactly the dead end the second update ran into — the screen could only
 * say "if nothing appeared, try again". A session reports back a status and a message, and
 * hands us the confirmation dialog explicitly instead of leaving it to chance.
 *
 * All of it exists only because sideloading has no update channel. It comes out if the
 * project ever ships through a store.
 */
class UpdateActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var action: TextView
    private val worker = Executors.newSingleThreadExecutor()

    private val installResult = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (val code = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // The confirmation dialog. With the intent route this step was implicit and
                    // could silently not happen; here it is ours to launch.
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    if (confirm == null) {
                        status.text = "The system asked for confirmation but sent no dialog."
                        return
                    }
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { startActivity(confirm) }.onFailure {
                        status.text = "Could not open the confirmation dialog: " +
                            it.javaClass.simpleName
                    }
                }

                PackageInstaller.STATUS_SUCCESS -> {
                    status.text = "Installed. Reopen the app to see the new version."
                    action.visibility = TextView.GONE
                }

                else -> {
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    status.text = "Install failed (status $code)\n${message ?: "no detail"}"
                    action.text = "Try again"
                    action.isClickable = true
                    action.setOnClickListener { download() }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            text = "Installed ${BuildConfig.VERSION_NAME}\nChecking…"
        }
        action = TextView(this).apply {
            setTextColor(ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            visibility = TextView.GONE
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(ROW)
            layoutParams = LinearLayout.LayoutParams(dp(420), ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(20) }
            setOnFocusChangeListener { view, hasFocus ->
                view.setBackgroundColor(if (hasFocus) ROW_FOCUSED else ROW)
            }
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(BACKGROUND)
                setPadding(dp(28), dp(28), dp(28), dp(28))
                addView(status)
                addView(action)
            }
        )

        ContextCompat.registerReceiver(
            this,
            installResult,
            IntentFilter(installAction()),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        worker.execute {
            val outcome = check()
            runOnUiThread { present(outcome) }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(installResult) }
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun present(result: Check) {
        val installed = BuildConfig.VERSION_NAME
        when (result) {
            is Check.UpToDate -> status.text = "Installed $installed\nUp to date."
            is Check.NoReleases -> status.text = "Installed $installed\nNo releases published yet."
            is Check.Failed -> status.text = "Installed $installed\nCheck failed: ${result.detail}"

            is Check.Newer -> {
                status.text = "Installed $installed\nAvailable: ${result.tag}"
                action.text = "Download and install"
                action.visibility = TextView.VISIBLE
                action.requestFocus()
                action.setOnClickListener { download() }
            }
        }
    }

    private fun download() {
        action.isClickable = false
        action.text = "Downloading…"
        worker.execute {
            val target = File(cacheDir, "update.apk")
            val outcome = runCatching { fetch(target) }
            runOnUiThread {
                outcome.fold(
                    onSuccess = { install(target) },
                    onFailure = {
                        status.text = "Download failed: ${it.javaClass.simpleName}\n" +
                            (it.message ?: "")
                        action.text = "Try again"
                        action.isClickable = true
                    },
                )
            }
        }
    }

    private fun fetch(target: File) {
        // A stale file from the previous attempt is one of the few things that differs between
        // the first update and the second, so start from nothing rather than writing over it.
        target.delete()
        val connection = URL(DOWNLOAD_URL).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            val total = connection.contentLength.toLong()
            var read = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) {
                            break
                        }
                        output.write(buffer, 0, count)
                        read += count
                        if (total > 0) {
                            val percent = (read * 100 / total).toInt()
                            runOnUiThread { action.text = "Downloading… $percent%" }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        // An APK is a zip. Checking the magic bytes turns "the installer did nothing" into a
        // message here, where the cause is still visible.
        require(target.length() > MINIMUM_APK_BYTES) {
            "downloaded ${target.length()} bytes, too small to be an APK"
        }
        val header = target.inputStream().use { input -> ByteArray(2).also { input.read(it) } }
        require(header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()) {
            "downloaded file is not a zip, so not an APK"
        }
    }

    private fun install(apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            status.text = "Allow this app to install packages, then press install again."
            action.text = "Open the permission screen"
            action.isClickable = true
            action.setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName"),
                    )
                )
                action.text = "Install"
                action.setOnClickListener { install(apk) }
            }
            return
        }

        status.text = "Handing ${apk.length() / 1024} kB to the installer…"
        action.text = "Working…"
        action.isClickable = false

        val outcome = runCatching {
            val installer = packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite(APK_ENTRY, 0, apk.length()).use { output ->
                    apk.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                }
                session.commit(pendingResult(sessionId).intentSender)
            }
        }
        outcome.onFailure { failure ->
            status.text = "Could not start the install: ${failure.javaClass.simpleName}\n" +
                (failure.message ?: "")
            action.text = "Try again"
            action.isClickable = true
            action.setOnClickListener { download() }
        }
    }

    /** Mutable on purpose: the system fills in the status extras on this intent. */
    private fun pendingResult(sessionId: Int): PendingIntent {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        return PendingIntent.getBroadcast(
            this,
            sessionId,
            Intent(installAction()).setPackage(packageName),
            flags,
        )
    }

    private fun installAction() = "$packageName.INSTALL_RESULT"

    private fun check(): Check {
        val connection = try {
            URL(API_URL).openConnection() as HttpsURLConnection
        } catch (failure: Exception) {
            return Check.Failed(failure.javaClass.simpleName)
        }
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")

            // Android's HttpURLConnection is OkHttp-backed and can throw here on a 404 rather
            // than returning the code. Before the first release exists a 404 is the normal
            // state, so it must not surface as an error.
            val code = try {
                connection.responseCode
            } catch (absent: FileNotFoundException) {
                HttpURLConnection.HTTP_NOT_FOUND
            }

            when {
                code == HttpURLConnection.HTTP_NOT_FOUND -> Check.NoReleases
                code !in 200..299 -> Check.Failed("HTTP $code")
                else -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val tag = TAG_NAME.find(body)?.groupValues?.get(1)
                        ?: return Check.Failed("no tag_name in response")
                    if (isNewer(tag, BuildConfig.VERSION_NAME)) Check.Newer(tag) else Check.UpToDate
                }
            }
        } catch (failure: Exception) {
            Check.Failed(failure.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Compares numerically rather than by string equality, so a debug build reporting
     * `0.0.0-dev` sees any release as newer and `0.0.10` is not treated as older than `0.0.9`.
     */
    private fun isNewer(latest: String, installed: String): Boolean {
        fun parts(version: String) =
            version.trim().removePrefix("v").split('.', '-').mapNotNull { it.toIntOrNull() }

        val newer = parts(latest)
        val current = parts(installed)
        for (index in 0 until maxOf(newer.size, current.size)) {
            val a = newer.getOrElse(index) { 0 }
            val b = current.getOrElse(index) { 0 }
            if (a != b) {
                return a > b
            }
        }
        return false
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val API_URL =
            "https://api.github.com/repos/vagrant326/atv-letterwise/releases/latest"
        const val DOWNLOAD_URL =
            "https://github.com/vagrant326/atv-letterwise/releases/download/latest/atv-letterwise.apk"
        const val TIMEOUT_MS = 20_000
        const val MINIMUM_APK_BYTES = 100_000L
        const val APK_ENTRY = "atv-letterwise.apk"
        val TAG_NAME = """"tag_name"\s*:\s*"([^"]+)"""".toRegex()

        const val BACKGROUND = 0xFF08080B.toInt()
        const val ROW = 0xFF16161C.toInt()
        const val ROW_FOCUSED = 0xFF2A3A46.toInt()
        const val ACCENT = 0xFF7FD1FF.toInt()
    }
}
