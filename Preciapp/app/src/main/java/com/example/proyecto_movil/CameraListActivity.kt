package com.example.proyecto_movil

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale
import org.json.JSONObject



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
            val storeName = d.getStringExtra("storeName") ?: "D1"

            val prod = CatalogProduct(
                nombre = nombre,
                marca = marca,
                categoria = cat,
                precio = precio,
                imageUri = uri,
                lat = if (!lat.isNaN()) lat else null,
                lng = if (!lng.isNaN()) lng else null,
                address = address,
                storeName = storeName,
                source = "manual"
            )

            FirebaseRefs.saveMyCatalogProduct(prod)
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
        if (!success) {
            Snackbar.make(recycler, "📷 Captura cancelada", Snackbar.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        try {
            val bitmap = android.provider.MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
            BarcodeScannerUtil.scan(bitmap) { code ->
                if (code == null) {
                    Snackbar.make(recycler, "⚠️ No se detectó código de barras.", Snackbar.LENGTH_LONG).show()
                    return@scan
                }

                // ✅ Vibración corta segura al detectar el código
                try {
                    val vibrator: Vibrator? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        val vm = getSystemService(android.os.VibratorManager::class.java)
                        vm?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    }
                    if (vibrator?.hasVibrator() == true) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(150)
                        }
                    }
                } catch (_: SecurityException) {
                    // Sin permiso VIBRATE o dispositivo sin vibrador → ignora
                } catch (_: Throwable) { }

                buscarProductoPorCodigo(code)
            }
        } catch (e: Exception) {
            Snackbar.make(recycler, "Error procesando la imagen: ${e.message}", Snackbar.LENGTH_LONG).show()
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

    /* ==================== CICLO DE VIDA ==================== */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_list)

        FirebaseRefs.warmupKeepSynced()

        recycler = findViewById(R.id.recyclerView)
        tvPresupuesto = findViewById(R.id.tvPresupuesto)
        tvGastado = findViewById(R.id.tvGastado)
        tvRestante = findViewById(R.id.tvRestante)
        etBuscar = findViewById(R.id.etProduct)
        btnScan = findViewById(R.id.btnScan)
        btnAddManual = findViewById(R.id.btnAddManual)
        ivBack = findViewById(R.id.ivBack)
        ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        presupuestoTotal = leerPresupuesto()
        tvPresupuesto.text = "Total: ${nf.format(presupuestoTotal)}"
        renderTotales()

        adapter = ProductosAdapter(
            onSumar = { p -> cambiarCantidad(p, +1) },
            onRestar = { p -> cambiarCantidad(p, -1) },
            getCantidad = { p -> cantidades[clave(p)] ?: 0 },
            format = nf,
            onItemClick = { producto ->
                // ✅ Este bloque asegura que siempre se incluya storeName al guardar
                upsertQtyInCart(
                    producto.copy(
                        storeName = producto.storeName ?: "Tienda genérica",
                        source = "catalog_search"
                    ),
                    (cantidades[clave(producto)] ?: 0) + 1
                )

                Snackbar.make(recycler, "🛒 Producto agregado: ${producto.nombre}", Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(0xFF4CAF50.toInt())
                    .show()
            }
        )

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        pushVisibles()

        btnScan.setOnClickListener { ensureCameraPermissionThenOpen() }
        btnAddManual.setOnClickListener {
            manualLauncher.launch(Intent(this, ManualProductActivity::class.java))
        }

        etBuscar.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrarCatalogo(s?.toString().orEmpty())
            }
        })

        attachCatalogListeners()

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

    /* ==================== ESCANEO: FIREBASE + OPEN FOOD FACTS ==================== */
    private fun buscarProductoPorCodigo(code: String) {
        val catalogRef = FirebaseRefs.catalogRef()
        catalogRef.child(code).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // 🔍 Extraer todos los campos correctamente del catálogo
                    val nombre = snapshot.child("name").getValue(String::class.java).orEmpty()
                    val marca = snapshot.child("brand").getValue(String::class.java).orEmpty()
                    val categoria = snapshot.child("category").getValue(String::class.java).orEmpty()
                    val precio = snapshot.child("price").getValue(Long::class.java) ?: 0L
                    val imageUri = snapshot.child("imageUri").getValue(String::class.java)
                    val storeName = snapshot.child("storeName").getValue(String::class.java) // ✅ aquí está la clave

                    val prod = CatalogProduct(
                        nombre = nombre,
                        marca = marca,
                        categoria = categoria,
                        precio = precio,
                        imageUri = imageUri,
                        storeName = storeName,  // ✅ mantenerlo
                        source = "scan"
                    )

                    // Agregar al carrito manteniendo storeName
                    upsertQtyInCart(prod, (cantidades[clave(prod)] ?: 0) + 1, source = "scan")

                    Snackbar.make(recycler, "✅ Producto encontrado: $nombre", Snackbar.LENGTH_LONG)
                        .setBackgroundTint(0xFF4CAF50.toInt())
                        .show()
                } else {
                    // Si no existe en catalog, intenta obtenerlo por API externa
                    fetchProductoDesdeOpenFoodFacts(code)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }


    private fun fetchProductoDesdeOpenFoodFacts(barcode: String) {
        Thread {
            try {
                val url = URL("https://world.openfoodfacts.org/api/v2/product/$barcode.json")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connect()

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val status = json.optInt("status", 0)

                if (status == 1) {
                    val prodObj = json.getJSONObject("product")
                    val nombre = prodObj.optString("product_name", "Producto sin nombre")
                    val marca = prodObj.optString("brands", "Sin marca")
                    val categoria = prodObj.optString("categories", "Otros")
                    val imageUri = prodObj.optString("image_url", null)

                    // 🏪 Asignar tienda base según categoría
                    val tienda = when {
                        categoria.contains("bebidas", true) -> "D1"
                        categoria.contains("lácteos", true) -> "Éxito"
                        categoria.contains("aseo", true) -> "Ara"
                        categoria.contains("snack", true) -> "Carulla"
                        else -> "Tienda Genérica"
                    }

                    runOnUiThread {
                        // Solicitar el precio una sola vez
                        val input = android.widget.EditText(this@CameraListActivity)
                        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        input.hint = "Ej: 3500"

                        androidx.appcompat.app.AlertDialog.Builder(this@CameraListActivity)
                            .setTitle("Precio del producto")
                            .setMessage("Introduce el precio de $nombre")
                            .setView(input)
                            .setPositiveButton("Guardar") { _, _ ->
                                val precio = input.text.toString().toLongOrNull() ?: 0L

                                // Crear objeto del producto con todos los datos
                                val nuevoProd = CatalogProduct(
                                    nombre = nombre,
                                    marca = marca,
                                    categoria = categoria,
                                    precio = precio,
                                    imageUri = imageUri,
                                    storeName = tienda,
                                    source = "scan_api"
                                )

                                // 💾 Guardar el producto directamente en catalog/ global
                                val catalogRef = FirebaseRefs.catalogRef()
                                // Usamos el código de barras como clave global para evitar duplicados
                                val nodeRef = catalogRef.child(barcode)
                                val dataMap = mapOf(
                                    "name" to nuevoProd.nombre,
                                    "brand" to nuevoProd.marca,
                                    "category" to nuevoProd.categoria,
                                    "price" to nuevoProd.precio,
                                    "imageUri" to nuevoProd.imageUri,
                                    "storeName" to nuevoProd.storeName,
                                    "barcode" to barcode
                                )
                                nodeRef.setValue(dataMap)
                                    .addOnSuccessListener {
                                        vibrateShort()
                                        upsertQtyInCart(nuevoProd, 1, source = "scan_api")
                                        Snackbar.make(
                                            recycler,
                                            "✅ Producto agregado y sincronizado: ${nuevoProd.nombre}",
                                            Snackbar.LENGTH_LONG
                                        ).setBackgroundTint(0xFF4CAF50.toInt()).show()
                                    }
                                    .addOnFailureListener { e ->
                                        Snackbar.make(
                                            recycler,
                                            "Error al guardar producto: ${e.localizedMessage}",
                                            Snackbar.LENGTH_LONG
                                        ).setBackgroundTint(0xFFF44336.toInt()).show()
                                    }
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    }

                } else {
                    runOnUiThread {
                        Snackbar.make(
                            recycler,
                            "⚠️ Código no encontrado en Open Food Facts.",
                            Snackbar.LENGTH_LONG
                        ).setBackgroundTint(0xFFF44336.toInt()).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Snackbar.make(
                        recycler,
                        "Error al consultar OpenFoodFacts: ${e.message}",
                        Snackbar.LENGTH_LONG
                    ).setBackgroundTint(0xFFF44336.toInt()).show()
                }
            }
        }.start()
    }



    /* ==================== CÁMARA ==================== */
    private fun ensureCameraPermissionThenOpen() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
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
            FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", photoFile)
        } catch (e: Exception) {
            Snackbar.make(recycler, "Error FileProvider: ${e.message}", Snackbar.LENGTH_LONG).show()
            null
        }
        photoUri ?: return
        takePicture.launch(photoUri)
    }

    /* ==================== PRESUPUESTO / TOTALES ==================== */
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

    /* ==================== CATÁLOGOS (GLOBAL + PRIVADO) ==================== */
    private fun attachCatalogListeners() {
        // --- Catálogo global (acepta llaves en inglés o español) ---
        val gRef = FirebaseRefs.catalogRef()
        globalCatalogListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                globalCatalog.clear()
                for (child in s.children) {
                    val nombre = child.child("name").getValue(String::class.java)
                        ?: child.child("nombre").getValue(String::class.java).orEmpty()
                    if (nombre.isBlank()) continue

                    val marca = child.child("brand").getValue(String::class.java)
                        ?: child.child("marca").getValue(String::class.java).orEmpty()
                    val categoria = child.child("category").getValue(String::class.java)
                        ?: child.child("categoria").getValue(String::class.java).orEmpty()
                    val precio = child.child("price").getValue(Long::class.java)
                        ?: child.child("precio").getValue(Long::class.java) ?: 0L
                    val imageUri = child.child("imageUri").getValue(String::class.java)
                    val storeName = child.child("storeName").getValue(String::class.java) // ✅ NUEVO

                    val p = CatalogProduct(
                        nombre = nombre,
                        marca = marca,
                        categoria = categoria,
                        precio = precio,
                        imageUri = imageUri,
                        storeName = storeName // ✅ se conserva para futuras búsquedas
                    )

                    globalCatalog[clave(p)] = p
                }
                refreshMergedCatalog()
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        gRef.addValueEventListener(globalCatalogListener!!)

        // --- Catálogo privado del usuario ---
        val mRef = FirebaseRefs.myCatalogRef()
        myCatalogListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                myCatalog.clear()
                for (child in s.children) {
                    val nombre = child.child("nombre").getValue(String::class.java)
                        ?: child.child("name").getValue(String::class.java).orEmpty()
                    if (nombre.isBlank()) continue

                    val marca = child.child("marca").getValue(String::class.java)
                        ?: child.child("brand").getValue(String::class.java).orEmpty()
                    val categoria = child.child("categoria").getValue(String::class.java)
                        ?: child.child("category").getValue(String::class.java).orEmpty()
                    val precio = child.child("precio").getValue(Long::class.java)
                        ?: child.child("price").getValue(Long::class.java) ?: 0L
                    val imageUri = child.child("imageUri").getValue(String::class.java)
                    val lat = child.child("lat").getValue(Double::class.java)
                    val lng = child.child("lng").getValue(Double::class.java)
                    val address = child.child("address").getValue(String::class.java)
                    val store = child.child("storeName").getValue(String::class.java)

                    val p = CatalogProduct(
                        nombre = nombre,
                        marca = marca,
                        categoria = categoria,
                        precio = precio,
                        imageUri = imageUri,
                        lat = lat,
                        lng = lng,
                        address = address,
                        storeName = store
                    )
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
        catalogoLocal.addAll(myCatalog.values)
        for ((k, p) in globalCatalog) if (!myCatalog.containsKey(k)) catalogoLocal.add(p)
        val q = etBuscar.text?.toString()?.trim().orEmpty()
        filtrarCatalogo(q)
    }

    private fun filtrarCatalogo(query: String) {
        val q = query.trim().lowercase()

        // 🔍 Filtra resultados por nombre, marca o categoría
        resultadosBusqueda =
            if (q.isEmpty()) emptyList()
            else catalogoLocal.filter {
                it.nombre.lowercase().contains(q) ||
                        it.marca.lowercase().contains(q) ||
                        it.categoria.lowercase().contains(q)
            }

        // 📋 Actualiza la lista visible
        pushVisibles()

        // 🧠 Ahora, si el usuario toca o agrega un producto desde búsqueda,
        // nos aseguramos de que el producto conserve su storeName real del catálogo
        adapter = ProductosAdapter(
            onSumar = { p ->
                val match = globalCatalog.values.firstOrNull {
                    it.nombre.equals(p.nombre, true) && it.marca.equals(p.marca, true)
                }
                val productoFinal = if (match != null && !match.storeName.isNullOrBlank()) {
                    p.copy(storeName = match.storeName)
                } else {
                    p
                }
                cambiarCantidad(productoFinal, +1)
            },
            onRestar = { p -> cambiarCantidad(p, -1) },
            getCantidad = { p -> cantidades[clave(p)] ?: 0 },
            format = nf,
            onItemClick = { p ->
                val match = globalCatalog.values.firstOrNull {
                    it.nombre.equals(p.nombre, true) && it.marca.equals(p.marca, true)
                }
                val productoFinal = if (match != null && !match.storeName.isNullOrBlank()) {
                    p.copy(storeName = match.storeName)
                } else {
                    p
                }
                upsertQtyInCart(productoFinal, (cantidades[clave(productoFinal)] ?: 0) + 1, source = "catalog")
            }
        )

        recycler.adapter = adapter
    }


    /* ==================== ITEMS (CARRITO) ==================== */
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
                    val nombre = prodSnap.child("nombre").getValue(String::class.java)
                        ?: prodSnap.child("name").getValue(String::class.java).orEmpty()
                    if (nombre.isBlank()) continue
                    val marca = prodSnap.child("marca").getValue(String::class.java)
                        ?: prodSnap.child("brand").getValue(String::class.java).orEmpty()
                    val categoria = prodSnap.child("categoria").getValue(String::class.java)
                        ?: prodSnap.child("category").getValue(String::class.java).orEmpty()
                    val precio = prodSnap.child("precio").getValue(Long::class.java)
                        ?: prodSnap.child("price").getValue(Long::class.java) ?: 0L
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

    /* ==================== LISTA VISIBLE ==================== */
    private fun pushVisibles() {
        val q = etBuscar.text?.toString()?.trim().orEmpty()
        val nueva = if (q.isEmpty()) carritoActual.toList() else resultadosBusqueda.toList()
        recycler.post { adapter.submitList(nueva) }
    }

    /* ==================== SUMAR / RESTAR ==================== */
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

        // Persistir
        upsertQtyInCart(p, targetQty = nuevo)
    }

    /* ==================== PERSISTENCIA EN RTDB ==================== */
    /** Crea o actualiza el item en la lista asegurando guardar storeName */
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

        // ✅ siempre asegurar que tenga un storeName antes de guardar
        val storeFinal = when {
            !p.storeName.isNullOrBlank() -> p.storeName
            !p.marca.isNullOrBlank() -> p.marca      // fallback si viene marca
            p.categoria.contains("bebidas", true) -> "Éxito"
            p.categoria.contains("snack", true) -> "Carulla"
            else -> "Tienda Genérica"
        }

        // actualizar cantidad si ya existe
        if (existingKey != null) {
            val node = readyRef.child(existingKey)
            if (targetQty <= 0) node.removeValue()
            else node.child("qty").setValue(targetQty)
            return
        }

        // crear nuevo item si qty > 0
        if (targetQty <= 0) return

        val push = readyRef.push()
        val (lat, lng, addr) = extraLocation ?: Triple(Double.NaN, Double.NaN, "")

        val productMap = mutableMapOf<String, Any?>(
            "nombre" to p.nombre,
            "marca" to p.marca,
            "categoria" to p.categoria,
            "precio" to p.precio,
            "imageUri" to p.imageUri,
            "source" to source,
            "storeName" to storeFinal   // ✅ se garantiza aquí siempre
        )

        // ubicación opcional (solo en items de origen "manual")
        if (lat.isFinite() && lng.isFinite()) {
            productMap["lat"] = lat
            productMap["lng"] = lng
        }
        if (addr.isNotBlank()) productMap["address"] = addr

        val itemMap = mapOf("product" to productMap, "qty" to targetQty)
        push.setValue(itemMap)
    }



    private fun vibrateShort() {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = this@CameraListActivity.getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                this@CameraListActivity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            150,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(150)
                }
            }
        } catch (_: SecurityException) {
            // No tiene permiso o hardware de vibración
        } catch (_: Throwable) { }
    }

    /* ==================== HELPERS ==================== */
    private fun clave(p: CatalogProduct) = "${p.nombre}|${p.marca}"
}
