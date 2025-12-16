package com.example.garbage_truck

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.garbage_truck.databinding.FragmentSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // 設定「登入 / 註冊」按鈕 -> 跳轉到 LoginFragment
        binding.btnGoToLogin.setOnClickListener {
            val action = SettingsFragmentDirections.actionSettingsFragmentToLoginFragment()
            findNavController().navigate(action)
        }

        // 設定「登出」按鈕
        binding.btnLogout.setOnClickListener {
            auth.signOut()
            updateUI(null)
        }

        // 設定「我的最愛清運點」按鈕
        binding.btnFavoriteLocations.setOnClickListener {
            val action = SettingsFragmentDirections.actionSettingsFragmentToFavoriteAddressFragment()
            findNavController().navigate(action)
        }

        // 從偏好設定取出已儲存的深色模式狀態
        val prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val darkModeOn = prefs.getBoolean("dark_mode", false)
        val rememberDarkMode = prefs.getBoolean("remember_dark_mode", true)

        // 初始化列表，用現在的狀態填入
        val items = listOf(
            SettingItem("深色模式", darkModeOn),
            SettingItem("是否記憶深色模式", rememberDarkMode)
        )

        binding.settingsList.layoutManager = LinearLayoutManager(requireContext())
        binding.settingsList.adapter = SettingsAdapter(items) { item ->
            // 未來若有其他設定項目，可在此處理
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI(auth.currentUser)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            // === 已登入 ===
            binding.btnGoToLogin.visibility = View.GONE
            binding.btnLogout.visibility = View.VISIBLE

            val name = user.displayName
            val email = user.email

            binding.tvUsername.text = if (!name.isNullOrEmpty()) name else "使用者"
            binding.tvEmail.text = email

            // 載入頭像
            user.photoUrl?.let {
                Glide.with(this)
                    .load(it)
                    .circleCrop()
                    .into(binding.ivProfile)
            }
        } else {
            // === 未登入 ===
            binding.btnGoToLogin.visibility = View.VISIBLE
            binding.btnLogout.visibility = View.GONE

            binding.tvUsername.text = "尚未登入"
            binding.tvEmail.text = "點擊此處進行登入以同步資料"
            binding.ivProfile.setImageResource(R.drawable.ic_feature) // 回復預設圖示
        }
    }
}