package com.lockscreen.player.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lockscreen.player.databinding.BottomSheetSettingsBinding
import com.lockscreen.player.locale.AppLanguage
import com.lockscreen.player.locale.LocalePreference

class SettingsBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.currentLanguageText.text = getString(
            LocalePreference.getLanguage(requireContext()).labelRes,
        )
        binding.languageSettingRow.setOnClickListener {
            val host = activity as? FragmentActivity ?: return@setOnClickListener
            dismiss()
            LanguagePickerDialogFragment.show(host)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingsBottomSheet"

        fun show(host: FragmentActivity) {
            if (host.supportFragmentManager.findFragmentByTag(TAG) != null) return
            SettingsBottomSheetFragment().show(host.supportFragmentManager, TAG)
        }
    }
}
