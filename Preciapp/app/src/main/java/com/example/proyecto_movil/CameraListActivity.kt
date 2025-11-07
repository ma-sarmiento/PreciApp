package com.example.proyecto_movil

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.*
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraListActivity : AppCompatActivity() {

    /* -------------------- prefs -------------------- */
    private val prefsNewName = "preciapp_prefs"
    private val keyPresupuestoNew = "presupuesto_total"
    private val prefsOldName = "prefs"
    private val keyPresupuestoOld = "presupuesto"

    /* -------------------- launchers -------------------- */
    private val manualLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val d = result.data ?: return@registerForActivityResult
            val nombre = d.getStringExtra("nombre") ?: return@registerForActivityResult
            val marca = d.getStringExtra("marca") ?: "Manual"
            val cat = d.getStringExtra("categoria") ?: "Otros"
            val precio: Long = d.getLongExtra("precio", 0L)
            val cant: Int = d.getIntExtra("cantidad", 1)
            val uri = d.getStringExtra("imageUri")
            val lat = d.getDoubleExtra("lat", Double.NaN)
            val lng = d.getDoubleExtra("lng", Double.NaN)
            val address = d.getStringExtra("address").orEmpty()

            val prod = CatalogProduct(nombre, marca, cat, precio, uri)

            // ✅ 1) Guardar/actualizar el producto en el catálogo PRIVADO del usuario
            FirebaseRefs.saveMyCatalogProduct(prod)

            // ✅ 2) Añadir al carrito (solo cantidades)
            upsertQtyInCart(
                prod,
                targetQty = (cantidades[clave(prod)] ?: 0) + cant,
                extraLocation = Triple(lat, lng, address),
                source = "manual"
            )
        }
    }

    /* -------------------- UI -------------------- */
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ProductosAdapter
    private lateinit var tvPresupuesto: TextView
    private lateinit var tvGastado: TextView
    private lateinit var tvRestante: TextView
    private lateinit var etBuscar: TextInputEditText
    private lateinit var btnScan: MaterialButton
    private lateinit var btnAddManual: MaterialButton
    private lateinit var ivBack: ImageView

    /* -------------------- cámara -------------------- */
    private var photoUri: Uri? = null
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openCamera()
        else Snackbar.make(recycler, "Permiso de cámara denegado", Snackbar.LENGTH_LONG).show()
    }
    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val prod = CatalogProduct(
                nombre = "Producto escaneado $ts",
                marca = "Cámara",
                categoria = "Otros",
                precio = 0L,
                imageUri = photoUri?.toString()
            )
            upsertQtyInCart(
                prod,
                targetQty = (cantidades[clave(prod)] ?: 0) + 1,
                source = "camera"
            )
        } else {
            Snackbar.make(recycler, "Captura cancelada", Snackbar.LENGTH_SHORT).show()
        }
    }

    /* -------------------- dinero / carrito -------------------- */
    private val nf = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
    private var presupuestoTotal: Long = 0L
    private var gastado: Long = 0L

    private val cantidades = mutableMapOf<String, Int>()                // clave = nombre|marca
    private val itemKeyByClave = mutableMapOf<String, String>()         // clave → pushKey
    private val productoDeItem = mutableMapOf<String, CatalogProduct>() // pushKey → producto

    /* -------------------- catálogos y carrito (vista) -------------------- */
    private val globalCatalog = mutableMapOf<String, CatalogProduct>()  // catalog/
    private val myCatalog     = mutableMapOf<String, CatalogProduct>()  // users/{uid}/myCatalog
    private val catalogoLocal = mutableListOf<CatalogProduct>()         // merge de ambos
    private var resultadosBusqueda: List<CatalogProduct> = emptyList()
    private var carritoActual: List<CatalogProduct> = emptyList()

    /* -------------------- RTDB -------------------- */
    private var globalCatalogListener: ValueEventListener? = null
    private var myCatalogListener: ValueEventListener? = null
    private var itemsListener: ValueEventListener? = null
    private var itemsRef: DatabaseReference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_list)

        // Warmup de caché (catálogo global, lista actual y catálogo privado)
        FirebaseRefs.warmupKeepSynced()

        // UI
        recycler = findViewById(R.id.recyclerView)
        tvPresupuesto = findViewById(R.id.tvPresupuesto)
        tvGastado = findViewById(R.id.tvGastado)
        tvRestante = findViewById(R.id.tvRestante)
        etBuscar = findViewById(R.id.etProduct)
        btnScan = findViewById(R.id.btnScan)
        btnAddManual = findViewById(R.id.btnAddManual)
        ivBack = findViewById(R.id.ivBack)
        ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Presupuesto persistente
        presupuestoTotal = leerPresupuesto()
        tvPresupuesto.text = "Total: ${nf.format(presupuestoTotal)}"
        renderTotales()

        // Recycler
        adapter = ProductosAdapter(
            onSumar = { p -> cambiarCantidad(p, +1) },
            onRestar = { p -> cambiarCantidad(p, -1) },
            getCantidad = { p -> cantidades[clave(p)] ?: 0 },
            format = nf
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        pushVisibles()

        // Acciones
        btnScan.setOnClickListener { ensureCameraPermissionThenOpen() }
        btnAddManual.setOnClickListener {
            manualLauncher.launch(Intent(this, ManualProductActivity::class.java))
        }

        // Búsqueda sobre catálogo combinado (global + privado)
        etBuscar.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrarCatalogo(s?.toString().orEmpty())
            }
        })

        // Escuchar catálogo global y catálogo privado del usuario
        attachCatalogListeners()

        // Asegurar lista actual y escuchar items
        FirebaseRefs.currentItemsRefAsync { ref, _ ->
            itemsRef = ref
            attachItemsListener(ref)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        globalCatalogListener?.let { FirebaseRefs.catalogRef().removeEventListener(it) }
        myCatalogListener?.let { FirebaseRefs.myCatalogRef().removeEventListener(it) }
        itemsListener?.let { itemsRef?.removeEventListener(it) }
    }

    /* -------------------- Cámara -------------------- */
    private fun ensureCameraPermissionThenOpen() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) openCamera() else requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun openCamera() {
        val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        if (dir == null) {
            Snackbar.make(recycler, "No se pudo acceder a Pictures", Snackbar.LENGTH_LONG).show()
            return
        }
        val photoFile = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
        photoUri = try {
            FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                photoFile
            )
        } catch (e: Exception) {
            Snackbar.make(recycler, "Error FileProvider: ${e.message}", Snackbar.LENGTH_LONG).show()
            null
        }
        photoUri ?: return
        takePicture.launch(photoUri)
    }

    /* -------------------- Presupuesto -------------------- */
    private fun leerPresupuesto(): Long {
        val spNew = getSharedPreferences(prefsNewName, Context.MODE_PRIVATE)
        val v = spNew.getLong(keyPresupuestoNew, 0L)
        if (v > 0L) return v
        val spOld = getSharedPreferences(prefsOldName, Context.MODE_PRIVATE)
        val viejo = spOld.getFloat(keyPresupuestoOld, 0f)
        if (viejo > 0f) {
            val mig = viejo.toLong()
            spNew.edit().putLong(keyPresupuestoNew, mig).apply()
            return mig
        }
        return 0L
    }

    private fun renderTotales() {
        val restante = presupuestoTotal - gastado
        tvGastado.text = "Gastado: ${nf.format(gastado)}"
        tvRestante.text = "Restante: ${nf.format(restante.coerceAtLeast(0))}"
        tvRestante.setTextColor(if (restante < 0) 0xFFD32F2F.toInt() else 0xFF101828.toInt())
    }

    /* -------------------- Catálogos (global + privado) -------------------- */
    private fun attachCatalogListeners() {
        // Global
        val gRef = FirebaseRefs.catalogRef()
        globalCatalogListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                globalCatalog.clear()
                for (child in s.children) {
                    val nombre = child.child("nombre").getValue(String::class.java).orEmpty()
                    if (nombre.isBlank()) continue
                    val marca = child.child("marca").getValue(String::class.java).orEmpty()
                    val categoria = child.child("categoria").getValue(String::class.java).orEmpty()
                    val precio = child.child("precio").getValue(Long::class.java) ?: 0L
                    val imageUri = child.child("imageUri").getValue(String::class.java)
                    val p = CatalogProduct(nombre, marca, categoria, precio, imageUri)
                    globalCatalog[clave(p)] = p
                }
                refreshMergedCatalog()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        gRef.addValueEventListener(globalCatalogListener!!)

        // Privado (mis productos)
        val mRef = FirebaseRefs.myCatalogRef()
        myCatalogListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                myCatalog.clear()
                for (child in s.children) {
                    val nombre = child.child("nombre").getValue(String::class.java).orEmpty()
                    if (nombre.isBlank()) continue
                    val marca = child.child("marca").getValue(String::class.java).orEmpty()
                    val categoria = child.child("categoria").getValue(String::class.java).orEmpty()
                    val precio = child.child("precio").getValue(Long::class.java) ?: 0L
                    val imageUri = child.child("imageUri").getValue(String::class.java)
                    val p = CatalogProduct(nombre, marca, categoria, precio, imageUri)
                    myCatalog[clave(p)] = p
                }
                refreshMergedCatalog()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        mRef.addValueEventListener(myCatalogListener!!)
    }

    /** Fusiona catálogo global + privado y actualiza búsqueda/adapter. */
    private fun refreshMergedCatalog() {
        catalogoLocal.clear()

        // 1) primero los del catálogo privado (preferencia del usuario)
        catalogoLocal.addAll(myCatalog.values)

        // 2) luego los globales que no estén duplicados por clave
        for ((k, p) in globalCatalog) {
            if (!myCatalog.containsKey(k)) catalogoLocal.add(p)
        }

        // Reaplicar filtro si había texto
        val q = etBuscar.text?.toString()?.trim().orEmpty()
        filtrarCatalogo(q)
    }

    private fun filtrarCatalogo(query: String) {
        val q = query.trim().lowercase()
        resultadosBusqueda =
            if (q.isEmpty()) emptyList()
            else catalogoLocal.filter {
                it.nombre.lowercase().contains(q) ||
                        it.marca.lowercase().contains(q) ||
                        it.categoria.lowercase().contains(q)
            }
        pushVisibles()
    }

    /* -------------------- Items (carrito) -------------------- */
    private fun attachItemsListener(ref: DatabaseReference) {
        itemsListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                cantidades.clear()
                itemKeyByClave.clear()
                productoDeItem.clear()

                val lista = mutableListOf<CatalogProduct>()
                var totalGastado = 0L

                for (itemSnap in s.children) {
                    val key = itemSnap.key ?: continue
                    val qty = (itemSnap.child("qty").getValue(Int::class.java) ?: 0).coerceAtLeast(0)

                    val prodSnap = itemSnap.child("product")
                    val nombre = prodSnap.child("nombre").getValue(String::class.java).orEmpty()
                    if (nombre.isBlank()) continue
                    val marca = prodSnap.child("marca").getValue(String::class.java).orEmpty()
                    val categoria = prodSnap.child("categoria").getValue(String::class.java).orEmpty()
                    val precio = prodSnap.child("precio").getValue(Long::class.java) ?: 0L
                    val imageUri = prodSnap.child("imageUri").getValue(String::class.java)

                    val p = CatalogProduct(nombre, marca, categoria, precio, imageUri)
                    val k = clave(p)

                    cantidades[k] = qty
                    itemKeyByClave[k] = key
                    productoDeItem[key] = p

                    if (qty > 0) {
                        lista.add(p)
                        totalGastado += precio * qty
                    }
                }

                carritoActual = lista
                gastado = totalGastado
                renderTotales()
                pushVisibles()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(itemsListener!!)
    }

    /* -------------------- Lista visible -------------------- */
    private fun pushVisibles() {
        val q = etBuscar.text?.toString()?.trim().orEmpty()
        val nueva = if (q.isEmpty()) carritoActual.toList() else resultadosBusqueda.toList()
        recycler.post { adapter.submitList(nueva) }
    }

    /* -------------------- Sumar / restar (UI optimista) -------------------- */
    private fun cambiarCantidad(p: CatalogProduct, delta: Int) {
        val k = clave(p)
        val actual = cantidades[k] ?: 0
        val nuevo = (actual + delta).coerceAtLeast(0)

        // límite de presupuesto al sumar
        if (delta > 0 && presupuestoTotal > 0) {
            val potencial = gastado + p.precio
            if (potencial > presupuestoTotal) {
                tvRestante.setTextColor(0xFFD32F2F.toInt())
                tvRestante.animate().alpha(0.5f).setDuration(80).withEndAction {
                    tvRestante.alpha = 1f
                    tvRestante.setTextColor(0xFF101828.toInt())
                }.start()
                return
            }
        }

        // UI optimista
        cantidades[k] = nuevo
        val lista = adapter.currentList
        val idx = lista.indexOfFirst { it.nombre.equals(p.nombre, true) && it.marca == p.marca }
        if (idx >= 0) recycler.post { adapter.notifyItemChanged(idx) }

        var total = 0L
        for (item in lista) {
            val q = (cantidades[clave(item)] ?: 0).toLong()
            total += q * item.precio
        }
        gastado = total
        renderTotales()

        // Persistir en RTDB
        upsertQtyInCart(p, targetQty = nuevo)
    }

    /**
     * Sube/actualiza la qty en RTDB. Si itemsRef no está listo, lo resuelve y reintenta.
     */
    private fun upsertQtyInCart(
        p: CatalogProduct,
        targetQty: Int,
        extraLocation: Triple<Double, Double, String>? = null,
        source: String = "catalog"
    ) {
        val readyRef = itemsRef
        if (readyRef == null) {
            FirebaseRefs.currentItemsRefAsync { ref, _ ->
                itemsRef = ref
                upsertQtyInCart(p, targetQty, extraLocation, source)
            }
            return
        }

        val k = clave(p)
        val existingKey = itemKeyByClave[k]

        if (existingKey != null) {
            val node = readyRef.child(existingKey)
            if (targetQty <= 0) node.removeValue()
            else node.child("qty").setValue(targetQty)
            return
        }

        if (targetQty <= 0) return

        val push = readyRef.push()
        val (lat, lng, addr) = extraLocation ?: Triple(Double.NaN, Double.NaN, "")
        val productMap = mutableMapOf<String, Any?>(
            "nombre" to p.nombre,
            "marca" to p.marca,
            "categoria" to p.categoria,
            "precio" to p.precio,
            "imageUri" to p.imageUri,
            "source" to source
        )
        if (lat.isFinite() && lng.isFinite()) {
            productMap["lat"] = lat
            productMap["lng"] = lng
        }
        if (addr.isNotBlank()) productMap["address"] = addr

        val itemMap = mapOf("product" to productMap, "qty" to targetQty)
        push.setValue(itemMap)
    }

    /* -------------------- Helpers -------------------- */
    private fun clave(p: CatalogProduct) = "${p.nombre}|${p.marca}"
}
