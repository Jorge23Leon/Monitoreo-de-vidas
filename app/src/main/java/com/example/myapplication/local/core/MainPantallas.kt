package com.example.myapplication.local.core

enum class PantallaActual {

    LOGIN,
    CARGANDO_SESION,
    REGISTRO,
    RECUPERAR_PASSWORD,
    INFORMACION,
    CONTACTO,

    SELECCION_PARENT_CIA,
    SELECCION_CIA,

    MODULOS_TRABAJO,

    // =====================================================
    // MONITOREO FITOSANITARIO
    // =====================================================

    FILTROS_MONITOREO,
    LISTA_MONITOREOS,
    MAPA_MONITOREO,
    REGISTRO_PUNTO_MONITOREO,
    REPORTE_MONITOREO,

    // =====================================================
    // ASPERSIÓN
    // =====================================================

    ASPERSION_LISTA,
    ASPERSION_DETALLE,
    ASPERSION_MAPA,

    // =====================================================
    // NDVI
    // =====================================================

    NDVI_LISTA,
    NDVI_DETALLE,
    NDVI_MAPA,

    // =====================================================
    // PERFIL
    // =====================================================

    PERFIL_USUARIO,

    // =====================================================
    // ADMIN
    // =====================================================

    ADMIN_HOME,
    ADMIN_MONITOREOS,
    ADMIN_CATALOGOS,
    ADMIN_GESTION_AGRICOLA
}