package io.github.vagrant326.atvletterwise.update

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import io.github.vagrant326.atvletterwise.BuildConfig
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

private sealed interface Outcome {
    data class Latest(val tag: String) : Outcome
    data object NoReleases : Outcome
    data class Failed(val detail: String) : Outcome
}

/**
 * The only component in this app that touches the network, and it runs in its own process
 * (`:updater`, see the manifest) so the component handling keystrokes contains no
 * networking code at all.
 *
 * It runs when the user opens this screen and not otherwise: no background job, no boot
 * receiver, no periodic poll, no check when the keyboard starts. One request, no payload,
 * no device identifier, no analytics. Nothing typed on this device is ever sent anywhere.
 *
 * All of it exists only because sideloading has no update channel. It comes out if the
 * project ever gets a Play Store listing.
 */
class UpdateActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            text = "Installed ${BuildConfig.VERSION_NAME}\nChecking…"
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.BLACK)
                val padding = (24 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
                addView(status)
            }
        )
        Executors.newSingleThreadExecutor().execute {
            val outcome = check()
            runOnUiThread { report(outcome) }
        }
    }

    private fun report(outcome: Outcome) {
        val installed = BuildConfig.VERSION_NAME
        status.text = when (outcome) {
            is Outcome.NoReleases ->
                "Installed $installed\nNo releases published yet."

            is Outcome.Failed ->
                "Installed $installed\nCheck failed: ${outcome.detail}"

            is Outcome.Latest -> if (outcome.tag.removePrefix("v") == installed) {
                "Installed $installed\nUp to date."
            } else {
                "Installed $installed\nAvailable: ${outcome.tag}\n\n" +
                    "Open $RELEASES_URL in a downloader app to install it."
            }
        }
    }

    private fun check(): Outcome {
        val connection = try {
            URL(API_URL).openConnection() as HttpsURLConnection
        } catch (failure: Exception) {
            return Outcome.Failed(failure.javaClass.simpleName)
        }
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")

            // Android's HttpURLConnection is OkHttp-backed and can throw here on a 404
            // rather than returning the code. A 404 is the normal state before the first
            // release exists, so it must not surface as an error.
            val status = try {
                connection.responseCode
            } catch (absent: FileNotFoundException) {
                HttpURLConnection.HTTP_NOT_FOUND
            }

            when {
                status == HttpURLConnection.HTTP_NOT_FOUND -> Outcome.NoReleases
                status !in 200..299 -> Outcome.Failed("HTTP $status")
                else -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    TAG_NAME.find(body)?.groupValues?.get(1)
                        ?.let { Outcome.Latest(it) }
                        ?: Outcome.Failed("no tag_name in response")
                }
            }
        } catch (failure: Exception) {
            Outcome.Failed(failure.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val API_URL =
            "https://api.github.com/repos/vagrant326/atv-letterwise/releases/latest"
        const val RELEASES_URL =
            "https://github.com/vagrant326/atv-letterwise/releases/latest"
        const val TIMEOUT_MS = 10_000
        val TAG_NAME = """"tag_name"\s*:\s*"([^"]+)"""".toRegex()
    }
}
