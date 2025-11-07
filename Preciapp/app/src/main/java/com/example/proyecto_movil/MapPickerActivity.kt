package com.example.proyecto_movil

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

class MapPickerActivity : AppCompatActivity() {

    private lateinit var map: GoogleMap
    private var marker: Marker? = null

    // UI
    private lateinit var root: View
    private lateinit var toolbarBack: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var bottomCard: MaterialCardView
    private lateinit var etDireccion: TextInputEditText
    private lateinit var btnUsarEsta: MaterialButton

    // Estado
    private var currentLatLng: LatLng? = null
    private var currentAddress: String? = null

    // Servicios
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val reqLocationPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val granted = res[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            enableMyLocationLayer()
            moveCameraToMyLocation()
        } else {
            moveCameraToBogota()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_picker)

        // refs
        root = findViewById(R.id.root)
        toolbarBack = findViewById(R.id.btnBack)
        toolbarTitle = findViewById(R.id.tvTitle)
        bottomCard = findViewById(R.id.bottomCard)
        etDireccion = findViewById(R.id.etDireccion)
        btnUsarEsta = findViewById(R.id.btnUsarEsta)

        toolbarTitle.text = "Elige ubicación"
        toolbarBack.setOnClickListener { finish() }

        // Obtener (o crear) el SupportMapFragment dentro del FrameLayout
        val mapFragment = (supportFragmentManager.findFragmentById(R.id.mapFragment)
                as? SupportMapFragment) ?: SupportMapFragment.newInstance().also { frag ->
            supportFragmentManager.beginTransaction()
                .replace(R.id.mapFragment, frag)
                .commitNow() // importante: que quede insertado antes de getMapAsync
        }

        mapFragment.getMapAsync { gMap ->
            map = gMap
            map.uiSettings.isZoomControlsEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true

            applyDynamicMapPadding()

            if (hasLocationPermission()) {
                enableMyLocationLayer()
                moveCameraToMyLocation()
            } else {
                reqLocationPerms.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }

            map.setOnCameraIdleListener {
                val target = map.cameraPosition.target
                putMarker(target)
                reverseGeocode(target)
            }
        }

        btnUsarEsta.setOnClickListener {
            val data = Intent().apply {
                putExtra("lat", currentLatLng?.latitude)
                putExtra("lng", currentLatLng?.longitude)
                putExtra(
                    "address",
                    currentAddress ?: etDireccion.text?.toString()?.trim().orEmpty()
                )
            }
            setResult(RESULT_OK, data)
            finish()
        }
    }

    private fun applyDynamicMapPadding() {
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val statusBarH = statusBarHeight()
                val toolbarH = findViewById<View>(R.id.toolbar).height
                val bottomH = bottomCard.height + dp(16)
                if (::map.isInitialized) map.setPadding(0, statusBarH + toolbarH, 0, bottomH)
            }
        }
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun statusBarHeight(): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
    }

    private fun hasLocationPermission(): Boolean {
        val f = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val c = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        return f || c
    }

    private fun enableMyLocationLayer() {
        if (::map.isInitialized && hasLocationPermission()) {
            try { map.isMyLocationEnabled = true } catch (_: SecurityException) {}
        }
    }

    private fun moveCameraToMyLocation() {
        try {
            fused.lastLocation
                .addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        val here = LatLng(loc.latitude, loc.longitude)
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(here, 15f))
                    } else {
                        moveCameraToBogota()
                    }
                }
                .addOnFailureListener { moveCameraToBogota() }
        } catch (_: SecurityException) {
            moveCameraToBogota()
        }
    }

    private fun moveCameraToBogota() {
        val bogota = LatLng(4.7110, -74.0721)
        if (::map.isInitialized) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(bogota, 14f))
            putMarker(bogota)
            reverseGeocode(bogota)
        }
    }

    private fun putMarker(latLng: LatLng) {
        currentLatLng = latLng
        if (marker == null) {
            marker = map.addMarker(MarkerOptions().position(latLng))
        } else {
            marker?.position = latLng
        }
    }

    private fun reverseGeocode(latLng: LatLng) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= 33) {
                geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { list ->
                    val addr = list.firstOrNull()?.getAddressLine(0)
                    currentAddress = addr
                    etDireccion.setText(addr ?: "")
                }
            } else {
                @Suppress("DEPRECATION")
                val list = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                val addr = list?.firstOrNull()?.getAddressLine(0)
                currentAddress = addr
                etDireccion.setText(addr ?: "")
            }
        } catch (_: Exception) {
            currentAddress = null
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

