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
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        // 🔆 Forzar modo claro en toda la app
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)  // layout de login

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
                    // 🔍 Verificar si la cuenta está desactivada
                    val uid = auth.currentUser!!.uid
                    FirebaseRefs.db().reference.child("users/$uid/profile/disabled")
                        .get()
                        .addOnSuccessListener { snap ->
                            val disabled = snap.getValue(Boolean::class.java) == true
                            if (disabled) {
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
            auth.setLanguageCode("es")
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener { toast("Te enviamos un correo para restablecer la contraseña") }
                .addOnFailureListener { e -> toast("No pude enviar el correo: ${e.localizedMessage}") }
        }

        // ---- Ir a registro ----
        textViewRegistrarme.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    // 🔁 Si ya está logueado y la cuenta no está desactivada, entrar directo
    override fun onStart() {
        super.onStart()
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid
        FirebaseRefs.db().reference.child("users/$uid/profile/disabled")
            .get()
            .addOnSuccessListener { snap ->
                val disabled = snap.getValue(Boolean::class.java) == true
                if (!disabled) goHome()
            }
            .addOnFailureListener {
                // si falla la lectura, no forzamos navegación
            }
    }

    /** ▶️ Pantalla principal de la app */
    private fun goHome() {
        // Si tu pantalla principal se llama diferente, cámbiala aquí:
        // Intent(this, CameraListActivity::class.java)
        startActivity(Intent(this, Principal::class.java))
        finish()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
