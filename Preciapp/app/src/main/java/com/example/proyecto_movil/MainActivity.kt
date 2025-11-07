package com.example.proyecto_movil

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)  // tu layout de login

        auth = FirebaseAuth.getInstance()

        val editTextUsuario: EditText = findViewById(R.id.editTextUsuario)
        val editTextContrasena: EditText = findViewById(R.id.editTextContraseña)
        val buttonIniciarSesion: Button = findViewById(R.id.buttonIniciarSesion)
        val textViewOlvideContrasena: TextView = findViewById(R.id.textViewOlvideContrasena)
        val textViewRegistrarme: TextView = findViewById(R.id.textViewRegistrarme)

        // ---- Iniciar sesión ----
        buttonIniciarSesion.setOnClickListener {
            val email = editTextUsuario.text.toString().trim()
            val pass = editTextContrasena.text.toString().trim()

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                toast("Ingresa un correo válido"); return@setOnClickListener
            }
            if (pass.length < 6) {
                toast("La contraseña debe tener al menos 6 caracteres"); return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener {
                    // 🔍 Aquí revisamos si la cuenta está desactivada antes de entrar a la app
                    val uid = auth.currentUser!!.uid
                    FirebaseRefs.db().reference.child("users/$uid/profile/disabled")
                        .get()
                        .addOnSuccessListener { snap ->
                            val disabled = snap.getValue(Boolean::class.java) == true
                            if (disabled) {
                                // Mostrar diálogo para reactivar
                                AlertDialog.Builder(this)
                                    .setTitle("Cuenta desactivada")
                                    .setMessage("Tu cuenta está desactivada. ¿Deseas reactivarla ahora?")
                                    .setPositiveButton("Reactivar") { _, _ ->
                                        FirebaseRefs.db().reference
                                            .child("users/$uid/profile/disabled")
                                            .setValue(false)
                                            .addOnSuccessListener {
                                                toast("Cuenta reactivada correctamente")
                                                goHome()
                                            }
                                            .addOnFailureListener { e ->
                                                toast("Error al reactivar: ${e.localizedMessage}")
                                                auth.signOut()
                                            }
                                    }
                                    .setNegativeButton("Cancelar") { _, _ ->
                                        auth.signOut()
                                    }
                                    .setCancelable(false)
                                    .show()
                            } else {
                                // Si no está desactivada, entrar normal
                                goHome()
                            }
                        }
                        .addOnFailureListener { e ->
                            toast("Error al leer estado: ${e.localizedMessage}")
                            auth.signOut()
                        }
                }
                .addOnFailureListener { e ->
                    toast("No pudimos iniciar sesión: ${e.localizedMessage}")
                }
        }

        // ---- Recuperar contraseña ----
        textViewOlvideContrasena.setOnClickListener {
            val email = editTextUsuario.text.toString().trim()
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                toast("Escribe tu correo para enviarte el enlace")
                return@setOnClickListener
            }
            auth.setLanguageCode("es") // correo en español
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    toast("Te enviamos un correo para restablecer la contraseña")
                }
                .addOnFailureListener { e ->
                    toast("No pude enviar el correo: ${e.localizedMessage}")
                }
        }

        // ---- Ir a registro ----
        textViewRegistrarme.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        // Si ya hay sesión iniciada, entra directo
        if (FirebaseAuth.getInstance().currentUser != null) {
            goHome()
        }
    }

    // ---- Ir a la pantalla principal ----
    private fun goHome() {
        startActivity(
            Intent(this, Principal::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    // ---- Helper para mensajes ----
    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
