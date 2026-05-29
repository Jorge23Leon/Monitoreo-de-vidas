package com.example.myapplication.local.admin.catalogos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.example.myapplication.local.entities.LocalPhytostageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AdminCatalogosScreen(
    database: AppDatabase,
    nombreUsuario: String,
    rolUsuario: String,
    nombreCia: String,
    onBackClick: () -> Unit,
    onPerfilClick: () -> Unit,
    onMonitoreosClick: () -> Unit,
    onAdminClick: () -> Unit,
    onCerrarSesionClick: () -> Unit,
    onMensaje: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var cargando by remember { mutableStateOf(true) }
    var guardando by remember { mutableStateOf(false) }
    var tabActual by remember { mutableStateOf(AdminCatalogoTab.CULTIVOS) }

    var cultivos by remember { mutableStateOf<List<LocalCropCatalogEntity>>(emptyList()) }
    var catalogoFito by remember { mutableStateOf<List<LocalPhytosanitaryCatalogEntity>>(emptyList()) }
    var etapas by remember { mutableStateOf<List<LocalPhytostageEntity>>(emptyList()) }

    var mostrarDialogCultivo by remember { mutableStateOf(false) }
    var mostrarDialogFito by remember { mutableStateOf(false) }

    var cultivoEnEdicion by remember { mutableStateOf<LocalCropCatalogEntity?>(null) }
    var fitoEnEdicion by remember { mutableStateOf<LocalPhytosanitaryCatalogEntity?>(null) }

    fun recargarCatalogos() {
        coroutineScope.launch {
            cargando = true
            try {
                val datos = withContext(Dispatchers.IO) {
                    Triple(
                        database.localCropCatalogDao().getAllCrops(),
                        database.localphytosanitarycatalogDao().getAllCatalogo(),
                        database.localphytostageDao().getAllPhytostages()
                    )
                }
                cultivos = datos.first
                catalogoFito = datos.second
                etapas = datos.third
            } catch (e: Exception) {
                e.printStackTrace()
                onMensaje("Error al cargar catálogos: ${e.message}")
            } finally {
                cargando = false
            }
        }
    }

    LaunchedEffect(Unit) {
        recargarCatalogos()
    }

    fun abrirAgregarCultivo() {
        cultivoEnEdicion = null
        mostrarDialogCultivo = true
    }

    fun abrirEditarCultivo(cultivo: LocalCropCatalogEntity) {
        cultivoEnEdicion = cultivo
        mostrarDialogCultivo = true
    }

    fun abrirAgregarFito() {
        fitoEnEdicion = null
        mostrarDialogFito = true
    }

    fun abrirEditarFito(item: LocalPhytosanitaryCatalogEntity) {
        if (esSinPlagaSistemaAdmin(item)) {
            onMensaje("El registro 'Sin plaga' es del sistema y no se debe editar")
            return
        }
        fitoEnEdicion = item
        mostrarDialogFito = true
    }

    fun cerrarDialogos() {
        if (!guardando) {
            mostrarDialogCultivo = false
            mostrarDialogFito = false
            cultivoEnEdicion = null
            fitoEnEdicion = null
        }
    }

    fun ejecutarGuardarCultivo(
        nombre: String,
        variedad: String,
        codigo: String,
        descripcion: String,
        photo: String?
    ) {
        coroutineScope.launch {
            guardando = true
            try {
                val resultado = guardarCultivoAdmin(
                    database = database,
                    cultivosActuales = cultivos,
                    cultivoBase = cultivoEnEdicion,
                    nombre = nombre,
                    variedad = variedad,
                    codigo = codigo,
                    descripcion = descripcion,
                    photo = photo
                )

                onMensaje(resultado.mensaje)

                if (resultado.exito) {
                    mostrarDialogCultivo = false
                    cultivoEnEdicion = null
                    recargarCatalogos()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onMensaje("No se pudo guardar el cultivo: ${e.message}")
            } finally {
                guardando = false
            }
        }
    }

    fun ejecutarGuardarFitosanitario(
        nombre: String,
        tipo: String,
        cultivo: LocalCropCatalogEntity?,
        minRef: String,
        maxRef: String,
        descripcion: String,
        photo: String?,
        etapasTexto: String
    ) {
        coroutineScope.launch {
            guardando = true
            try {
                val resultado = guardarFitosanitarioAdmin(
                    database = database,
                    catalogoFitoActual = catalogoFito,
                    itemBase = fitoEnEdicion,
                    nombre = nombre,
                    tipo = tipo,
                    cultivo = cultivo,
                    minRef = minRef,
                    maxRef = maxRef,
                    descripcion = descripcion,
                    photo = photo,
                    etapasTexto = etapasTexto
                )

                onMensaje(resultado.mensaje)

                if (resultado.exito) {
                    mostrarDialogFito = false
                    fitoEnEdicion = null
                    recargarCatalogos()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onMensaje("No se pudo guardar el catálogo: ${e.message}")
            } finally {
                guardando = false
            }
        }
    }

    if (mostrarDialogCultivo) {
        DialogoCultivoAdmin(
            guardando = guardando,
            cultivo = cultivoEnEdicion,
            onDismiss = { cerrarDialogos() },
            onGuardar = { nombre, variedad, codigo, descripcion, photo ->
                ejecutarGuardarCultivo(nombre, variedad, codigo, descripcion, photo)
            }
        )
    }

    if (mostrarDialogFito) {
        DialogoFitosanitarioAdmin(
            guardando = guardando,
            item = fitoEnEdicion,
            cultivos = cultivos,
            etapas = etapas,
            onDismiss = { cerrarDialogos() },
            onGuardar = { nombre, tipo, cultivo, minRef, maxRef, descripcion, photo, etapasTexto ->
                ejecutarGuardarFitosanitario(
                    nombre = nombre,
                    tipo = tipo,
                    cultivo = cultivo,
                    minRef = minRef,
                    maxRef = maxRef,
                    descripcion = descripcion,
                    photo = photo,
                    etapasTexto = etapasTexto
                )
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8F1))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EncabezadoApp(
                nombreUsuario = nombreUsuario,
                rolUsuario = rolUsuario,
                onPerfilClick = onPerfilClick,
                onMonitoreosClick = onMonitoreosClick,
                onAdminClick = onAdminClick,
                onCerrarSesionClick = onCerrarSesionClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            AdminCatalogosHeroCard(
                nombreCia = nombreCia,
                totalCultivos = cultivos.size,
                totalFito = catalogoFito.size,
                onBackClick = onBackClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            AdminCatalogTabs(
                tabActual = tabActual,
                onTabChange = { tabActual = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (cargando) {
                AdminCatalogLoadingCard()
            } else {
                when (tabActual) {
                    AdminCatalogoTab.CULTIVOS -> {
                        SeccionCultivosAdmin(
                            cultivos = cultivos,
                            onAgregarCultivo = { abrirAgregarCultivo() },
                            onEditarCultivo = { cultivo -> abrirEditarCultivo(cultivo) }
                        )
                    }

                    AdminCatalogoTab.FITOSANITARIO -> {
                        SeccionFitosanitarioAdmin(
                            catalogoFito = catalogoFito,
                            cultivos = cultivos,
                            etapas = etapas,
                            onAgregarFito = { abrirAgregarFito() },
                            onEditarFito = { item -> abrirEditarFito(item) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}

@Composable
private fun AdminCatalogosHeroCard(
    nombreCia: String,
    totalCultivos: Int,
    totalFito: Int,
    onBackClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF1B5E20),
                            Color(0xFF43A047)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📚 Administrar catálogos",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 21.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Prepara cultivos, plagas y enfermedades. Las etapas ya están fijas y se seleccionan dentro de cada registro.",
                        color = Color(0xFFE8F5E9),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                OutlinedButton(
                    onClick = onBackClick,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Volver")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "CIA actual: $nombreCia",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCatalogChip("Cultivos", totalCultivos, Modifier.weight(1f))
                StatCatalogChip("Fito", totalFito, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCatalogChip(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.20f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value.toString(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(text = label, color = Color(0xFFE8F5E9), fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

