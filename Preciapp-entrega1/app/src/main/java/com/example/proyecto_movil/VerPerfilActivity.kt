package com.example.proyecto_movil

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

class VerPerfilActivity : AppCompatActivity() {

    // Estado
    private var enModoEdicion = false

    // Vistas
    private lateinit var btnBack: ImageButton
    private lateinit var btnToggle: MaterialButton
    private lateinit var imgAvatar: ImageView

    // Lectura
    private lateinit var tvNombreLabel: TextView
    private lateinit var tvNombre: TextView
    private lateinit var tvUsuarioLabel: TextView
    private lateinit var tvUsuario: TextView

    // Edición
    private lateinit var etNombre: TextInputEditText
    private lateinit var etUsuario: TextInputEditText

    // Grupos (para alternar)
    private lateinit var groupRead: View
    private lateinit var groupEdit: View

    // Firebase
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val profileRef by lazy { FirebaseRefs.profileRef() }
    private val storage by lazy { FirebaseStorage.getInstance().reference }
    private var listener: ValueEventListener? = null

    // Control username único
    private var usernameActualEnDB: String = ""

    // Para borrar la foto anterior si subimos una nueva
    private var fotoActualUrl: String? = null

    // Photo picker
    private var nuevaFotoUri: Uri? = null
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            nuevaFotoUri = uri
            subirFotoDePerfil(uri)
        } else {
            Toast.makeText(this, "No seleccionaste imagen", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ver_perfil)

        // Referencias
        btnBack   = findViewById(R.id.btnBack)
        btnToggle = findViewById(R.id.btnToggle)
        imgAvatar = findViewById(R.id.imgAvatar)

        tvNombreLabel  = findViewById(R.id.tvNombreLabel)
        tvNombre       = findViewById(R.id.tvNombre)
        tvUsuarioLabel = findViewById(R.id.tvUsuarioLabel)
        tvUsuario      = findViewById(R.id.tvUsuario)

        etNombre  = findViewById(R.id.etNombre)
        etUsuario = findViewById(R.id.etUsuario)

        groupRead = findViewById(R.id.groupRead)
        groupEdit = findViewById(R.id.groupEdit)

        // Botón atrás
        btnBack.setOnClickListener { finish() }

        // Alterna Editar / Hecho
        btnToggle.setOnClickListener {
            if (enModoEdicion) guardarCambios()  // si estaba editando, guarda
            aplicarModoEdicion(!enModoEdicion)
        }

        // Tocar avatar → seleccionar y subir foto
        imgAvatar.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        aplicarModoEdicion(false) // iniciar en modo lectura
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser == null) {
            Toast.makeText(this, "Debes iniciar sesión", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        // Listener en vivo al perfil (muestra nombre/usuario/foto)
        listener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val nombre  = snap.child("displayName").getValue(String::class.java).orEmpty()
                val usuario = snap.child("username").getValue(String::class.java).orEmpty()
                val foto    = snap.child("photoUrl").getValue(String::class.java).orEmpty()

                tvNombre.text  = if (nombre.isBlank()) "Sin nombre" else nombre
                tvUsuario.text = if (usuario.isBlank()) "—" else usuario
                usernameActualEnDB = usuario

                // precargar campos de edición
                etNombre.setText(nombre)
                etUsuario.setText(usuario)

                fotoActualUrl = foto
                if (foto.isNotBlank()) {
                    imgAvatar.load(foto) {
                        crossfade(true)
                        transformations(CircleCropTransformation())
                        placeholder(R.drawable.avatar)
                        error(R.drawable.avatar)
                    }
                } else {
                    imgAvatar.setImageResource(R.drawable.avatar)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@VerPerfilActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
        profileRef.addValueEventListener(listener!!)
    }

    override fun onStop() {
        super.onStop()
        listener?.let { profileRef.removeEventListener(it) }
    }

    /** Normaliza username: minúsculas, sin espacios (reemplaza por "_") */
    private fun normUsername(u: String) =
        u.trim().lowercase().replace("\\s+".toRegex(), "_")

    /**
     * Guarda cambios de nombre/usuario en RTDB y mantiene el índice `usernames`.
     */
    private fun guardarCambios() {
        val nombreNuevo   = etNombre.text?.toString()?.trim().orEmpty()
        val usernameInput = etUsuario.text?.toString()?.trim().orEmpty()
        val usuarioNuevo  = if (usernameInput.isBlank()) "" else normUsername(usernameInput)

        val uid   = FirebaseRefs.uid() ?: return
        val email = auth.currentUser?.email.orEmpty()
        val root  = FirebaseRefs.db().reference

        // Si no cambia el username, solo actualiza nombre
        if (usuarioNuevo.isBlank() || usuarioNuevo == usernameActualEnDB) {
            val updates = mutableMapOf<String, Any>()
            if (nombreNuevo.isNotBlank()) updates["displayName"] = nombreNuevo

            if (updates.isEmpty()) return
            profileRef.updateChildren(updates)
                .addOnSuccessListener { Toast.makeText(this, "Perfil actualizado", Toast.LENGTH_SHORT).show() }
                .addOnFailureListener { e -> Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show() }
            return
        }

        // Validar que el nuevo username no exista
        root.child("usernames").child(usuarioNuevo).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    Toast.makeText(this, "Ese nombre de usuario ya está ocupado", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Actualizaciones atómicas: perfil + índice nuevo, y borrar índice viejo si existía
                val updates = hashMapOf<String, Any?>(
                    "users/$uid/profile/displayName" to (if (nombreNuevo.isNotBlank()) nombreNuevo else tvNombre.text.toString()),
                    "users/$uid/profile/username"   to usuarioNuevo,
                    "usernames/$usuarioNuevo"       to mapOf("uid" to uid, "email" to email)
                )
                if (usernameActualEnDB.isNotBlank()) {
                    updates["usernames/$usernameActualEnDB"] = null
                }

                root.updateChildren(updates)
                    .addOnSuccessListener {
                        usernameActualEnDB = usuarioNuevo
                        Toast.makeText(this, "Perfil actualizado", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error verificando usuario: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    /** Sube la foto a Storage y guarda la URL en RTDB (borrando la anterior si existía) */
    private fun subirFotoDePerfil(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return

        // nombre único por timestamp para evitar cache y permitir borrar la anterior
        val fileName = "avatar_${System.currentTimeMillis()}.jpg"
        val fileRef  = storage.child("users/$uid/$fileName")

        // 1) Subir y luego pedir downloadUrl
        fileRef.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: Exception("Error al subir")
                fileRef.downloadUrl
            }
            .addOnSuccessListener { url ->
                val nuevaUrl = url.toString()

                // 2) Borrar foto anterior si era una URL válida de Storage
                val vieja = fotoActualUrl
                if (!vieja.isNullOrBlank() && vieja.startsWith("https://")) {
                    try {
                        FirebaseStorage.getInstance().getReferenceFromUrl(vieja)
                            .delete()
                            .addOnFailureListener { /* ignorar si ya no existe */ }
                    } catch (_: Exception) { /* si la URL no es de Storage, ignoramos */ }
                }

                // 3) Guardar la nueva URL en RTDB
                profileRef.child("photoUrl").setValue(nuevaUrl)
                    .addOnSuccessListener {
                        fotoActualUrl = nuevaUrl
                        imgAvatar.load(nuevaUrl) {
                            crossfade(true)
                            transformations(CircleCropTransformation())
                            placeholder(R.drawable.avatar)
                            error(R.drawable.avatar)
                        }
                        Toast.makeText(this, "Foto actualizada", Toast.LENGTH_SHORT).show()

                        // 4) Devolver la URL a Principal para refrescar de inmediato
                        setResult(RESULT_OK, Intent().putExtra("updatedPhotoUrl", nuevaUrl))
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "No pude guardar la URL: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Falló la subida: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    /** Cambia entre modo lectura y edición. */
    private fun aplicarModoEdicion(activar: Boolean) {
        enModoEdicion = activar
        groupRead.visibility = if (activar) View.GONE else View.VISIBLE
        groupEdit.visibility = if (activar) View.VISIBLE else View.GONE
        btnToggle.text = getString(if (activar) R.string.action_done else R.string.action_edit)
    }
}
