package com.expeysi.app.ui.home

import android.Manifest
import android.app.LocaleManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/*
 * Отображение времени в формате устройства, как часы в статус-баре: системная
 * настройка «24-часовой формат» главнее локали, а без неё — короткий
 * локализованный шаблон. Локаль берём системную, а не контекста: у приложения
 * свой per-app язык, и его подмена не должна менять формат часов.
 */

/** Формат настроек TIME_12_24 для явного 24-часового формата. */
private const val SETTING_24H = "24"

/** Формат настроек TIME_12_24 для явного 12-часового формата. */
private const val SETTING_12H = "12"

/** Время [hour]:[minute] в формате устройства: настройка 12/24-hour и системная локаль. */
internal fun formatDeviceTime(context: Context, hour: Int, minute: Int): String {
    val setting = Settings.System.getString(context.contentResolver, Settings.System.TIME_12_24)
    return formatTime(hour, minute, setting, deviceLocale(context))
}

/**
 * Чистая функция для тестов: время [hour]:[minute] по значению настройки TIME_12_24
 * ([setting12Or24], null — не задана) и локали [locale].
 */
internal fun formatTime(hour: Int, minute: Int, setting12Or24: String?, locale: Locale): String {
    val time = LocalTime.of(hour, minute)
    val formatter = when (setting12Or24) {
        SETTING_24H -> DateTimeFormatter.ofPattern("HH:mm").withLocale(locale)
        SETTING_12H -> DateTimeFormatter.ofPattern("h:mm a").withLocale(locale)
        // Без настройки шаблон локали сам решает, 12 или 24 часа на часах.
        else -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    }
    return formatter.format(time)
}

/**
 * Часы устройства идут в 24-часовом формате? Согласовано с [formatTime]:
 * та же настройка TIME_12_24, а без неё — правила локали.
 */
internal fun deviceUses24Hour(context: Context): Boolean {
    val setting = Settings.System.getString(context.contentResolver, Settings.System.TIME_12_24)
    if (setting != null) {
        return setting == SETTING_24H
    }
    return localeUses24Hour(deviceLocale(context))
}

/**
 * Локаль 24-часовая? Признак 12-часового шаблона — буквенный маркер AM/PM,
 * поэтому пробуем на часах 24-й час: в 12-часовом выводе такого не бывает.
 */
private fun localeUses24Hour(locale: Locale): Boolean {
    val sample = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale)
        .format(LocalTime.of(15, 30))
    return sample.none(Char::isLetter)
}

/**
 * Локаль устройства, а не приложения: per-app язык не должен влиять на
 * формат часов. LocaleManager.systemLocales даёт именно системный список;
 * Resources.getSystem() на новых API тоже подменяется per-app языком, поэтому
 * годится только как fallback до Android 13, где такого механизма не было.
 */
internal fun deviceLocale(context: Context): Locale {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val systemLocales = context.getSystemService(LocaleManager::class.java)?.systemLocales
        if (systemLocales != null && !systemLocales.isEmpty) {
            return systemLocales[0]
        }
    }
    return Resources.getSystem().configuration.locales[0]
}

/**
 * POST_NOTIFICATIONS — рантайм-разрешение Android 13+; до 13-го уведомления
 * включены по умолчанию и запрос не нужен.
 */
internal fun reminderNeedsNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        // До Android 13 уведомления включены по умолчанию, запрос не нужен.
        return false
    }
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
}
