package com.example.proyecto_movil

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.firebase.database.*

class RutaEficienteActivity : AppCompatActivity() {

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var adapter: TiendasAdapter

    // --- UI ---
    private lateinit var cardResumen: View
    private lateinit var tvParadas: TextView
    private lateinit var tvProductos: TextView
    private lateinit var btnVerMapa: MaterialButton
    private lateinit var btnCompartir: MaterialButton

    // Modelo local para las paradas
    data class Stop(
        val title: String,
        val subtitle: String,
        val lat: Double? = null,
        val lng: Double? = null,
        val searchQuery: String? = null,
        val productsCount: Int = 0
    )

    private var stops: List<Stop> = emptyList()
    private var totalProducts: Int = 0

    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val ok = res[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) abrirMapsConRuta() else abrirMapsConRuta(sinOrigen = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ruta_eficiente)

        fused = LocationServices.getFusedLocationProviderClient(this)

        // --- Toolbar ---
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarRuta)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.title = "Ruta eficiente"
        toolbar.navigationIcon = AppCompatResources.getDrawable(this, R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.setTitleTextColor(android.graphics.Color.BLACK)
        toolbar.navigationIcon?.setTint(android.graphics.Color.BLACK)

        // --- Insets para los botones ---
        val acciones = findViewById<View>(R.id.acciones)
        ViewCompat.setOnApplyWindowInsetsListener(acciones) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom + bottom)
            insets
        }

        // --- Recycler ---
        val rv = findViewById<RecyclerView>(R.id.recyclerTiendas)
        adapter = TiendasAdapter()
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // --- Resumen / botones ---
        cardResumen = findViewById(R.id.cardResumen)
        tvParadas = findViewById(R.id.tvParadas)
        tvProductos = findViewById(R.id.tvProductos)
        btnVerMapa = findViewById(R.id.btnVerMapa)
        btnCompartir = findViewById(R.id.btnCompartir)

        cardResumen.visibility = View.GONE
        btnVerMapa.isEnabled = false
        btnCompartir.isEnabled = false

        btnVerMapa.setOnClickListener { solicitarPermisosYabrirMaps() }
        btnCompartir.setOnClickListener { compartirRuta() }

        cargarParadasDesdeLista()
    }

    /* --------------------- Firebase: cargar paradas --------------------- */
    private fun cargarParadasDesdeLista() {
        cardResumen.visibility = View.GONE
        btnVerMapa.isEnabled = false
        btnCompartir.isEnabled = false
        adapter.submitList(emptyList())

        FirebaseRefs.currentItemsRefAsync { ref, _ ->
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(ds: DataSnapshot) {
                    val mapManual = linkedMapOf<String, Stop>()
                    val mapGlobal = linkedMapOf<String, Stop>()
                    var productos = 0

                    for (item in ds.children) {
                        val qty = (item.child("qty").getValue(Int::class.java) ?: 0).coerceAtLeast(0)
                        if (qty <= 0) continue
                        productos += qty

                        val prod = item.child("product")
                        val source = prod.child("source").getValue(String::class.java).orEmpty()
                        val nombre = prod.child("nombre").getValue(String::class.java).orEmpty()
                        val marca = prod.child("marca").getValue(String::class.java).orEmpty()
                        val storeName = prod.child("storeName").getValue(String::class.java)
                        val storage = prod.child("storage").getValue(String::class.java)
                        val address = prod.child("address").getValue(String::class.java)
                        val lat = prod.child("lat").getValue(Double::class.java)
                        val lng = prod.child("lng").getValue(Double::class.java)

                        if (source == "manual" && lat != null && lng != null) {
                            // Productos ingresados manualmente con coordenadas
                            val titulo = storeName?.takeIf { it.isNotBlank() } ?: nombre.ifBlank { "Tienda local" }
                            val sub = address?.takeIf { it.isNotBlank() } ?: "Ubicación registrada por el usuario"
                            val key = "$titulo|$lat|$lng"

                            val prev = mapManual[key]
                            mapManual[key] = if (prev == null) {
                                Stop(
                                    title = titulo,
                                    subtitle = sub,
                                    lat = lat,
                                    lng = lng,
                                    searchQuery = null,
                                    productsCount = qty
                                )
                            } else {
                                prev.copy(productsCount = prev.productsCount + qty)
                            }
                        } else {
                            // 🏪 Productos de catálogo global → buscar siempre por storeName
                            val tienda = when {
                                !storeName.isNullOrBlank() -> storeName        // ✅ primero storeName
                                !storage.isNullOrBlank()   -> storage          // (si algún día lo usas)
                                !marca.isNullOrBlank()     -> marca
                                else -> "Tienda genérica"
                            }
                            val prev = mapGlobal[tienda]
                            mapGlobal[tienda] = if (prev == null) {
                                Stop(
                                    title = tienda,
                                    subtitle = "Se buscará la sede más cercana de $tienda",
                                    lat = null,
                                    lng = null,
                                    searchQuery = tienda,
                                    productsCount = qty
                                )
                            } else {
                                prev.copy(productsCount = prev.productsCount + qty)
                            }
                        }

                    }

                    stops = (mapGlobal.values + mapManual.values).toList()
                    totalProducts = productos
                    adapter.submitList(stops)

                    if (stops.isEmpty()) {
                        cardResumen.visibility = View.GONE
                        btnVerMapa.isEnabled = false
                        btnCompartir.isEnabled = false
                    } else {
                        tvParadas.text = "🔴 Paradas: ${stops.size}"
                        tvProductos.text = "🧺 Productos en lista: $totalProducts"
                        cardResumen.visibility = View.VISIBLE
                        btnVerMapa.isEnabled = true
                        btnCompartir.isEnabled = true
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@RutaEficienteActivity, "Error cargando lista.", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    /* --------------------- Google Maps externo --------------------- */
    private fun solicitarPermisosYabrirMaps() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            abrirMapsConRuta()
        } else {
            reqPerms.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun abrirMapsConRuta(sinOrigen: Boolean = false) {
        if (stops.isEmpty()) return

        val buildDestOrWp: (Stop) -> String = { s ->
            if (s.lat != null && s.lng != null) "${s.lat},${s.lng}" else Uri.encode("${s.searchQuery ?: s.title} Bogotá")
        }

        val openWithUrl: (String) -> Unit = { url ->
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .setPackage("com.google.android.apps.maps")
            )
        }

        val url = if (stops.size == 1) {
            "https://www.google.com/maps/dir/?api=1" +
                    "&destination=${buildDestOrWp(stops.first())}" +
                    "&travelmode=driving"
        } else {
            val dest = buildDestOrWp(stops.last())
            val wps = stops.dropLast(1).joinToString("|") { buildDestOrWp(it) }
            "https://www.google.com/maps/dir/?api=1" +
                    "&destination=$dest&waypoints=$wps&travelmode=driving"
        }

        openWithUrl(url)
    }

    /* --------------------- Compartir ruta --------------------- */
    private fun compartirRuta() {
        if (stops.isEmpty()) return
        val buildDestOrWp: (Stop) -> String = { s ->
            if (s.lat != null && s.lng != null) "${s.lat},${s.lng}"
            else Uri.encode("${s.searchQuery ?: s.title} Bogotá")
        }

        val url = if (stops.size == 1) {
            "https://www.google.com/maps/dir/?api=1" +
                    "&destination=${buildDestOrWp(stops.first())}&travelmode=driving"
        } else {
            val dest = buildDestOrWp(stops.last())
            val wps = stops.dropLast(1).joinToString("|") { buildDestOrWp(it) }
            "https://www.google.com/maps/dir/?api=1" +
                    "&destination=$dest&waypoints=$wps&travelmode=driving"
        }

        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "🛒 Mi ruta eficiente: $url")
        }
        startActivity(Intent.createChooser(i, "Compartir ruta…"))
    }

    /* --------------------- Adapter --------------------- */
    class TiendasAdapter :
        ListAdapter<Stop, TiendasAdapter.VH>(object : DiffUtil.ItemCallback<Stop>() {
            override fun areItemsTheSame(o: Stop, n: Stop) = o.title == n.title
            override fun areContentsTheSame(o: Stop, n: Stop) = o == n
        }) {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val avatar: TextView = view.findViewById(R.id.avatar)
            val tvNombre: TextView = view.findViewById(R.id.tvNombreTienda)
            val tvDireccion: TextView = view.findViewById(R.id.tvDireccionTienda)
            val chipTiempo: Chip = view.findViewById(R.id.chipTiempo)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tienda_pretty, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, pos: Int) {
            val t = getItem(pos)
            h.tvNombre.text = t.title
            h.tvDireccion.text = t.subtitle
            h.chipTiempo.text = "x${t.productsCount}"
            h.avatar.text = t.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        }
    }
}



