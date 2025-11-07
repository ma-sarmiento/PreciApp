package com.example.proyecto_movil

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.example.proyecto_movil.databinding.ActivityNotificacionesBinding
import com.google.android.material.snackbar.Snackbar

class NotificacionesActivity : AppCompatActivity() {

    private lateinit var b: ActivityNotificacionesBinding
    private val prefs by lazy { getSharedPreferences("prefs_notif", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityNotificacionesBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Toolbar con back
        setSupportActionBar(b.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.topAppBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // ---- Estado inicial (solo switch maestro) ----
        val master = prefs.getBoolean("master", true)
        b.swMaster.isChecked = master

        // Guardar cambios al togglear
        b.swMaster.setOnCheckedChangeListener { _, isChecked ->
            save("master", isChecked)
        }

        // Botón Guardar (feedback visual)
        b.btnGuardar.setOnClickListener {
            Snackbar.make(b.root, getString(R.string.saved_ok), Snackbar.LENGTH_SHORT).show()
        }

        // Abrir ajustes del sistema de notificaciones para esta app
        b.btnSistema.setOnClickListener {
            val intent = Intent().apply {
                action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS
                } else {
                    "android.settings.APP_NOTIFICATION_SETTINGS"
                }
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
        }
    }

    private fun save(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
}

