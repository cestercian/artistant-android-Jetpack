package `in`.artistant.app.common.util

import java.util.Locale

/**
 * The backend RLS + queries expect lowercase UUIDs everywhere (a load-bearing
 * iOS behaviour). Locale.ROOT so a Turkish-locale device doesn't lowercase 'I'
 * to a dotless 'ı'.
 */
fun String.lowercaseUuid(): String = lowercase(Locale.ROOT)
