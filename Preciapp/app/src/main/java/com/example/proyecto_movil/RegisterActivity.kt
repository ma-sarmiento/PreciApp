package com.example.proyecto_movil

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {

    private val DB_URL = "https://preciapp-6b298-default-rtdb.firebaseio.com/"

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        val editTextNombre: EditText = findViewById(R.id.editTextNombre)
        val editTextCorreo: EditText = findViewById(R.id.editTextCorreo)           // ← email
        val editTextUsuario: EditText = findViewById(R.id.editTextUsuario)         // ← username (opcional)
        val editTextContrasena: EditText = findViewById(R.id.editTextContraseña)
        val buttonRegistrarme: Button = findViewById(R.id.buttonRegistrarme)
        val textViewVolverLogin: TextView = findViewById(R.id.textViewVolverLogin)

        buttonRegistrarme.setOnClickListener {
            val nombre   = editTextNombre.text.toString().trim()
            val correo   = editTextCorreo.text.toString().trim()
            val usuario  = editTextUsuario.text.toString().trim()   // alias/nickname opcional
            val pass     = editTextContrasena.text.toString().trim()

            if (nombre.isEmpty() || correo.isEmpty() || pass.isEmpty()) {
                toast("Por favor, completa nombre, correo y contraseña"); return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                toast("Correo inválido"); return@setOnClickListener
            }
            if (pass.length < 6) {
                toast("La contraseña debe tener al menos 6 caracteres"); return@setOnClickListener
            }

            // 1) Crear usuario en Auth
            auth.createUserWithEmailAndPassword(correo, pass)
                .addOnSuccessListener {
                    val uid = auth.currentUser!!.uid

                    // 2) Guardar perfil básico en Realtime Database
                    val perfil = mapOf(
                        "displayName" to nombre,
                        "username" to usuario,
                        "email" to correo,
                        "photoUrl" to ""          // se llenará luego en EditarCuentaActivity
                    )
                    val ref = FirebaseDatabase.getInstance(DB_URL).reference
                    ref.child("users").child(uid).child("profile").setValue(perfil)
                        .addOnSuccessListener {
                            toast("Cuenta creada. ¡Bienvenido $nombre!")
                            goHome()
                        }
                        .addOnFailureListener { e ->
                            toast("Usuario creado, pero no pude guardar el perfil: ${e.localizedMessage}")
                            goHome()
                        }
                }
                .addOnFailureListener { e ->
                    toast("No pude crear tu cuenta: ${e.localizedMessage}")
                }
        }

        textViewVolverLogin.setOnClickListener { finish() }
    }

    private fun goHome() {
        startActivity(Intent(this, Principal::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

