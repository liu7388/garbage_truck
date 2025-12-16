package com.example.garbage_truck

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider

class LoginFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText

    // Google Sign In Launcher
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == -1) { // -1 代表 RESULT_OK
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.idToken?.let { firebaseAuthWithGoogle(it) }
            } catch (e: ApiException) {
                Toast.makeText(context, "Google 登入失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
    }

    override fun onStart() {
        super.onStart()
        val pendingResultTask = auth.pendingAuthResult
        if (pendingResultTask != null) {
            pendingResultTask
                .addOnSuccessListener { authResult ->
                    Log.d("LOGIN", "User signed in: ${authResult.user?.email}")
                    onLoginSuccess()
                }
                .addOnFailureListener { e ->
                    Log.e("LOGIN", "Error: ${e.message}")
                }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_login, container, false)

        etEmail = view.findViewById(R.id.et_email)
        etPassword = view.findViewById(R.id.et_password)

        // 初始化 Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // 自動生成的 ID
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        // 按鈕監聽
        view.findViewById<Button>(R.id.btn_login_email).setOnClickListener { loginWithEmail() }
        view.findViewById<Button>(R.id.btn_register).setOnClickListener {
            val action = LoginFragmentDirections.actionLoginFragmentToRegisterFragment()
            findNavController().navigate(action)
        }
        view.findViewById<Button>(R.id.btn_login_google).setOnClickListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
        view.findViewById<Button>(R.id.btn_login_github).setOnClickListener { loginWithGithub() }

        // 取消按鈕
        view.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            findNavController().popBackStack()
        }

        return view
    }

    private fun onLoginSuccess() {
        Toast.makeText(context, "登入成功", Toast.LENGTH_SHORT).show()
        findNavController().popBackStack() // 關閉此頁面，回到 SettingFragment
    }

    // --- Email 登入 ---
    private fun loginWithEmail() {
        val email = etEmail.text.toString()
        val password = etPassword.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(context, "請輸入 Email 和密碼", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { onLoginSuccess() }
            .addOnFailureListener { e ->
                Toast.makeText(context, "登入失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- Google 登入驗證 ---
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { onLoginSuccess() }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Google 驗證失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- GitHub 登入 ---
    private fun loginWithGithub() {
        val provider = OAuthProvider.newBuilder("github.com")

        auth.startActivityForSignInWithProvider(requireActivity(), provider.build())
            .addOnSuccessListener { onLoginSuccess() }
            .addOnFailureListener { e ->
                Toast.makeText(context, "GitHub 登入失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}