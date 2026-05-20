package com.lockscreen.player.locale

import androidx.annotation.StringRes
import com.lockscreen.player.R

enum class AppLanguage(
    /** BCP 47 tag; null = follow system. */
    val tag: String?,
    @StringRes val labelRes: Int,
) {
    SYSTEM(null, R.string.language_system),
    ZH_TW("zh-TW", R.string.language_name_zh_tw),
    EN("en", R.string.language_name_en),
    JA("ja", R.string.language_name_ja),
    ;

    companion object {
        fun fromStoredTag(stored: String?): AppLanguage {
            return when (stored) {
                ZH_TW.tag -> ZH_TW
                EN.tag -> EN
                JA.tag -> JA
                else -> SYSTEM
            }
        }
    }
}
