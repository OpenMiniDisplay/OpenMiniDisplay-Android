package com.openminidisplay.settings

enum class AppColorScheme(val storageKey: String) {
    DARK("dark"),
    LIGHT("light"),
    OLED("oled"),
    ;

    companion object {
        fun fromKey(key: String?): AppColorScheme =
            entries.firstOrNull { it.storageKey == key } ?: DARK
    }
}
