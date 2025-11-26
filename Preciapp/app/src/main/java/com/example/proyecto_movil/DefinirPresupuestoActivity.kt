package com.example.proyecto_movil

import android.content.Context
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.text.NumberFormat
import java.util.Locale

class DefinirPresupuestoActivity : AppCompatActivity() {

    private val prefsName = "preciapp_prefs"
    private val keyPresupuesto = "presupuesto_total"

    private lateinit var editTextPresupuesto: EditText
    private lateinit var buttonConfirmar: MaterialButton
    private lateinit var backButton: ImageView   // ← CORREGIDO

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val budgetRef by lazy { FirebaseRefs.currentBudgetRef() }

    private var budgetListener: ValueEventListener? = null
    private val nfCOP: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("es", "CO"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_definir_presupuesto)

        editTextPresupuesto = findViewById(R.id.editTextPresupuesto)
        buttonConfirmar = findViewById(R.id.buttonConfirmarPresupuesto)
        backButton = findViewById(R.id.backButton)   // ← CORRECTO

        // Cache local de respaldo offline
        val cache = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getLong(keyPresupuesto, 0L)

        if (cache > 0) {
            editTextPresupuesto.setText(nfCOP.format(cache))
            editTextPresupuesto.setSelection(editTextPresupuesto.text?.length ?: 0)
        }

        // Guardar al presionar Done
        editTextPresupuesto.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                guardarPresupuestoEnFirebase(editTextPresupuesto)
                true
            } else false
        }

        buttonConfirmar.setOnClickListener {
            guardarPresupuestoEnFirebase(editTextPresupuesto)
        }

        backButton.setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()

        if (auth.currentUser == null) return

        budgetListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {

                val amount = when {
                    snap.child("amount").exists() ->
                        snap.child("amount").getValue(Double::class.java) ?: 0.0
                    else ->
                        snap.child("amount").getValue(Long::class.java)?.toDouble() ?: 0.0
                }

                if (amount > 0.0) {
                    val entero = amount.toLong()

                    editTextPresupuesto.setText(nfCOP.format(entero))
                    editTextPresupuesto.setSelection(editTextPresupuesto.text?.length ?: 0)

                    getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                        .edit().putLong(keyPresupuesto, entero).apply()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        budgetRef.addValueEventListener(budgetListener!!)
    }

    override fun onStop() {
        super.onStop()
        budgetListener?.let { budgetRef.removeEventListener(it) }
    }

    private fun guardarPresupuestoEnFirebase(editText: EditText) {

        val crudo = editText.text?.toString()?.trim().orEmpty()

        if (crudo.isEmpty()) {
            Toast.makeText(this, "Ingrese un valor", Toast.LENGTH_SHORT).show()
            return
        }

        val normalizado = normalizarNumeroCOP(crudo)
        val valor = normalizado.toLongOrNull() ?: 0L

        if (valor <= 0L) {
            Toast.makeText(this, "Ingrese un valor mayor a 0", Toast.LENGTH_SHORT).show()
            return
        }

        val data = mapOf(
            "amount" to valor.toDouble(),
            "updatedAt" to System.currentTimeMillis()
        )

        budgetRef.setValue(data)
            .addOnSuccessListener {
                getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .edit().putLong(keyPresupuesto, valor).apply()

                Toast.makeText(this, "Presupuesto guardado", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "No se pudo guardar: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun normalizarNumeroCOP(input: String): String {

        val txt = input.replace(Regex("[^0-9,\\.]"), "")

        val lastSep = txt.indexOfLast { it == ',' || it == '.' }

        return if (lastSep >= 0 && lastSep == txt.length - 3) {
            txt.substring(0, lastSep).replace(Regex("[^0-9]"), "")
        } else {
            txt.replace(Regex("[^0-9]"), "")
        }
    }
}
