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

    /* ✅ Catálogo PRIVADO del usuario (para productos manuales persistentes del usuario) */
    fun myCatalogRef(): DatabaseReference = userRoot().child("myCatalog")

    /** Clave estable para un producto con nombre+marca válida para RTDB. */
    fun productKey(nombre: String, marca: String): String =
        (nombre.trim() + "|" + marca.trim()).lowercase()
            .replace("[.#$\\[\\]/]".toRegex(), "_")

    /** Guarda/actualiza un producto en el catálogo privado del usuario. */
    fun saveMyCatalogProduct(p: CatalogProduct, onDone: (() -> Unit)? = null) {
        if (uid().isNullOrBlank()) { onDone?.invoke(); return }
        val key = productKey(p.nombre, p.marca)
        val node = myCatalogRef().child(key)
        val map = mapOf(
            "nombre"   to p.nombre,
            "marca"    to p.marca,
            "categoria" to p.categoria,
            "precio"   to p.precio,
            "imageUri" to p.imageUri,
            "source"   to "manual"
        )
        node.updateChildren(map).addOnCompleteListener { onDone?.invoke() }
    }

    /* -------------------- Utilidades listas -------------------- */
    /** Garantiza que exista una lista actual. Devuelve el ref a items/ de esa lista. */
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
            // Cuando conozcas el listId actual puedes hacer: itemsRef(listId).keepSynced(true)
            myCatalogRef().keepSynced(true)
        } catch (_: Throwable) {}
    }

    /* -------------------- Seed catálogo (desactivado) -------------------- */
    fun seedCatalogIfEmpty(showToasts: Boolean = false) {
        // no-op (ya tienes catálogo en la base)
    }

    /* (opcional) prueba de escritura – ya no se usa en producción */
    fun testWrite(onResult: (Boolean, String?) -> Unit) {
        val ref = db().reference.child("__ping").push()
        ref.setValue(mapOf("t" to System.currentTimeMillis()))
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }
}


