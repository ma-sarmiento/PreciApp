package com.example.proyecto_movil

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage

class EditarCuentaActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("prefs_cuenta", MODE_PRIVATE) }
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_cuenta)

        // Idioma para correos/páginas de Firebase (o usa setLanguageCode("es") si quieres forzarlo)
        auth.useAppLanguage()

        // Toolbar con back
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Vistas
        val tvCorreoActual = findViewById<TextView>(R.id.tvCorreoActual)
        val swSonidos     = findViewById<SwitchMaterial>(R.id.swSonidos)
        val rowCorreo     = findViewById<LinearLayout>(R.id.rowCorreo)
        val rowPassword   = findViewById<LinearLayout>(R.id.rowPassword)
        val rowDesactivar = findViewById<LinearLayout>(R.id.rowDesactivar)
        val rowEliminar   = findViewById<LinearLayout>(R.id.rowEliminar)

        // Mostrar correo real (se refrescará en onStart con reload())
        tvCorreoActual.text = auth.currentUser?.email ?: getString(R.string.correo_demo)

        // Sonidos (demo)
        swSonidos.isChecked = prefs.getBoolean("sonidos_app", true)
        swSonidos.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("sonidos_app", checked).apply()
        }

        // ===== CAMBIAR CORREO (con reautenticación) =====
        rowCorreo.setOnClickListener {
            val user = auth.currentUser
            if (user == null) {
                infoDialog("Inicia sesión", "Debes iniciar sesión para cambiar tu correo.")
                return@setOnClickListener
            }
            pedirNuevoCorreo { nuevoCorreo ->
                pedirPasswordActual { password ->
                    val emailActual = user.email
                    auth.setLanguageCode("es")
                    if (emailActual.isNullOrBlank()) {
                        infoDialog("Sin correo", "Tu sesión no tiene un correo asociado.")
                        return@pedirPasswordActual
                    }
                    val cred = EmailAuthProvider.getCredential(emailActual, password)
                    user.reauthenticate(cred)
                        .addOnSuccessListener {
                            user.verifyBeforeUpdateEmail(nuevoCorreo)
                                .addOnSuccessListener {
                                    infoDialog(
                                        "Verifica tu nuevo correo",
                                        "Te enviamos un enlace a:\n\n$nuevoCorreo\n\n" +
                                                "Ábrelo y confirma para aplicar el cambio."
                                    )
                                }
                                .addOnFailureListener { e ->
                                    infoDialog("No pudimos actualizar", e.localizedMessage ?: "Error")
                                }
                        }
                        .addOnFailureListener { e ->
                            infoDialog(
                                "No pudimos verificar tu identidad",
                                "Revisa tu contraseña e inténtalo de nuevo.\n\nDetalle: ${e.localizedMessage}"
                            )
                        }
                }
            }
        }

        // ===== CAMBIAR CONTRASEÑA (email de reseteo) =====
        rowPassword.setOnClickListener {
            val email = auth.currentUser?.email
            auth.setLanguageCode("es")
            if (email.isNullOrBlank()) {
                infoDialog("Sin correo", "No hay un correo asociado a la sesión actual.")
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    infoDialog(
                        "Revisa tu correo",
                        "Te enviamos un enlace a:\n\n$email\n\nÁbrelo y define una nueva contraseña."
                    )
                }
                .addOnFailureListener { e ->
                    infoDialog("No pudimos enviar el correo", e.localizedMessage ?: "Error")
                }
        }

        // ===== DESACTIVAR CUENTA (soft delete) =====
        rowDesactivar.setOnClickListener {
            val user = auth.currentUser ?: return@setOnClickListener toast("Debes iniciar sesión")
            confirmar(
                "Desactivar cuenta",
                "Tu perfil quedará inactivo y cerraremos la sesión. " +
                        "Podrás reactivarlo iniciando sesión y aceptando reactivar."
            ) {
                pedirPasswordActual { pass ->
                    val cred = EmailAuthProvider.getCredential(user.email ?: "", pass)
                    user.reauthenticate(cred)
                        .addOnSuccessListener { desactivarCuenta() }
                        .addOnFailureListener { e ->
                            infoDialog("No pudimos verificar tu identidad", e.localizedMessage ?: "Error")
                        }
                }
            }
        }

        // ===== ELIMINAR CUENTA (hard delete) =====
        rowEliminar.setOnClickListener {
            val user = auth.currentUser ?: return@setOnClickListener toast("Debes iniciar sesión")
            confirmar(
                "Eliminar cuenta",
                "Esta acción es permanente. Se borrarán tus datos y tu cuenta de PresiApp."
            ) {
                pedirPasswordActual { pass ->
                    val cred = EmailAuthProvider.getCredential(user.email ?: "", pass)
                    user.reauthenticate(cred)
                        .addOnSuccessListener { eliminarCuentaDefinitiva() }
                        .addOnFailureListener { e ->
                            infoDialog("No pudimos verificar tu identidad", e.localizedMessage ?: "Error")
                        }
                }
            }
        }
    }

    // 🔄 Recargar usuario y sincronizar correo al entrar a la pantalla
    override fun onStart() {
        super.onStart()
        actualizarCorreoUI()
    }

    private fun actualizarCorreoUI() {
        auth.currentUser?.reload()
            ?.addOnCompleteListener {
                val emailActual = auth.currentUser?.email
                findViewById<TextView>(R.id.tvCorreoActual).text =
                    emailActual ?: getString(R.string.correo_demo)

                // (Opcional recomendado) sincroniza el correo en tu perfil RTDB
                emailActual?.let { correo ->
                    FirebaseRefs.profileRef().child("email").setValue(correo)
                }
            }
    }

    // ---------- Helpers de UI ----------
    private fun pedirNuevoCorreo(onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = "nuevo@correo.com"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setPadding(48, 32, 48, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("Cambiar correo")
            .setMessage("Te enviaremos un enlace al nuevo correo para confirmar el cambio.")
            .setView(input)
            .setPositiveButton("Enviar") { _, _ ->
                val nuevo = input.text.toString().trim()
                when {
                    nuevo.isEmpty() -> toast("Escribe un correo")
                    !Patterns.EMAIL_ADDRESS.matcher(nuevo).matches() -> toast("Correo inválido")
                    else -> onOk(nuevo)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun pedirPasswordActual(onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = "Contraseña actual"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 32, 48, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("Verificar identidad")
            .setMessage("Por seguridad, ingresa tu contraseña actual.")
            .setView(input)
            .setPositiveButton("Confirmar") { _, _ ->
                val pass = input.text.toString()
                if (pass.isBlank()) toast("Escribe tu contraseña") else onOk(pass)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmar(titulo: String, mensaje: String, onYes: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton("Aceptar") { _, _ -> onYes() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun infoDialog(title: String, msg: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------- Acciones de cuenta ----------
    // Soft delete: marca disabled=true y cierra sesión
    private fun desactivarCuenta() {
        val uid = auth.currentUser?.uid ?: return
        FirebaseRefs.db().reference.child("users/$uid/profile/disabled")
            .setValue(true)
            .addOnSuccessListener {
                infoDialog("Cuenta desactivada", "Cerramos tu sesión. Puedes reactivarla al iniciar sesión de nuevo.")
                auth.signOut()
                irALogin()
            }
            .addOnFailureListener { e ->
                infoDialog("No pudimos desactivar tu cuenta", e.localizedMessage ?: "Error")
            }
    }

    // Hard delete: borra Storage + RTDB + índice username + Auth
    private fun eliminarCuentaDefinitiva() {
        val user = auth.currentUser ?: return
        val uid  = user.uid
        val db   = FirebaseRefs.db().reference
        val storage = FirebaseStorage.getInstance().reference

        // Leer username para borrar índice
        db.child("users/$uid/profile").get()
            .addOnSuccessListener { snap ->
                val username = snap.child("username").getValue(String::class.java).orEmpty()

                // 1) Borrar Storage carpeta del usuario
                val userFolder = storage.child("users/$uid")
                userFolder.listAll()
                    .addOnSuccessListener { list ->
                        val deletions = list.items.map { it.delete() }
                        // 2) Borrar RTDB + índice
                        val updates = hashMapOf<String, Any?>("users/$uid" to null)
                        if (username.isNotBlank()) updates["usernames/$username"] = null

                        db.updateChildren(updates)
                            .addOnSuccessListener {
                                // 3) Borrar usuario de Auth
                                user.delete()
                                    .addOnSuccessListener {
                                        infoDialog("Cuenta eliminada", "Borramos tu cuenta y tus datos.")
                                        irALogin()
                                    }
                                    .addOnFailureListener { e ->
                                        infoDialog("No pudimos borrar la cuenta de Auth", e.localizedMessage ?: "Error")
                                    }
                            }
                            .addOnFailureListener { e ->
                                infoDialog("No pudimos borrar tus datos", e.localizedMessage ?: "Error")
                            }
                    }
                    .addOnFailureListener {
                        // Si no hay carpeta, igual borra RTDB/Auth
                        val updates = hashMapOf<String, Any?>("users/$uid" to null)
                        db.updateChildren(updates).addOnCompleteListener {
                            user.delete()
                                .addOnSuccessListener {
                                    infoDialog("Cuenta eliminada", "Borramos tu cuenta y tus datos.")
                                    irALogin()
                                }
                                .addOnFailureListener { e ->
                                    infoDialog("No pudimos borrar la cuenta de Auth", e.localizedMessage ?: "Error")
                                }
                        }
                    }
            }
            .addOnFailureListener { e ->
                infoDialog("No pudimos leer tu perfil", e.localizedMessage ?: "Error")
            }
    }

    private fun irALogin() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
