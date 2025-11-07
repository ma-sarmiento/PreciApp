package com.example.proyecto_movil

import java.io.Serializable

data class CatalogProduct(
    val nombre: String,
    val marca: String,
    val categoria: String,
    val precio: Long,
    val imageUri: String? = null
) : Serializable
