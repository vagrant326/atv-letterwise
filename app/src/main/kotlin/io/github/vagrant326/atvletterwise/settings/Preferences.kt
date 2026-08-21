package io.github.vagrant326.atvletterwise.settings

import android.content.Context

class Preferences(context: Context) {

    private val store = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /**
     * Defaults to on. The remote's number keys carry no letters, so without the legend
     * there is nothing anywhere that says which key holds which letters.
     */
    var showLegend: Boolean
        get() = store.getBoolean(KEY_SHOW_LEGEND, true)
        set(value) = store.edit().putBoolean(KEY_SHOW_LEGEND, value).apply()

    private companion object {
        const val NAME = "letterwise"
        const val KEY_SHOW_LEGEND = "show_legend"
    }
}
