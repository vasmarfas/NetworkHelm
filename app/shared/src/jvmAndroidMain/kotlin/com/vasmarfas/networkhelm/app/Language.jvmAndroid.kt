package com.vasmarfas.networkhelm.app

import java.util.Locale

private var system: Locale? = null

actual val canSetLanguage = true

actual fun setLanguage(tag: String?) {
    if (tag != null && system == null) system = Locale.getDefault()
    val locale = tag?.let(Locale::forLanguageTag) ?: system
    if (locale != null) Locale.setDefault(locale)
}
