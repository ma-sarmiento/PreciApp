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
    private val onDeleteAt: (Int) -> Unit   // ✅ callback para eliminar en Firebase
) : RecyclerView.Adapter<ProductAdapter.VH>() {

    // Formateador de moneda COP
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

        // 🏷️ Nombre del producto
        holder.tvName.text = item.name

        // 💰 Precio formateado en COP (ej: $ 6.400)
        holder.tvPrice.text = nf.format(item.price)

        // 🖼️ Imagen del producto o placeholder
        if (!item.imageUri.isNullOrEmpty()) {
            try {
                holder.ivProduct.setImageURI(Uri.parse(item.imageUri))
            } catch (_: Exception) {
                holder.ivProduct.setImageResource(R.drawable.ic_barcode)
            }
        } else {
            holder.ivProduct.setImageResource(R.drawable.ic_barcode)
        }

        // 🗑️ Al hacer clic en la basurita → elimina de Firebase y de la lista
        holder.ivDelete.setOnClickListener {
            onDeleteAt(holder.bindingAdapterPosition)
        }
    }

    override fun getItemCount(): Int = items.size

    /** Agregar un producto localmente (no se usa mucho en esta pantalla) */
    fun addItem(p: Product) {
        items.add(p)
        notifyItemInserted(items.lastIndex)
    }

    /** Quitar producto localmente sin tocar Firebase (sólo si ya se eliminó en base) */
    fun removeAt(position: Int) {
        if (position in items.indices) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}
