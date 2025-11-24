package com.example.proyecto_movil

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.proyecto_movil.databinding.ActivityIaBinding

class IAActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIaBinding

    private val promptBase = """
Eres la IA nutricional de PreciApp. Tu función es analizar los productos reales
que el usuario tiene actualmente en su lista de compras.

Con base en la lista:
1. Arma una receta sencilla.
2. Si no es posible, crea un snack compatible (1–3 ítems).
3. Incluye calorías aproximadas (rango general).

Formato:
1. Resultado
2. Cómo se combinan
3. Calorías estimadas
""".trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ya tienes tu propio encabezado, no usamos la ActionBar
        supportActionBar?.hide()

        binding = ActivityIaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d("IA_DEBUG", "IAActivity iniciada correctamente")

        // Botón atrás
        binding.btnBackIA.setOnClickListener {
            Log.d("IA_DEBUG", "CLICK en Back")
            finish()
        }

        // Botón generar recomendación
        binding.btnGenerar.setOnClickListener {
            Log.d("IA_DEBUG", "CLICK en Generar")
            binding.txtResultado.text = "Generando recomendación..."
            cargarProductosYConsultarIA()
        }
    }

    private fun cargarProductosYConsultarIA() {

        // Usa tu helper FirebaseRefs para obtener la lista actual
        FirebaseRefs.currentItemsRefAsync { ref, listId ->

            Log.d("IA_DEBUG", "Consultando lista de productos: $listId")

            ref.get().addOnSuccessListener { snapshot ->

                if (!snapshot.exists()) {
                    binding.txtResultado.text = "No tienes productos en tu lista."
                    return@addOnSuccessListener
                }

                val listaProductos = mutableListOf<String>()

                for (item in snapshot.children) {
                    val p = item.child("product")

                    val nombre = p.child("nombre").getValue(String::class.java).orEmpty()
                    val categoria = p.child("categoria").getValue(String::class.java).orEmpty()
                    val marca = p.child("marca").getValue(String::class.java).orEmpty()
                    val precio = p.child("precio").getValue(Long::class.java)?.toString().orEmpty()

                    if (nombre.isBlank()) continue

                    listaProductos.add("$nombre ($categoria, $marca, $precio)")
                }

                if (listaProductos.isEmpty()) {
                    binding.txtResultado.text = "No hay productos válidos en la lista."
                    return@addOnSuccessListener
                }

                val promptFinal = buildString {
                    appendLine(promptBase)
                    appendLine()
                    appendLine("Productos del usuario:")
                    listaProductos.forEach { appendLine("- $it") }
                }

                Log.d("IA_DEBUG", "PROMPT enviado a IA:\n$promptFinal")

                IAService.generarRespuesta(promptFinal) { respuesta ->

                    runOnUiThread {
                        if (respuesta == null) {
                            Log.e("IA_DEBUG", "❌ Respuesta nula desde IA")
                            binding.txtResultado.text = "Ocurrió un error consultando la IA."

                            Toast.makeText(
                                this,
                                "Error al consultar IA (API Key o conexión).",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Log.d("IA_DEBUG", "Respuesta IA OK:\n$respuesta")
                            binding.txtResultado.text = respuesta
                        }
                    }
                }

            }.addOnFailureListener { e ->
                Log.e("IA_DEBUG", "Error leyendo productos: ${e.message}", e)
                binding.txtResultado.text = "Error leyendo productos."
            }
        }
    }
}
