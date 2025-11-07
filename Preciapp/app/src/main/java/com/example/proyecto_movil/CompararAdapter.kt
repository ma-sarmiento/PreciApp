package com.example.proyecto_movil

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import java.text.NumberFormat

class CompararAdapter(
    private val format: NumberFormat,
    private val onMaxSelectionReached: () -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<CatalogProduct, CompararAdapter.VH>(Diff()) {

    private val selected = LinkedHashSet<CatalogProduct>()

    /** Devuelve los productos seleccionados */
    fun getSelected(): List<CatalogProduct> = selected.toList()

    /** Limpia la selección (útil cuando se hace nueva búsqueda) */
    fun clearSelection() {
        selected.clear()
        notifyDataSetChanged()
        onSelectionChanged(selected.size)
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: MaterialCardView = v.findViewById(R.id.card)
        val tvNombre: TextView = v.findViewById(R.id.tvNombre)
        val tvMarca: TextView = v.findViewById(R.id.tvMarca)
        val tvPrecio: TextView = v.findViewById(R.id.tvPrecio)
        val cb: MaterialCheckBox = v.findViewById(R.id.cbSelect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_producto_comparar, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val p = getItem(position)

        // Asignar datos del producto
        h.tvNombre.text = p.nombre
        h.tvMarca.text = "${p.marca} · ${p.categoria}"
        h.tvPrecio.text = format.format(p.precio)

        // Verificar si está seleccionado
        val isSel = selected.contains(p)
        h.cb.isChecked = isSel

        // Cambiar borde al estar seleccionado
        h.card.strokeWidth = if (isSel) 3 else 0
        h.card.strokeColor = 0xFF6B46C1.toInt() // morado

        // Click para alternar selección
        h.itemView.setOnClickListener {
            if (isSel) {
                selected.remove(p)
            } else {
                if (selected.size >= 3) {
                    onMaxSelectionReached()
                    return@setOnClickListener
                }
                selected.add(p)
            }
            notifyItemChanged(h.bindingAdapterPosition)
            onSelectionChanged(selected.size)
        }
    }

    private class Diff : DiffUtil.ItemCallback<CatalogProduct>() {
        override fun areItemsTheSame(a: CatalogProduct, b: CatalogProduct): Boolean =
            a.nombre.equals(b.nombre, true) && a.marca == b.marca

        override fun areContentsTheSame(a: CatalogProduct, b: CatalogProduct): Boolean = a == b
    }
}

