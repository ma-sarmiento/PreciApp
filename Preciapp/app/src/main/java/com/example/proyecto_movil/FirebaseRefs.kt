package com.example.proyecto_movil

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

object FirebaseRefs {

    private const val DB_URL = "https://preciapp-6b298-default-rtdb.firebaseio.com/"

    fun db(): FirebaseDatabase = FirebaseDatabase.getInstance(DB_URL)
    fun uid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    /* -------------------- Raíces -------------------- */
    fun usersRoot(): DatabaseReference = db().reference.child("users")
    private fun userRoot(): DatabaseReference = usersRoot().child(uid() ?: "_no_uid_")

    /* Perfil */
    fun profileRef(): DatabaseReference = userRoot().child("profile")

    /* Presupuesto actual */
    fun currentBudgetRef(): DatabaseReference = userRoot().child("budgets").child("current")

    /* Listas */
    fun listsRoot(): DatabaseReference = userRoot().child("lists")
    fun currentListIdRef(): DatabaseReference = listsRoot().child("currentListId")
    fun listRef(listId: String): DatabaseReference = listsRoot().child(listId)
    fun itemsRef(listId: String): DatabaseReference = listRef(listId).child("items")

    /* Catálogo GLOBAL */
    fun catalogRef(): DatabaseReference = db().reference.child("catalog")

    /* ✅ Catálogo PRIVADO del usuario */
    fun myCatalogRef(): DatabaseReference = userRoot().child("myCatalog")

    /** Clave estable (nombre|marca) para un producto. */
    fun productKey(nombre: String, marca: String): String =
        (nombre.trim() + "|" + marca.trim()).lowercase()
            .replace("[.#$\\[\\]/]".toRegex(), "_")

    /**
     * ✅ Guarda o actualiza un producto en el catálogo privado del usuario.
     * Incluye ubicación, dirección, tienda y fuente ("manual", "camera", etc.).
     */
    fun saveMyCatalogProduct(p: CatalogProduct, onDone: (() -> Unit)? = null) {
        if (uid().isNullOrBlank()) {
            onDone?.invoke(); return
        }

        val key = productKey(p.nombre, p.marca)
        val ref = myCatalogRef().child(key)

        ref.get().addOnSuccessListener { snap ->
            // Si ya existe, conserva lat/lng/address/storeName previos
            val prevLat = snap.child("lat").getValue(Double::class.java)
            val prevLng = snap.child("lng").getValue(Double::class.java)
            val prevAddr = snap.child("address").getValue(String::class.java)
            val prevStore = snap.child("storeName").getValue(String::class.java)

            val data = mutableMapOf<String, Any?>(
                "nombre" to p.nombre,
                "marca" to p.marca,
                "categoria" to p.categoria,
                "precio" to p.precio,
                "imageUri" to p.imageUri,
                "source" to (p.source ?: "manual")
            )

            // Coordenadas nuevas o conservar anteriores
            if (p.lat != null && p.lng != null && p.lat.isFinite() && p.lng.isFinite()) {
                data["lat"] = p.lat
                data["lng"] = p.lng
            } else if (prevLat != null && prevLng != null) {
                data["lat"] = prevLat
                data["lng"] = prevLng
            }

            // Dirección y tienda
            data["address"] = if (!p.address.isNullOrBlank()) p.address else prevAddr
            data["storeName"] = if (!p.storeName.isNullOrBlank()) p.storeName else prevStore

            ref.updateChildren(data).addOnCompleteListener { onDone?.invoke() }
        }.addOnFailureListener {
            onDone?.invoke()
        }
    }

    /* -------------------- Utilidades listas -------------------- */
    /** Garantiza que exista una lista actual y devuelve ref a items/ */
    fun currentItemsRefAsync(cb: (DatabaseReference, String) -> Unit) {
        val root = listsRoot()
        currentListIdRef().addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val existing = s.getValue(String::class.java)
                if (!existing.isNullOrBlank()) {
                    cb(itemsRef(existing), existing)
                    return
                }

                val newId = root.push().key!!
                val meta = mapOf(
                    "name" to "Mi lista",
                    "createdAt" to System.currentTimeMillis()
                )
                val updates = hashMapOf<String, Any?>(
                    "/$newId/meta" to meta,
                    "/currentListId" to newId
                )
                root.updateChildren(updates).addOnCompleteListener {
                    cb(itemsRef(newId), newId)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    /* -------------------- Warmup de caché (más rápido al abrir) -------------------- */
    fun warmupKeepSynced() {
        try {
            catalogRef().keepSynced(true)
            currentListIdRef().keepSynced(true)
            myCatalogRef().keepSynced(true)
        } catch (_: Throwable) {}
    }

    /* -------------------- Seed catálogo global -------------------- */
    fun seedCatalogIfEmpty(showToasts: Boolean = false) {
        val ref = catalogRef()
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) return
                val seed = mapOf(
                    "jugo_naranja_delvalle" to mapOf(
                        "name" to "Jugo Naranja 1L",
                        "brand" to "Del Valle",
                        "category" to "Bebidas",
                        "price" to 4800
                    ),
                    "leche_deslactosada_alqueria" to mapOf(
                        "name" to "Leche Deslactosada",
                        "brand" to "Alquería",
                        "category" to "Lácteos",
                        "price" to 3500
                    ),
                    "leche_entera_alpina" to mapOf(
                        "name" to "Leche Entera",
                        "brand" to "Alpina",
                        "category" to "Lácteos",
                        "price" to 3200
                    ),
                    "pan_bimbo" to mapOf(
                        "name" to "Pan Tajado",
                        "brand" to "Bimbo",
                        "category" to "Panadería",
                        "price" to 5200
                    )
                )
                ref.updateChildren(seed)
                if (showToasts) {
                    android.util.Log.i("FirebaseRefs", "✅ Catálogo global inicializado.")
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    /* -------------------- Prueba de conexión -------------------- */
    fun testWrite(onResult: (Boolean, String?) -> Unit) {
        val ref = db().reference.child("__ping").push()
        ref.setValue(mapOf("t" to System.currentTimeMillis()))
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }
}
