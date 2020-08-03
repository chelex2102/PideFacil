package com.pidefacil

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val GOOGLE_SIGN_IN = 100
    }

    private var foto: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar!!.hide()




        //Analytics event
        val analytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(this)
        val bundle = Bundle()
        bundle.putString("message", "Integración de Firebase completa")
        analytics.logEvent("InitScreen", bundle)


        setup()

        session()


    }

    override fun onStart() {
        super.onStart()

        authLayout.visibility = View.VISIBLE
    }

    private fun session() {
        val prefs = getSharedPreferences(getString(R.string.prefs_file), Context.MODE_PRIVATE)
        val user = prefs.getString("user", null)
        val email = prefs.getString("email", null)
        val userId = prefs.getString("userId", null)
        val fotoperf = prefs.getString("foto", null)
//
        if(email !=null && user !=null && userId != null && fotoperf != null) {
            authLayout.visibility = View.INVISIBLE
            showInit(email, user, userId, fotoperf)
        }
    }

    private fun showInit(email: String, user: String, userId: String, foto: String) {
        val homeIntent = Intent(this, HomeActivity::class.java).apply {
            putExtra("email", email)
            putExtra("user", user)
            putExtra("userId", userId)
            putExtra("foto", foto)
            FirebaseMessaging.getInstance().isAutoInitEnabled = true

            FirebaseInstanceId.getInstance().instanceId
                .addOnCompleteListener(OnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        return@OnCompleteListener
                    }
                    val token = task.result?.token
                    val prefs = getSharedPreferences(
                        getString(R.string.prefs_file),
                        Context.MODE_PRIVATE
                    ).edit()
                    prefs.putString("email", email)
                    prefs.putString("user", user)
                    prefs.putString("userId", userId)
                    prefs.putString("token", token)
                    prefs.putString("foto", foto)
                    prefs.apply()

                    val map = mutableMapOf<String, Any>()

                    map["user"] = user
                    map["email"] = email
                    map["token"] = token!!
                    map["profilePick"] = foto


                    val refmes = FirebaseMessaging.getInstance().subscribeToTopic("Usuarios")
                    refmes.addOnCompleteListener { }


                    val ref = FirebaseFirestore.getInstance().collection("Usuarios")
                    ref.document(userId).set(map)
                })
        }
        startActivity(homeIntent)

    }

    private fun setup() {
        googleButton.setOnClickListener {
            val googleConf = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()

            val googleClient = GoogleSignIn.getClient(this, googleConf)
            startActivityForResult(googleClient.signInIntent, GOOGLE_SIGN_IN)
            googleClient.signOut()

            startActivityForResult(googleClient.signInIntent, GOOGLE_SIGN_IN)
        }

        FirebaseInstanceId.getInstance().instanceId
            .addOnCompleteListener { task ->
                if(!task.isSuccessful) {
                    Log.w(this.toString(), "getInstanceID failed", task.exception)
                }
                val token = task.result?.token

                val msg = getString(R.string.fui_email_link_confirm_email_message, token)
                Log.d(this.toString(), msg)

            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GOOGLE_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account!!.idToken, null)

                FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnCompleteListener{
                        if (it.isSuccessful) {
                            val downloadPerfilfoto = account.photoUrl
                            foto = downloadPerfilfoto.toString()

                            showInit(account.email ?: "", account.displayName?:"", account.id?:"",
                                foto!!)
                        }else{
                            showAlert()
                        }
                    }

            } catch (e: ApiException) {
                Toast.makeText(this, "Obteniendo su información", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun showAlert() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Error")
        builder.setMessage("Se produjo un error autenticando al usuario")
        builder.setPositiveButton("Aceptar", null)
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }
}