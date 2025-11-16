package com.example.proyecto_movil

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.text.NumberFormat

/**
 * Adapter para búsqueda con stepper – 0 +.
 * Usa el layout: R.layout.item_producto_busqueda
 */
class ProductosAdapter(
    private val onSumar: (CatalogProduct) -> Unit,
    private val onRestar: (CatalogProduct) -> Unit,
    private val getCantidad: (CatalogProduct) -> Int,
    private val format: NumberFormat,
    private val onItemClick: (CatalogProduct) -> Unit // 👈 nuevo callback
) : ListAdapter<CatalogProduct, ProductosAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<CatalogProduct>() {
        override fun areItemsTheSame(o: CatalogProduct, n: CatalogProduct) =
            o.nombre == n.nombre && o.marca == n.marca
        override fun areContentsTheSame(o: CatalogProduct, n: CatalogProduct) = o == n
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvNombre: TextView = v.findViewById(R.id.tvNombre)
        val tvMarcaCategoria: TextView = v.findViewById(R.id.tvMarcaCategoria)
        val tvPrecio: TextView = v.findViewById(R.id.tvPrecio)
        val tvCantidad: TextView = v.findViewById(R.id.tvCantidad)
        val btnMas: MaterialButton = v.findViewById(R.id.btnMas)
        val btnMenos: MaterialButton = v.findViewById(R.id.btnMenos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_producto_busqueda, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val p = getItem(pos)
        h.tvNombre.text = p.nombre
        h.tvMarcaCategoria.text = "${p.marca} • ${p.categoria}"
        h.tvPrecio.text = format.format(p.precio)
        h.tvCantidad.text = getCantidad(p).toString()

        // Limpia íconos automáticos del tema Material
        h.btnMas.icon = null
        h.btnMenos.icon = null

        // Acciones del stepper
        h.btnMas.setOnClickListener { onSumar(p) }
        h.btnMenos.setOnClickListener { onRestar(p) }

        // 👇 NUEVO: detectar clic sobre todo el item (para agregar desde búsqueda)
        h.itemView.setOnClickListener {
            onItemClick(p)
        }
    }
}

