package com.example.garbage_truck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class RegisterFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etPasswordConfirm: EditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_register, container, false)

        auth = FirebaseAuth.getInstance()

        etName = view.findViewById(R.id.et_name_register)
        etEmail = view.findViewById(R.id.et_email_register)
        etPassword = view.findViewById(R.id.et_password_register)
        etPasswordConfirm = view.findViewById(R.id.et_password_confirm)

        view.findViewById<Button>(R.id.btn_submit_register).setOnClickListener { registerUser() }
        view.findViewById<Button>(R.id.btn_cancel_register).setOnClickListener {
            findNavController().popBackStack()
        }

        return view
    }

    private fun registerUser() {
        val name = etName.text.toString()
        val email = etEmail.text.toString()
        val password = etPassword.text.toString()
        val confirmPassword = etPasswordConfirm.text.toString()

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(context, "請填寫所有欄位", Toast.LENGTH_SHORT).show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(context, "密碼不一致", Toast.LENGTH_SHORT).show()
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()

                user?.updateProfile(profileUpdates)?.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(context, "註冊成功", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack(R.id.settingsFragment, false)
                    } else {
                        Toast.makeText(context, "個人資料更新失敗: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "註冊失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}