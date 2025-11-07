package com.example.proyecto_movil

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.text.NumberFormat
import java.util.Locale

class CompararPreciosActivity : AppCompatActivity() {

    private lateinit var adapter: CompararAdapter
    private lateinit var rv: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var etQuery: TextInputEditText

    private val nf = NumberFormat.getCurrencyInstance(Locale("es", "CO"))

    // Fuente simple para demo (puedes traerla de donde gustes)
    private val catalogo = listOf(
        CatalogProduct("Leche Entera", "Alpina", "Lácteos", 3200),
        CatalogProduct("Leche Deslactosada", "Alquería", "Lácteos", 3500),
        CatalogProduct("Leche Light", "Colanta", "Lácteos", 3300),
        CatalogProduct("Leche Descremada", "Colanta", "Lácteos", 3100),
        CatalogProduct("Leche Entera", "Alquería", "Lácteos", 3150),
        CatalogProduct("Pan tajado", "Bimbo", "Panadería", 5200),
        CatalogProduct("Jugo Naranja 1L", "Del Valle", "Bebidas", 4800)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comparar_precios)

        // Inset para no quedar bajo la cámara
        val root = findViewById<View>(R.id.rootComparar)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            v.setPadding(v.paddingLeft, top, v.paddingRight, v.paddingBottom)
            insets
        }

        // Hooks
        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        etQuery = findViewById(R.id.etQuery)
        rv = findViewById(R.id.recyclerComparar)
        tvEmpty = findViewById(R.id.tvEmpty)
        fab = findViewById(R.id.fabCompare)

        adapter = CompararAdapter(
            format = nf,
            onMaxSelectionReached = {
                Snackbar.make(rv, "Máximo 3 productos", Snackbar.LENGTH_SHORT).show()
            },
            onSelectionChanged = { count ->
                if (count >= 2) {
                    fab.text = "Comparar ($count)"
                    fab.show()
                } else {
                    fab.hide()
                }
            }
        )

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // Prefill si viene con "query" en el intent
        val qInicial = (intent.getStringExtra("query") ?: "").trim()
        etQuery.setText(qInicial)
        filtrar(qInicial)

        etQuery.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrar(s?.toString() ?: "")
            }
        })

        fab.setOnClickListener {
            val sel = adapter.getSelected()
            val i = Intent(this, ComparacionActivity::class.java)
            i.putExtra("items", ArrayList(sel)) // Serializable
            startActivity(i)
        }
    }

    private fun filtrar(q: String) {
        val query = q.trim().lowercase()
        val res = if (query.isEmpty()) {
            emptyList()
        } else {
            catalogo.filter {
                it.nombre.lowercase().contains(query)
                        || it.marca.lowercase().contains(query)
                        || it.categoria.lowercase().contains(query)
            }.sortedByDescending { it.precio } // ejemplo de orden
        }
        tvEmpty.visibility = if (res.isEmpty()) View.VISIBLE else View.GONE
        adapter.submitList(res)
        adapter.clearSelection()
    }
}

