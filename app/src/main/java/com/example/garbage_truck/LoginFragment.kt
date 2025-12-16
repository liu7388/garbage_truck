package com.example.garbage_truck

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.garbage_truck.databinding.FragmentLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                Log.d("LoginFragment", "Google SignIn successful, got idToken.")
                account?.idToken?.let { firebaseAuthWithGoogle(it) }
            } catch (e: ApiException) {
                Log.e("LoginFragment", "Google sign in failed", e)
                Toast.makeText(context, "Google 登入失敗: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnLoginEmail.setOnClickListener { loginWithEmail() }
        binding.btnRegister.setOnClickListener {
            val action = LoginFragmentDirections.actionLoginFragmentToRegisterFragment()
            findNavController().navigate(action)
        }
        binding.btnLoginGoogle.setOnClickListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
        binding.btnLoginGithub.setOnClickListener { loginWithGithub() }
        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun onLoginSuccess() {
        Toast.makeText(context, "登入成功", Toast.LENGTH_SHORT).show()
        findNavController().popBackStack()
    }

    private fun loginWithEmail() {
        val email = binding.etEmail.text.toString()
        val password = binding.etPassword.text.toString()

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

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { onLoginSuccess() }
            .addOnFailureListener { e ->
                Log.e("LoginFragment", "Firebase Google auth failed", e)
                Toast.makeText(context, "Google 驗證失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loginWithGithub() {
        val provider = OAuthProvider.newBuilder("github.com")
        auth.startActivityForSignInWithProvider(requireActivity(), provider.build())
            .addOnSuccessListener { onLoginSuccess() }
            .addOnFailureListener { e ->
                Toast.makeText(context, "GitHub 登入失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}