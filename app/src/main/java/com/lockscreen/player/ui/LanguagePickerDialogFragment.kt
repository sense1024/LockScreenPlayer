package com.lockscreen.player.ui

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lockscreen.player.R
import com.lockscreen.player.locale.AppLanguage
import com.lockscreen.player.locale.LocalePreference

class LanguagePickerDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val languages = AppLanguage.entries.toTypedArray()
        val labels = languages.map { getString(it.labelRes) }.toTypedArray()
        val current = LocalePreference.getLanguage(requireContext())
        val checkedIndex = languages.indexOf(current).coerceAtLeast(0)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_language_title)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                val selected = languages[which]
                if (selected != current) {
                    LocalePreference.setLanguage(requireContext(), selected)
                    dialog.dismiss()
                    requireActivity().recreate()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        const val TAG = "LanguagePickerDialog"

        fun show(host: androidx.fragment.app.FragmentActivity) {
            if (host.supportFragmentManager.findFragmentByTag(TAG) != null) return
            LanguagePickerDialogFragment().show(host.supportFragmentManager, TAG)
        }
    }
}
