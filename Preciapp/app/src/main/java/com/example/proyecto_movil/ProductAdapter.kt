package com.example.proyecto_movil

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale

class ProductAdapter(
    private val items: MutableList<Product>,
    private val onDeleteAt: (Int) -> Unit,          // 🗑️ callback para eliminar en Firebase
    private val onItemClick: (Product) -> Unit      // 👈 nuevo callback para agregar al carrito
) : RecyclerView.Adapter<ProductAdapter.VH>() {

    private val nf = NumberFormat.getCurrencyInstance(Locale("es", "CO"))

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.product_name_textview)
        val tvPrice: TextView = itemView.findViewById(R.id.product_price_textview)
        val ivDelete: ImageView = itemView.findViewById(R.id.delete_icon)
        val ivProduct: ImageView = itemView.findViewById(R.id.product_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        // 🏷️ Nombre y precio
        holder.tvName.text = item.name
        holder.tvPrice.text = nf.format(item.price)
        holder.itemView.setOnClickListener { onItemClick(item) }

        // 🖼️ Imagen o placeholder
        if (!item.imageUri.isNullOrEmpty()) {
            try {
                holder.ivProduct.setImageURI(Uri.parse(item.imageUri))
            } catch (_: Exception) {
                holder.ivProduct.setImageResource(R.drawable.ic_barcode)
            }
        } else {
            holder.ivProduct.setImageResource(R.drawable.ic_barcode)
        }

        // 🗑️ Eliminar producto
        holder.ivDelete.setOnClickListener {
            onDeleteAt(holder.bindingAdapterPosition)
        }

        // 👆 Click general → agregar a carrito (mantiene storeName)
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    /** Agregar un producto localmente (no se usa mucho en esta pantalla) */
    fun addItem(p: Product) {
        items.add(p)
        notifyItemInserted(items.lastIndex)
    }

    /** Quitar producto localmente (ya eliminado en base) */
    fun removeAt(position: Int) {
        if (position in items.indices) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}
