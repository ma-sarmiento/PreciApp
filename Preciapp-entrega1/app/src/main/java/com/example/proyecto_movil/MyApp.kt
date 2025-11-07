package com.example.proyecto_movil

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.google.android.libraries.places.api.Places
import com.google.firebase.database.FirebaseDatabase

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // --- Firebase Realtime Database: persistencia local (offline cache) ---
        val db = FirebaseDatabase.getInstance("https://preciapp-6b298-default-rtdb.firebaseio.com/")
        db.setPersistenceEnabled(true)
        db.reference.child("catalog").keepSynced(true)

        // --- Google Places: inicializa usando la misma key del Manifest ---
        if (!Places.isInitialized()) {
            try {
                val ai: ApplicationInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                val metaData = ai.metaData
                val apiKey = metaData?.getString("com.google.android.geo.API_KEY")

                if (!apiKey.isNullOrEmpty()) {
                    Places.initialize(this, apiKey)
                } else {
                    android.util.Log.e("MyApp", "⚠️ API key de Google Maps no encontrada en meta-data.")
                }
            } catch (e: Exception) {
                android.util.Log.e("MyApp", "Error leyendo API key del Manifest: ${e.message}")
            }
        }
    }
}
