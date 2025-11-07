package com.example.proyecto_movil

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.google.android.material.card.MaterialCardView
import java.text.NumberFormat
import java.util.Locale

class ComparacionActivity : AppCompatActivity() {

    private val nf = NumberFormat.getCurrencyInstance(Locale("es", "CO"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comparacion)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Recupera productos seleccionados (Serializable desde CompararPreciosActivity)
        @Suppress("UNCHECKED_CAST")
        val items = (intent.getSerializableExtra("items") as? ArrayList<CatalogProduct>)
            ?: arrayListOf()

        // Encabezados (tarjetas de cada producto)
        val header = findViewById<LinearLayout>(R.id.headerContainer)
        header.removeAllViews()
        items.forEach { p -> header.addView(productHeaderCard(p)) }

        // Tabla comparativa
        val rowsContainer = findViewById<LinearLayout>(R.id.rowsContainer)
        rowsContainer.removeAllViews()

        // Cabecera de columnas: ahora dice “Producto” y sin fondo extra
        rowsContainer.addView(rowDivider())
        rowsContainer.addView(rowHeader("Producto", items.map { "${it.nombre}\n${it.marca}" }))

        // Filas de atributos
        atributos(items).forEach { (nombreAttr, valores) ->
            rowsContainer.addView(rowDivider())
            rowsContainer.addView(row(nombreAttr, valores))
        }
        rowsContainer.addView(rowDivider())
    }

    /** Devuelve pares (atributo, valoresPorColumna) en el orden de los items. */
    private fun atributos(items: List<CatalogProduct>): List<Pair<String, List<String>>> {
        val precio = items.map { nf.format(it.precio) }
        val categoria = items.map { it.categoria }
        val marca = items.map { it.marca }

        return listOf(
            "Precio" to precio,
            "Categoría" to categoria,
            "Marca" to marca
        )
    }

    /** Tarjeta superior de cada producto seleccionado. */
    private fun productHeaderCard(p: CatalogProduct): View {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                220.dp, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 12.dp, 0) }
            radius = 24f
            cardElevation = 8f
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp)
        }

        val img = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 110.dp
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_barcode) // placeholder
            p.imageUri?.let {
                try { setImageURI(Uri.parse(it)) } catch (_: Exception) {}
            }
            clipToOutline = true
        }

        val tvName = TextView(this).apply {
            text = p.nombre
            setTextColor(0xFF101828.toInt())
            setTypeface(typeface, Typeface.BOLD)
            textSize = 14f
            setPadding(0, 8.dp, 0, 2.dp)
            maxLines = 2
        }

        val tvBrand = TextView(this).apply {
            text = p.marca
            setTextColor(0xFF6B7280.toInt())
            textSize = 12f
        }

        val tvPrice = TextView(this).apply {
            text = nf.format(p.precio)
            setTextColor(0xFF101828.toInt())
            setTypeface(typeface, Typeface.BOLD)
            textSize = 16f
            setPadding(0, 8.dp, 0, 0)
        }

        col.addView(img)
        col.addView(tvName)
        col.addView(tvBrand)
        col.addView(tvPrice)
        card.addView(col)
        return card
    }

    /** Fila header: “Producto | Col1 | Col2 | …” en negrita, sin fondo. */
    private fun rowHeader(title: String, values: List<String>): View {
        val row = baseRow()
        row.addView(cell(title, bold = true))
        values.forEach { v -> row.addView(cell(v, bold = true)) }
        return row
    }

    /** Fila normal. */
    private fun row(attrName: String, values: List<String>): View {
        val row = baseRow()
        row.addView(cell(attrName, tint = 0xFF6B7280.toInt()))
        values.forEach { v -> row.addView(cell(v)) }
        return row
    }

    /** Separador fino entre filas. */
    private fun rowDivider(): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, 6.dp, 0, 6.dp) }
            setBackgroundColor(0xFFE5E7EB.toInt())
        }

    /** Contenedor horizontal de una fila. */
    private fun baseRow(): LinearLayout =
        LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            weightSum = 4f // columna “Producto” + hasta 3 productos
        }

    /** Crea una celda simple (sin fondo adicional). */
    private fun cell(
        text: String,
        bold: Boolean = false,
        tint: Int = 0xFF101828.toInt()
    ): View {
        val ll = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
            setPadding(10.dp, 8.dp, 10.dp, 8.dp)
        }

        val tv = TextView(this).apply {
            this.text = text
            setTextColor(tint)
            textSize = 13f
            gravity = Gravity.START
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

        ll.addView(tv)
        return ll
    }

    // dp helper
    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}


