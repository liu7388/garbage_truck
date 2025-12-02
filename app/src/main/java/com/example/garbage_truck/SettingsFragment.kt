package com.example.garbage_truck

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var auth: FirebaseAuth
    private lateinit var ivProfile: ImageView
    private lateinit var tvUsername: TextView
    private lateinit var tvEmail: TextView
    private lateinit var btnGoLogin: Button
    private lateinit var btnLogout: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // 綁定 UI
        ivProfile = view.findViewById(R.id.iv_profile)
        tvUsername = view.findViewById(R.id.tv_username)
        tvEmail = view.findViewById(R.id.tv_email)
        btnGoLogin = view.findViewById(R.id.btn_go_to_login)
        btnLogout = view.findViewById(R.id.btn_logout)

        // 設定「登入 / 註冊」按鈕 -> 跳轉到 LoginFragment
        btnGoLogin.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_loginFragment)
        }

        // 設定「登出」按鈕
        btnLogout.setOnClickListener {
            auth.signOut()
            updateUI(null)
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.settingsList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // 從偏好設定取出已儲存的深色模式狀態
        val prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val darkModeOn = prefs.getBoolean("dark_mode", false)
        val rememberDarkMode = prefs.getBoolean("remember_dark_mode", true)

        // 初始化列表，用現在的狀態填入
        val items = listOf(
            SettingItem("深色模式", darkModeOn),
            SettingItem("是否記憶深色模式", rememberDarkMode)
        )

        recyclerView.adapter = SettingsAdapter(items)
    }

    override fun onResume() {
        super.onResume()
        updateUI(auth.currentUser)
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            // === 已登入 ===
            btnGoLogin.visibility = View.GONE
            btnLogout.visibility = View.VISIBLE

            val name = user.displayName
            val email = user.email

            tvUsername.text = if (!name.isNullOrEmpty()) name else "使用者"
            tvEmail.text = email

            // 載入頭像
            user.photoUrl?.let {
                Glide.with(this)
                    .load(it)
                    .circleCrop()
                    .into(ivProfile)
            }
        } else {
            // === 未登入 ===
            btnGoLogin.visibility = View.VISIBLE
            btnLogout.visibility = View.GONE

            tvUsername.text = "尚未登入"
            tvEmail.text = "點擊此處進行登入以同步資料"
            ivProfile.setImageResource(R.drawable.ic_feature) // 回復預設圖示
        }
    }
}