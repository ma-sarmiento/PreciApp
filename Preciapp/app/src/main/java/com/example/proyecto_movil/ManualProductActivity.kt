package com.example.proyecto_movil

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import java.io.File

class ManualProductActivity : AppCompatActivity() {

    // UI
    private lateinit var ivBack: ImageView
    private lateinit var ivFoto: ImageView
    private lateinit var etNombre: TextInputEditText
    private lateinit var etMarca: TextInputEditText
    private lateinit var actvCategoria: MaterialAutoCompleteTextView
    private lateinit var etPrecio: TextInputEditText
    private lateinit var tvCantidad: TextView
    private lateinit var btnMenos: MaterialButton
    private lateinit var btnMas: MaterialButton
    private lateinit var btnTomarFoto: MaterialButton
    private lateinit var btnGuardar: MaterialButton

    // Ubicación / tienda (opcionales)
    private lateinit var etNombreTienda: TextInputEditText
    private lateinit var etDireccion: TextInputEditText
    private lateinit var btnElegirEnMapa: MaterialButton
    private lateinit var tvCoords: TextView

    // Estado
    private var cantidad = 1
    private var photoUri: Uri? = null
    private var selLat: Double? = null
    private var selLng: Double? = null

    /* ---------------- Launchers ---------------- */

    private val reqCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) openCamera() else snack("Permiso de cámara denegado") }

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) ivFoto.setImageURI(photoUri)
        else { photoUri = null; snack("Captura cancelada") }
    }

    private val pickOnMap = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK && res.data != null) {
            val lat = res.data!!.getDoubleExtra("lat", Double.NaN)
            val lng = res.data!!.getDoubleExtra("lng", Double.NaN)
            selLat = if (lat.isFinite()) lat else null
            selLng = if (lng.isFinite()) lng else null
            val addr = res.data!!.getStringExtra("address").orEmpty()

            if (selLat != null && selLng != null) {
                tvCoords.text = "Lat: ${"%.5f".format(selLat)}  Lng: ${"%.5f".format(selLng)}"
            } else {
                tvCoords.text = getString(R.string.no_location)
            }
            if (addr.isNotBlank()) etDireccion.setText(addr)
        }
    }

    /* ---------------- Lifecycle ---------------- */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_product)

        // Referencias UI
        ivBack = findViewById(R.id.ivBack)
        ivFoto = findViewById(R.id.ivFoto)
        etNombre = findViewById(R.id.etNombre)
        etMarca = findViewById(R.id.etMarca)
        actvCategoria = findViewById(R.id.actv_categoria)
        etPrecio = findViewById(R.id.etPrecio)
        tvCantidad = findViewById(R.id.tvCantidad)
        btnMenos = findViewById(R.id.btnMenos)
        btnMas = findViewById(R.id.btnMas)
        btnTomarFoto = findViewById(R.id.btnTomarFoto)
        btnGuardar = findViewById(R.id.btnGuardar)
        etNombreTienda = findViewById(R.id.etNombreTienda)
        etDireccion = findViewById(R.id.etDireccion)
        btnElegirEnMapa = findViewById(R.id.btnElegirEnMapa)
        tvCoords = findViewById(R.id.tvCoords)

        // Atrás
        ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Configurar lista de categorías típicas de mercado
        val categorias = listOf(
            "Lácteos",
            "Frutas",
            "Verduras",
            "Carnes",
            "Abarrotes",
            "Panadería",
            "Bebidas",
            "Aseo del hogar",
            "Higiene personal",
            "Cereales y granos",
            "Enlatados",
            "Snacks y dulces",
            "Mascotas",
            "Otros"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, categorias)
        actvCategoria.setAdapter(adapter)

        // Cantidad
        tvCantidad.text = cantidad.toString()
        btnMenos.setOnClickListener {
            if (cantidad > 1) {
                cantidad--
                tvCantidad.text = cantidad.toString()
            }
        }
        btnMas.setOnClickListener { cantidad++; tvCantidad.text = cantidad.toString() }

        // Cámara
        btnTomarFoto.setOnClickListener { ensureCameraThenOpen() }
        ivFoto.setOnClickListener { ensureCameraThenOpen() }

        // Mapa
        btnElegirEnMapa.setOnClickListener {
            pickOnMap.launch(Intent(this, MapPickerActivity::class.java))
        }

        // Guardar producto
        btnGuardar.setOnClickListener {
            val nombre = etNombre.text?.toString()?.trim().orEmpty()
            val marca = etMarca.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: "Manual"
            val categoria = actvCategoria.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: "Otros"
            val precioTxt = etPrecio.text?.toString()?.trim().orEmpty()

            if (nombre.isEmpty()) {
                (etNombre.parent.parent as? TextInputLayout)?.error = "Requerido"
                return@setOnClickListener
            } else {
                (etNombre.parent.parent as? TextInputLayout)?.error = null
            }

            val precioLong = precioTxt.replace("[^0-9]".toRegex(), "").toLongOrNull() ?: -1L
            if (precioLong < 0) {
                (etPrecio.parent.parent as? TextInputLayout)?.error = "Precio inválido"
                return@setOnClickListener
            } else {
                (etPrecio.parent.parent as? TextInputLayout)?.error = null
            }

            val storeName = etNombreTienda.text?.toString()?.trim().orEmpty()
                .takeIf { it.isNotEmpty() }
            val address = etDireccion.text?.toString()?.trim().orEmpty()
                .takeIf { it.isNotEmpty() }

            val data = Intent().apply {
                putExtra("nombre", nombre)
                putExtra("marca", marca)
                putExtra("categoria", categoria)
                putExtra("precio", precioLong)
                putExtra("cantidad", cantidad)
                putExtra("imageUri", photoUri?.toString())
                putExtra("lat", selLat ?: Double.NaN)
                putExtra("lng", selLng ?: Double.NaN)
                putExtra("address", address ?: "")
                putExtra("storeName", storeName ?: "")
            }
            setResult(RESULT_OK, data)
            finish()
        }
    }

    /* ---------------- Cámara ---------------- */

    private fun ensureCameraThenOpen() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) openCamera() else reqCamera.launch(Manifest.permission.CAMERA)
    }

    private fun openCamera() {
        val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES) ?: run {
            snack("No se pudo acceder a Pictures")
            return
        }
        val file = File(dir, "MANUAL_${System.currentTimeMillis()}.jpg")
        photoUri = try {
            FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            snack("Error FileProvider: ${e.message}")
            null
        }
        photoUri ?: return
        takePicture.launch(photoUri)
    }

    /* ---------------- Utils ---------------- */

    private fun snack(msg: String) =
        Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG).show()
}

