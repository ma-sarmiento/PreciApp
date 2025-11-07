package com.example.proyecto_movil

object CatalogSeeder {

    fun seedIfEmpty() {
        val ref = FirebaseRefs.catalogRef()
        ref.get().addOnSuccessListener { snap ->
            if (snap.hasChildren()) return@addOnSuccessListener

            val seed: Map<String, Any> = mapOf(
                "leche_entera_alpina" to mapOf(
                    "name" to "Leche Entera",
                    "brand" to "Alpina",
                    "category" to "Lácteos",
                    "price" to 3200L
                ),
                "leche_deslactosada_alqueria" to mapOf(
                    "name" to "Leche Deslactosada",
                    "brand" to "Alquería",
                    "category" to "Lácteos",
                    "price" to 3500L
                ),
                "pan_bimbo" to mapOf(
                    "name" to "Pan tajado",
                    "brand" to "Bimbo",
                    "category" to "Panadería",
                    "price" to 5200L
                ),
                "jugo_naranja_delvalle" to mapOf(
                    "name" to "Jugo Naranja 1L",
                    "brand" to "Del Valle",
                    "category" to "Bebidas",
                    "price" to 4800L
                )
            )

            ref.updateChildren(seed)
        }
    }
}
