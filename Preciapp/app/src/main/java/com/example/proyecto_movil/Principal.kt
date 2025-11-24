package com.example.proyecto_movil

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class Principal : AppCompatActivity() {

    private lateinit var avatar: ImageView

    // Firebase
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val profileRef by lazy { FirebaseRefs.profileRef() } // users/{uid}/profile
    private var profileListener: ValueEventListener? = null

    // Launcher para recibir resultado desde VerPerfilActivity (nueva URL de foto)
    private val editProfileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val url = result.data?.getStringExtra("updatedPhotoUrl")
            if (!url.isNullOrBlank()) {
                avatar.load(url) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                    placeholder(R.drawable.avatar)
                    error(R.drawable.avatar)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_principal)

        // --- Botones principales ---
        findViewById<MaterialButton>(R.id.buttonEscanearProducto).setOnClickListener {
            startActivity(Intent(this, CameraListActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.buttonMiLista).setOnClickListener {
            startActivity(Intent(this, ListaProductosActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.buttonDefinirPresupuesto).setOnClickListener {
            startActivity(Intent(this, DefinirPresupuestoActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.buttonVerRutaEficiente).setOnClickListener {
            startActivity(Intent(this, RutaEficienteActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.buttonCompararPrecios).setOnClickListener {
            startActivity(Intent(this, CompararPreciosActivity::class.java))
        }

        // ⭐ **Botón IA (AGREGADO)**
        findViewById<MaterialButton>(R.id.buttonIA).setOnClickListener {
            startActivity(Intent(this, IAActivity::class.java))
        }

        // --- Avatar (miniatura + menú) ---
        avatar = findViewById(R.id.avatarIcon)
        avatar.isClickable = true
        avatar.isFocusable = true
        avatar.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.menu_avatar, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_ver_perfil -> {
                        editProfileLauncher.launch(Intent(this, VerPerfilActivity::class.java))
                        true
                    }
                    R.id.menu_notificaciones -> {
                        startActivity(Intent(this, NotificacionesActivity::class.java))
                        true
                    }
                    R.id.menu_config_cuenta -> {
                        startActivity(Intent(this, EditarCuentaActivity::class.java))
                        true
                    }
                    R.id.action_logout -> {
                        auth.signOut()
                        getSharedPreferences("prefs_cuenta", MODE_PRIVATE)
                            .edit().clear().apply()

                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    override fun onStart() {
        super.onStart()

        if (auth.currentUser == null) {
            val i = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(i)
            finish()
            return
        }

        profileListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val url = snap.child("photoUrl").getValue(String::class.java)
                if (!url.isNullOrBlank()) {
                    avatar.load(url) {
                        crossfade(true)
                        transformations(CircleCropTransformation())
                        placeholder(R.drawable.avatar)
                        error(R.drawable.avatar)
                    }
                } else {
                    avatar.setImageResource(R.drawable.avatar)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        profileRef.addValueEventListener(profileListener!!)
    }

    override fun onStop() {
        super.onStop()
        profileListener?.let { profileRef.removeEventListener(it) }
    }
}
