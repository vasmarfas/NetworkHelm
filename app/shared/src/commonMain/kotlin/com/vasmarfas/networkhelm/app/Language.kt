package com.vasmarfas.networkhelm.app

val Languages = mapOf("en" to "English", "ru" to "Русский")

// iOS keeps the language per app in its own settings, and the browser takes it from the page
expect val canSetLanguage: Boolean

expect fun setLanguage(tag: String?)
