package com.lagradost.cloudstream3.utils

object SubtitleHelper {
    fun fromThreeLettersToLanguage(code: String): String {
        return when (code.lowercase()) {
            "eng" -> "English"
            "ara" -> "Arabic"
            "spa" -> "Spanish"
            "fre", "fra" -> "French"
            "ger", "deu" -> "German"
            "ita" -> "Italian"
            "por" -> "Portuguese"
            "rus" -> "Russian"
            "jpn" -> "Japanese"
            "kor" -> "Korean"
            "chi", "zho" -> "Chinese"
            "hin" -> "Hindi"
            "ind" -> "Indonesian"
            else -> code
        }
    }

    fun fromTwoLettersToLanguage(code: String): String {
        return when (code.lowercase()) {
            "en" -> "English"
            "ar" -> "Arabic"
            "es" -> "Spanish"
            "fr" -> "French"
            "de" -> "German"
            "it" -> "Italian"
            "pt" -> "Portuguese"
            "ru" -> "Russian"
            "ja" -> "Japanese"
            "ko" -> "Korean"
            "zh" -> "Chinese"
            "hi" -> "Hindi"
            "id" -> "Indonesian"
            else -> code
        }
    }
}
