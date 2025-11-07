package com.example.proyecto_movil

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.JsonParser
import com.google.maps.android.PolyUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import kotlin.math.*

class MapaRutaActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private var gmap: GoogleMap? = null
    private lateinit var fused: FusedLocationProviderClient
    private val http by lazy { OkHttpClient() }

    private lateinit var progress: ProgressBar
    private lateinit var tvInfo: TextView
    private lateinit var tvResumen: TextView
    private lateinit var btnRecalcular: MaterialButton
    private lateinit var btnAbrirMaps: MaterialButton
    private lateinit var fabRecenter: FloatingActionButton
    private lateinit var fabMapType: FloatingActionButton

    data class Stop(
        val title: String,
        val subtitle: String,
        val lat: Double?,
        val lng: Double?,
        val searchQuery: String?,
        val productsCount: Int
    )

    private var stops: List<Stop> = emptyList()
    private var myLocation: Location? = null
    private var lastPoints: List<LatLng> = emptyList()

    private val reqLocPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val ok = res[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) { enableMyLocation(); fetchLastLocationThenResolve() }
        else { resolveMissingCoordinates(null) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mapa_ruta)

        findViewById<MaterialToolbar>(R.id.toolbarMapa).apply {
            navigationIcon = AppCompatResources.getDrawable(this@MapaRutaActivity, R.drawable.ic_arrow_back)
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() } // ← back seguro
        }

        fused = LocationServices.getFusedLocationProviderClient(this)
        progress = findViewById(R.id.progress)
        tvInfo = findViewById(R.id.tvInfo)
        tvResumen = findViewById(R.id.tvResumen)
        btnRecalcular = findViewById(R.id.btnRecalcular)
        btnAbrirMaps = findViewById(R.id.btnAbrirMaps)
        fabRecenter = findViewById(R.id.fabRecenter)
        fabMapType = findViewById(R.id.fabMapType)

        // Recibir bundles compat (API 33+)
        val bundles: ArrayList<Bundle> =
            if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableArrayListExtra("stops_bundles", Bundle::class.java) ?: arrayListOf()
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra("stops_bundles") ?: arrayListOf()
            }

        stops = bundles.map { b ->
            Stop(
                title = b.getString("title").orEmpty(),
                subtitle = b.getString("subtitle").orEmpty(),
                lat = b.getDouble("lat").let { if (it.isNaN()) null else it },
                lng = b.getDouble("lng").let { if (it.isNaN()) null else it },
                searchQuery = b.getString("searchQuery"),
                productsCount = b.getInt("productsCount", 0)
            )
        }

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        btnRecalcular.setOnClickListener { fetchLastLocationThenResolve() }
        btnAbrirMaps.setOnClickListener { openInExternalMaps() }
        fabRecenter.setOnClickListener { recenterCamera() }
        fabMapType.setOnClickListener { toggleMapType() }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        gmap = googleMap
        gmap?.uiSettings?.isZoomControlsEnabled = true

        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation(); fetchLastLocationThenResolve()
        } else {
            reqLocPerms.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun enableMyLocation() { try { gmap?.isMyLocationEnabled = true } catch (_: SecurityException) {} }

    private fun fetchLastLocationThenResolve() {
        progress.visibility = View.VISIBLE
        tvInfo.text = "Obteniendo ubicación…"
        try {
            fused.lastLocation
                .addOnSuccessListener { loc -> myLocation = loc; resolveMissingCoordinates(loc) }
                .addOnFailureListener { resolveMissingCoordinates(null) }
        } catch (_: SecurityException) { resolveMissingCoordinates(null) }
    }

    /** Geocodifica sedes (si faltan lat/lng). Si nada se puede resolver, continúa igual. */
    private fun resolveMissingCoordinates(baseLoc: Location?) {
        progress.visibility = View.VISIBLE
        tvInfo.text = "Calculando paradas…"

        Thread {
            val geo = Geocoder(this, Locale.getDefault())
            val cl = baseLoc?.latitude
            val clg = baseLoc?.longitude

            fun box() = if (cl != null && clg != null) arrayOf(cl - 0.25, clg - 0.25, cl + 0.25, clg + 0.25) else null
            fun variants(q: String) = listOf(q, "$q tienda", "$q supermercado", "$q sede")

            fun tryResolve(name: String): Pair<Double, Double>? {
                box()?.let { (latMin, lngMin, latMax, lngMax) ->
                    try { geo.getFromLocationName(name, 3, latMin, lngMin, latMax, lngMax)?.firstOrNull()
                        ?.let { return it.latitude to it.longitude } } catch (_: Exception) {}
                }
                return try { geo.getFromLocationName(name, 3)?.firstOrNull()
                    ?.let { it.latitude to it.longitude } } catch (_: Exception) { null }
            }

            val resolved = stops.map { s ->
                if (s.lat != null && s.lng != null) s
                else {
                    val q = (s.searchQuery?.ifBlank { s.title } ?: s.title).trim()
                    val pair = variants(q).firstNotNullOfOrNull { tryResolve(it) }
                    if (pair != null) s.copy(lat = pair.first, lng = pair.second) else s
                }
            }

            runOnUiThread { stops = resolved; fetchRouteWithOSRM() }
        }.start()
    }

    /** Ruta real (sin API key) usando OSRM; si falla, fallback a línea. */
    private fun fetchRouteWithOSRM() {
        val map = gmap ?: return
        map.clear()

        val pts = mutableListOf<LatLng>()
        myLocation?.let { pts += LatLng(it.latitude, it.longitude) }
        stops.forEach { if (it.lat != null && it.lng != null) pts += LatLng(it.lat, it.lng) }

        // Marcadores
        var idx = 1
        myLocation?.let {
            map.addMarker(MarkerOptions()
                .position(LatLng(it.latitude, it.longitude))
                .title("Origen")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))
        }
        stops.forEach { s ->
            if (s.lat != null && s.lng != null) {
                map.addMarker(MarkerOptions()
                    .position(LatLng(s.lat, s.lng))
                    .title("${idx}. ${s.title}")
                    .snippet("${s.subtitle} · x${s.productsCount}")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))
                idx++
            }
        }

        if (pts.size < 2) {
            progress.visibility = View.GONE
            tvInfo.text = "Paradas: 0"
            Toast.makeText(this, "No hay ubicaciones con coordenadas aún.", Toast.LENGTH_LONG).show()
            return
        }

        val b = LatLngBounds.Builder()
        pts.forEach { b.include(it) }
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 80))

        progress.visibility = View.VISIBLE
        tvInfo.text = "Calculando ruta…"

        Thread {
            try {
                // OSRM espera lon,lat
                val coords = pts.joinToString(";") { "${it.longitude},${it.latitude}" }
                val url = "https://router.project-osrm.org/route/v1/driving/$coords?overview=full&geometries=polyline&alternatives=false&steps=false&annotations=distance,duration"

                val req = Request.Builder().url(url).get().build()
                val resp = http.newCall(req).execute()
                val body = resp.body?.string().orEmpty()
                resp.close()

                val root = JsonParser.parseString(body).asJsonObject
                val code = root["code"]?.asString ?: "Error"
                if (code != "Ok") throw IllegalStateException(code)

                val route0 = root["routes"].asJsonArray[0].asJsonObject
                val encoded = route0["geometry"].asString
                val distanceMeters = route0["distance"].asDouble
                val durationSecs = route0["duration"].asDouble
                val decoded = PolyUtil.decode(encoded)

                runOnUiThread {
                    progress.visibility = View.GONE
                    lastPoints = decoded
                    map.addPolyline(PolylineOptions().addAll(decoded).width(10f))
                    val distKm = distanceMeters / 1000.0
                    val mins = (durationSecs / 60.0).roundToInt()
                    val nParadas = max(0, pts.size - (if (myLocation != null) 1 else 0))
                    tvInfo.text = "Paradas: $nParadas"
                    tvResumen.text = "Paradas: $nParadas · Distancia: ${"%.2f".format(distKm)} km · Tiempo: ~${mins} min"
                }
            } catch (e: Exception) {
                Log.e("OSRM", "falló: ${e.message}")
                runOnUiThread {
                    progress.visibility = View.GONE
                    Toast.makeText(this, "No se pudo obtener ruta (OSRM). Mostrando aproximación.", Toast.LENGTH_LONG).show()
                    drawStraightFallback(pts)
                }
            }
        }.start()
    }

    private fun drawStraightFallback(pts: List<LatLng>) {
        val map = gmap ?: return
        lastPoints = pts
        map.addPolyline(PolylineOptions().addAll(pts).width(8f))
        val distKm = totalDistanceKm(pts)
        val mins = ((distKm / 30.0) * 60).roundToInt()
        val nParadas = max(0, pts.size - (myLocation?.let { 1 } ?: 0))
        tvInfo.text = "Paradas: $nParadas"
        tvResumen.text = "Paradas: $nParadas · Distancia: ${"%.2f".format(distKm)} km · Tiempo: ~${mins} min"
    }

    // Utils
    private fun totalDistanceKm(pts: List<LatLng>): Double {
        if (pts.size < 2) return 0.0
        var sum = 0.0
        for (i in 0 until pts.size - 1) sum += haversineKm(pts[i], pts[i + 1])
        return sum
    }
    private fun haversineKm(a: LatLng, b: LatLng): Double {
        val R = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val s1 = sin(dLat / 2).pow(2.0)
        val s2 = cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2).pow(2.0)
        return 2 * R * asin(sqrt(s1 + s2))
    }
    private fun recenterCamera() {
        val map = gmap ?: return
        if (lastPoints.isEmpty()) return
        val b = LatLngBounds.Builder()
        lastPoints.forEach { b.include(it) }
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 80))
    }
    private fun toggleMapType() {
        val map = gmap ?: return
        map.mapType = when (map.mapType) {
            GoogleMap.MAP_TYPE_NORMAL -> GoogleMap.MAP_TYPE_HYBRID
            else -> GoogleMap.MAP_TYPE_NORMAL
        }
    }
    private fun openInExternalMaps() {
        if (lastPoints.isEmpty()) return
        val origin = lastPoints.first()
        val dest = lastPoints.last()
        val waypoints = lastPoints.drop(1).dropLast(1)
            .joinToString("|") { "${it.latitude},${it.longitude}" }

        val url = buildString {
            append("https://www.google.com/maps/dir/?api=1")
            append("&origin=").append(Uri.encode("${origin.latitude},${origin.longitude}"))
            append("&destination=").append(Uri.encode("${dest.latitude},${dest.longitude}"))
            if (waypoints.isNotBlank()) append("&waypoints=").append(Uri.encode(waypoints))
            append("&travelmode=driving")
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.apps.maps"))
    }

    // Ciclo de vida MapView
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState)
    }
}
