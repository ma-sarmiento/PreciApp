package com.example.proyecto_movil

import java.io.Serializable

data class CatalogProduct(
    val nombre: String = "",
    val marca: String = "",
    val categoria: String = "",
    val precio: Long = 0,
    val imageUri: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val address: String? = null,
    val storeName: String? = null,
    val source: String? = null
) : Serializable
