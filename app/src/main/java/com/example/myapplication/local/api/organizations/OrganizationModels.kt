package com.example.myapplication.local.api.organizations

data class CiaPadreApiItem(
    val extId: String,
    val name: String,
    val slug: String,
    val description: String?
)

data class CiaHijaApiItem(
    val extId: String,
    val name: String,
    val slug: String,
    val description: String?,
    val parentExtId: String,
    val parent: CiaPadreApiItem
)

sealed class ResultadoCiasApi {
    data class Exito(
        val ciasHijas: List<CiaHijaApiItem>
    ) : ResultadoCiasApi()

    data class Error(
        val mensaje: String
    ) : ResultadoCiasApi()
}