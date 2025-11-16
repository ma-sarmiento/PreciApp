package com.example.proyecto_movil

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import java.text.NumberFormat
import java.util.Locale

class ListaProductosActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView

    // ⬇️ Tu adapter debe tener un callback para la basurita: onDeleteAt(pos)
    // (Si aún no lo tiene, agrégalo en ProductAdapter)
    private lateinit var adapter: ProductAdapter

    // Datos mostrados
    private val productosLocal = mutableListOf<Product>()

    // Claves de Firebase alineadas por posición con productosLocal
    private val keysPorPosicion = mutableListOf<String>()

    // RTDB
    private var itemsRef: DatabaseReference? = null
    private var itemsListener: ValueEventListener? = null

    // Formateador de moneda COP
    private val nf = NumberFormat.getCurrencyInstance(Locale("es", "CO"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lista_productos)

        recycler = findViewById(R.id.recyclerLista)
        recycler.layoutManager = LinearLayoutManager(this)

        // ⬇️ Pasamos el callback para borrar por posición
        adapter = ProductAdapter(
            items = productosLocal,
            onDeleteAt = { pos -> borrarItemEnFirebase(pos) },
            onItemClick = { product ->
                // ✅ Cuando el usuario toca un producto del listado o buscador:
                val catalogProduct = CatalogProduct(
                    nombre = product.name,
                    marca = "Genérico",  // puedes reemplazar si tienes un campo brand
                    categoria = "Sin categoría",
                    precio = product.price.toLong(),
                    imageUri = product.imageUri,
                    storeName = "Tienda Genérica", // 👈 o usa product.storeName si lo tienes
                    source = "catalog_search"
                )

                FirebaseRefs.currentItemsRefAsync { ref, _ ->
                    // agrega al carrito o incrementa cantidad si ya existe
                    val push = ref.push()
                    val itemMap = mapOf(
                        "product" to mapOf(
                            "nombre" to catalogProduct.nombre,
                            "marca" to catalogProduct.marca,
                            "categoria" to catalogProduct.categoria,
                            "precio" to catalogProduct.precio,
                            "imageUri" to catalogProduct.imageUri,
                            "storeName" to catalogProduct.storeName,
                            "source" to catalogProduct.source
                        ),
                        "qty" to 1
                    )
                    push.setValue(itemMap)
                }
            }
        )
        recycler.adapter = adapter

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()

        // Asegura lista actual y obtén /users/{uid}/lists/{id}/items
        FirebaseRefs.currentItemsRefAsync { ref, _ ->
            // Limpia listeners previos por si onStart corre de nuevo
            itemsListener?.let { itemsRef?.removeEventListener(it) }
            itemsRef = ref

            itemsListener = object : ValueEventListener {
                override fun onDataChange(ds: DataSnapshot) {
                    productosLocal.clear()
                    keysPorPosicion.clear()

                    // Cada hijo de items/:
                    // { product:{nombre, precio, imageUri, ...}, qty:N }
                    for (item in ds.children) {
                        val key = item.key ?: continue
                        val qty = (item.child("qty").getValue(Int::class.java) ?: 0).coerceAtLeast(0)

                        val prod = item.child("product")
                        val nombre = prod.child("nombre").getValue(String::class.java).orEmpty()
                        val precio = prod.child("precio").getValue(Long::class.java) ?: 0L
                        val imageUri = prod.child("imageUri").getValue(String::class.java)

                        if (nombre.isBlank()) continue

                        // Subtotal = precio unitario * cantidad
                        val subtotal = precio * qty
                        val displayName = if (qty > 1) "$nombre (x$qty)" else nombre

                        productosLocal.add(
                            Product(
                                name = displayName,
                                // El adapter formatea; guardamos valor numérico
                                price = subtotal.toDouble(),
                                imageUri = imageUri
                            )
                        )
                        keysPorPosicion.add(key)
                    }

                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    // opcional: log/Toast
                }
            }

            ref.addValueEventListener(itemsListener!!)
        }
    }

    override fun onStop() {
        super.onStop()
        itemsListener?.let { listener ->
            itemsRef?.removeEventListener(listener)
        }
    }

    /* ----------- Borrar ítem en Firebase por posición ----------- */
    private fun borrarItemEnFirebase(pos: Int) {
        val key = keysPorPosicion.getOrNull(pos) ?: return
        val ref = itemsRef ?: return
        ref.child(key).removeValue()
        // No tocamos productosLocal aquí: el listener refresca la UI.
    }
}
