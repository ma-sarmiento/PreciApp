package com.example.proyecto_movil

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.google.firebase.database.*
import java.text.NumberFormat
import java.util.Locale

class CompararPreciosActivity : AppCompatActivity() {

    private lateinit var adapter: CompararAdapter
    private lateinit var rv: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var etQuery: TextInputEditText

    private val nf = NumberFormat.getCurrencyInstance(Locale("es", "CO"))

    // Catálogo cargado desde Firebase (global)
    private val catalogoLocal = mutableListOf<CatalogProduct>()

    // Referencia a la rama "catalog" de la RTDB
    private lateinit var catalogRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comparar_precios)

        // Insets para no quedar debajo de la cámara
        val root = findViewById<View>(R.id.rootComparar)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            v.setPadding(v.paddingLeft, top, v.paddingRight, v.paddingBottom)
            insets
        }

        // Firebase (catálogo global)
        catalogRef = FirebaseDatabase
            .getInstance("https://preciapp-6b298-default-rtdb.firebaseio.com/")
            .getReference("catalog")

        // Hooks UI
        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        etQuery = findViewById(R.id.etQuery)
        rv = findViewById(R.id.recyclerComparar)
        tvEmpty = findViewById(R.id.tvEmpty)
        fab = findViewById(R.id.fabCompare)

        // Adapter: NO mostrar precio en la lista de búsqueda
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
            },
            showPrice = false // 👈 clave: ocultar precio en el listado de búsqueda
        )

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // Buscar mientras escribe
        etQuery.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrarCatalogo(s?.toString().orEmpty())
            }
        })

        // Acción del FAB → abrir ComparacionActivity
        fab.setOnClickListener {
            val seleccionados = adapter.getSelected()
            if (seleccionados.size < 2) {
                Snackbar.make(rv, "Selecciona al menos 2 productos", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val i = Intent(this, ComparacionActivity::class.java)
            i.putExtra("items", ArrayList(seleccionados))
            startActivity(i)
        }

        // Cargar catálogo desde Firebase
        cargarCatalogoDesdeFirebase()
    }

    private fun cargarCatalogoDesdeFirebase() {
        tvEmpty.visibility = View.VISIBLE
        tvEmpty.text = "Cargando productos…"

        catalogoLocal.clear()

        catalogRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                catalogoLocal.clear()

                for (child in snapshot.children) {
                    // Acepta llaves en inglés o español (por si algo quedó mezclado)
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
                    val storeName = child.child("storeName").getValue(String::class.java)

                    val prod = CatalogProduct(
                        nombre = nombre,
                        marca = marca,
                        categoria = categoria,
                        precio = precio,
                        imageUri = imageUri,
                        lat = null,
                        lng = null,
                        address = null,
                        storeName = storeName,
                        source = child.child("source").getValue(String::class.java)
                    )

                    catalogoLocal.add(prod)
                }

                if (catalogoLocal.isEmpty()) {
                    tvEmpty.text = "No hay productos en el catálogo."
                } else {
                    tvEmpty.text = "Escribe para buscar productos por nombre, marca o categoría."
                }

                // Si ya había texto escrito, volvemos a filtrar con lo que esté en el EditText
                val q = etQuery.text?.toString().orEmpty()
                filtrarCatalogo(q)
            }

            override fun onCancelled(error: DatabaseError) {
                tvEmpty.text = "Error cargando catálogo."
            }
        })
    }

    private fun filtrarCatalogo(texto: String) {
        val q = texto.trim().lowercase()

        val resultados =
            if (q.isEmpty()) {
                emptyList()
            } else {
                catalogoLocal.filter {
                    it.nombre.lowercase().contains(q) ||
                            it.marca.lowercase().contains(q) ||
                            it.categoria.lowercase().contains(q)
                }
            }

        tvEmpty.visibility = if (resultados.isEmpty()) View.VISIBLE else View.GONE
        tvEmpty.text =
            if (q.isEmpty())
                "Escribe para buscar productos por nombre, marca o categoría."
            else
                "No se encontraron coincidencias."

        adapter.submitList(resultados)
        adapter.clearSelection()
    }
}
