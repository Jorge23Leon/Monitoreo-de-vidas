package com.example.myapplication.local

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.example.myapplication.local.entities.LocalPhytostageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RegistroPuntoMonitoreoScreen(
    database: AppDatabase,
    nombreUsuario: String,
    header: LocalPhytomonitoringHeaderEntity,
    punto: LocalPhytomonitoringTargetPointEntity,
    onCancelar: () -> Unit,
    onGuardado: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var catalogo by remember {
        mutableStateOf<List<LocalPhytosanitaryCatalogEntity>>(emptyList())
    }

    var etapas by remember {
        mutableStateOf<List<LocalPhytostageEntity>>(emptyList())
    }

    var fitoSeleccionado by remember {
        mutableStateOf<LocalPhytosanitaryCatalogEntity?>(null)
    }

    val cantidadesPorEtapa = remember {
        mutableStateMapOf<String, Int>()
    }

    var observaciones by rememberSaveable {
        mutableStateOf("")
    }

    var nombreCultivo by remember {
        mutableStateOf("Cultivo no identificado")
    }

    var registrosAgregados by rememberSaveable {
        mutableStateOf(0)
    }

    var cargando by remember {
        mutableStateOf(true)
    }

    var finalizando by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(Unit) {
        cargando = true
        error = null

        try {
            val resultado = withContext(Dispatchers.IO) {
                val catalogoDb = database.localphytosanitarycatalogDao()
                    .getAllCatalogo()
                    .filterNot { item ->
                        item.name.equals("Sin plaga", ignoreCase = true)
                    }

                val cultivoDb = database.localCropCatalogDao()
                    .getCropById(header.idCrop)

                val capturasExistentes = database.localphytomonitoringcheckpointDao()
                    .getCheckpointsByTargetPoint(punto.idTargetPoint)
                    .filter { checkpoint ->
                        checkpoint.presenceStatus == 1
                    }

                val totalPlagasAgregadas = capturasExistentes
                    .map { checkpoint ->
                        checkpoint.idPhytosanitary
                    }
                    .distinct()
                    .size

                Triple(
                    catalogoDb,
                    cultivoDb?.name ?: "Cultivo no identificado",
                    totalPlagasAgregadas
                )
            }

            catalogo = resultado.first
            nombreCultivo = resultado.second
            registrosAgregados = resultado.third

        } catch (e: Exception) {
            error = "Error al cargar datos: ${e.message}"
        } finally {
            cargando = false
        }
    }

    LaunchedEffect(fitoSeleccionado?.idPhytosanitary) {
        val fito = fitoSeleccionado

        etapas = emptyList()
        cantidadesPorEtapa.clear()

        if (fito != null) {
            try {
                val etapasDb = withContext(Dispatchers.IO) {
                    database.localphytostageDao()
                        .getStagesByPhytosanitary(fito.idPhytosanitary)
                }

                etapas = etapasDb

                etapasDb.forEach { etapa ->
                    cantidadesPorEtapa[etapa.stage] = 0
                }

            } catch (e: Exception) {
                error = "Error al cargar etapas: ${e.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FBF7))
    ) {
        EncabezadoApp(
            nombreUsuario = nombreUsuario
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RegistroHeaderCard(
                punto = punto
            )

            Spacer(modifier = Modifier.height(10.dp))

            CultivoMonitoreadoCard(
                nombreCultivo = nombreCultivo
            )

            Spacer(modifier = Modifier.height(10.dp))

            InfoBox(
                text = "Registros guardados en este punto: $registrosAgregados"
            )

            Spacer(modifier = Modifier.height(14.dp))

            SectionTitle(
                icon = "🐛",
                title = "Plagas y enfermedades"
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                cargando -> {
                    InfoBox(
                        text = "Cargando plagas y enfermedades..."
                    )
                }

                error != null -> {
                    InfoBox(
                        text = error ?: "Error desconocido",
                        isError = true
                    )
                }

                catalogo.isEmpty() -> {
                    InfoBox(
                        text = "No hay plagas o enfermedades registradas.",
                        isError = true
                    )
                }

                else -> {
                    CatalogoPlagasGrid(
                        catalogo = catalogo,
                        fitoSeleccionado = fitoSeleccionado,
                        onSelected = { item ->
                            fitoSeleccionado = item
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            fitoSeleccionado?.let { fito ->
                RegistroActivoCard(
                    nombre = fito.name
                )

                Spacer(modifier = Modifier.height(14.dp))

                SectionTitle(
                    icon = "🌱",
                    title = "Fases / Etapas"
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (etapas.isEmpty()) {
                    InfoBox(
                        text = "Esta plaga/enfermedad no tiene fases registradas. Se guardará como presencia general."
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 3.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            etapas.forEach { etapa ->
                                val cantidad = cantidadesPorEtapa[etapa.stage] ?: 0

                                EtapaRowModerna(
                                    nombreEtapa = etapa.stage,
                                    cantidad = cantidad,
                                    onMenos = {
                                        val actual = cantidadesPorEtapa[etapa.stage] ?: 0
                                        cantidadesPorEtapa[etapa.stage] = maxOf(0, actual - 1)
                                    },
                                    onMas = {
                                        val actual = cantidadesPorEtapa[etapa.stage] ?: 0
                                        cantidadesPorEtapa[etapa.stage] = actual + 1
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle(
                icon = "📝",
                title = "Observaciones"
            )

            Spacer(modifier = Modifier.height(8.dp))

            ObservacionesBox(
                value = observaciones,
                onValueChange = {
                    observaciones = it
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    if (finalizando) return@Button

                    finalizando = true

                    coroutineScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                var sinPlaga = database.localphytosanitarycatalogDao()
                                    .getAllCatalogo()
                                    .firstOrNull { item ->
                                        item.name.equals("Sin plaga", ignoreCase = true)
                                    }

                                if (sinPlaga == null) {
                                    val idNuevo = database.localphytosanitarycatalogDao()
                                        .insertPhytosanitary(
                                            LocalPhytosanitaryCatalogEntity(
                                                name = "Sin plaga",
                                                type = "SIN_PLAGA",
                                                minRefValue = 0,
                                                maxRefValue = 0,
                                                description = "Punto revisado sin presencia de plagas o enfermedades",
                                                photo = null,
                                                idDefaultCrop = header.idCrop
                                            )
                                        )

                                    sinPlaga = database.localphytosanitarycatalogDao()
                                        .getPhytosanitaryById(idNuevo)
                                }

                                if (sinPlaga != null) {
                                    val checkpoint = LocalPhytomonitoringCheckpointEntity(
                                        qty = 0,
                                        presenceStatus = 0,
                                        stage = null,
                                        notes = "Punto revisado sin presencia de plagas o enfermedades",
                                        capturedAt = System.currentTimeMillis(),
                                        idTargetPoint = punto.idTargetPoint,
                                        idHeader = header.idHeader,
                                        idPhytosanitary = sinPlaga.idPhytosanitary,
                                        idLocalPlot = punto.idLocalPlot
                                    )

                                    database.localphytomonitoringcheckpointDao()
                                        .insertCheckpoint(checkpoint)
                                }

                                database.LocalPhytomonitoringTargetPointDao()
                                    .actualizarStatusPunto(
                                        idTargetPoint = punto.idTargetPoint,
                                        status = "Completado"
                                    )
                            }

                            Toast.makeText(
                                context,
                                "Punto registrado sin plagas",
                                Toast.LENGTH_SHORT
                            ).show()

                            onGuardado()

                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Error al registrar sin plaga: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            finalizando = false
                        }
                    }
                },
                enabled = !finalizando,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8BC34A),
                    disabledContainerColor = Color(0xFF9E9E9E)
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp
                )
            ) {
                Text(
                    text = if (finalizando) {
                        "Guardando..."
                    } else {
                        "🌿 Sin plaga"
                    },
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    if (finalizando) return@Button

                    val fito = fitoSeleccionado

                    if (fito == null && registrosAgregados <= 0) {
                        Toast.makeText(
                            context,
                            "Selecciona una plaga/enfermedad o presiona Sin plaga",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }

                    val registrosConCantidad = cantidadesPorEtapa
                        .filter { it.value > 0 }

                    if (fito != null && etapas.isNotEmpty() && registrosConCantidad.isEmpty()) {
                        Toast.makeText(
                            context,
                            "Captura al menos una fase con cantidad mayor a 0",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }

                    finalizando = true

                    coroutineScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                if (fito != null) {
                                    if (etapas.isEmpty()) {
                                        val checkpoint = LocalPhytomonitoringCheckpointEntity(
                                            qty = 1,
                                            presenceStatus = 1,
                                            stage = null,
                                            notes = observaciones.ifBlank { null },
                                            capturedAt = System.currentTimeMillis(),
                                            idTargetPoint = punto.idTargetPoint,
                                            idHeader = header.idHeader,
                                            idPhytosanitary = fito.idPhytosanitary,
                                            idLocalPlot = punto.idLocalPlot
                                        )

                                        database.localphytomonitoringcheckpointDao()
                                            .insertCheckpoint(checkpoint)

                                    } else {
                                        registrosConCantidad.forEach { (etapa, cantidad) ->
                                            val checkpoint = LocalPhytomonitoringCheckpointEntity(
                                                qty = cantidad,
                                                presenceStatus = 1,
                                                stage = etapa,
                                                notes = observaciones.ifBlank { null },
                                                capturedAt = System.currentTimeMillis(),
                                                idTargetPoint = punto.idTargetPoint,
                                                idHeader = header.idHeader,
                                                idPhytosanitary = fito.idPhytosanitary,
                                                idLocalPlot = punto.idLocalPlot
                                            )

                                            database.localphytomonitoringcheckpointDao()
                                                .insertCheckpoint(checkpoint)
                                        }
                                    }
                                }

                                database.LocalPhytomonitoringTargetPointDao()
                                    .actualizarStatusPunto(
                                        idTargetPoint = punto.idTargetPoint,
                                        status = "Completado"
                                    )
                            }

                            Toast.makeText(
                                context,
                                "Punto finalizado correctamente",
                                Toast.LENGTH_SHORT
                            ).show()

                            onGuardado()

                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Error al finalizar punto: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            finalizando = false
                        }
                    }
                },
                enabled = !finalizando,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF137A25),
                    disabledContainerColor = Color(0xFF9E9E9E)
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp
                )
            ) {
                Text(
                    text = if (finalizando) {
                        "Finalizando..."
                    } else {
                        "✅ Terminar punto"
                    },
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun RegistroHeaderCard(
    punto: LocalPhytomonitoringTargetPointEntity
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFF1F8EF)
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Punto monitoreo (${punto.idTargetPoint})",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF1B1B1B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Radio permitido: ${punto.radiusM} m",
                fontSize = 14.sp,
                color = Color(0xFF6B6B6B),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CultivoMonitoreadoCard(
    nombreCultivo: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFEAF7E8)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Cultivo monitoreado",
                fontSize = 12.sp,
                color = Color(0xFF4F4F4F)
            )

            Text(
                text = nombreCultivo,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF145A20),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CatalogoPlagasGrid(
    catalogo: List<LocalPhytosanitaryCatalogEntity>,
    fitoSeleccionado: LocalPhytosanitaryCatalogEntity?,
    onSelected: (LocalPhytosanitaryCatalogEntity) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        catalogo.chunked(2).forEach { fila ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fila.forEach { item ->
                    FitoCardModerna(
                        item = item,
                        seleccionado = fitoSeleccionado?.idPhytosanitary == item.idPhytosanitary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSelected(item)
                        }
                    )
                }

                if (fila.size == 1) {
                    Spacer(
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    icon: String,
    title: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.size(6.dp))

        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF145A20)
        )
    }
}

@Composable
private fun InfoBox(
    text: String,
    isError: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) Color(0xFFFFEBEE) else Color(0xFFF1F8EF)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            fontSize = 13.sp,
            color = if (isError) Color(0xFFC62828) else Color(0xFF4F4F4F),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FitoCardModerna(
    item: LocalPhytosanitaryCatalogEntity,
    seleccionado: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(88.dp)
            .border(
                width = if (seleccionado) 2.dp else 1.dp,
                color = if (seleccionado) Color(0xFF1B8F2E) else Color(0xFFE0E0E0),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable {
                onClick()
            },
        colors = CardDefaults.cardColors(
            containerColor = if (seleccionado) Color(0xFFF1FAF1) else Color.White
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (seleccionado) 4.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8F5E9)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (item.type.equals("ENFERMEDAD", ignoreCase = true)) {
                            "🦠"
                        } else {
                            "🐛"
                        },
                        fontSize = 22.sp
                    )
                }

                if (seleccionado) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1B8F2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            Text(
                text = item.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF202020),
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RegistroActivoCard(
    nombre: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFEAF7E8)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD5F0D0)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🌿",
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Usted está registrando:",
                    fontSize = 12.sp,
                    color = Color(0xFF4F4F4F)
                )

                Text(
                    text = nombre,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF145A20)
                )
            }
        }
    }
}

@Composable
private fun EtapaRowModerna(
    nombreEtapa: String,
    cantidad: Int,
    onMenos: () -> Unit,
    onMas: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = nombreEtapa,
            fontSize = 15.sp,
            color = Color(0xFF2C2C2C),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )

        CircleCounterButtonModerno(
            text = "−",
            enabled = cantidad > 0,
            borderColor = Color(0xFFDC3D2A),
            backgroundColor = Color.White,
            onClick = onMenos
        )

        Text(
            text = cantidad.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.size(42.dp)
        )

        CircleCounterButtonModerno(
            text = "+",
            enabled = true,
            borderColor = Color(0xFF1B8F2E),
            backgroundColor = Color.White,
            onClick = onMas
        )
    }
}

@Composable
private fun ObservacionesBox(
    value: String,
    onValueChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            if (value.isBlank()) {
                Text(
                    text = "Escribe tus observaciones...",
                    fontSize = 14.sp,
                    color = Color(0xFF9E9E9E)
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = Color.Black
                ),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun CircleCounterButtonModerno(
    text: String,
    enabled: Boolean,
    borderColor: Color,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(34.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            disabledContainerColor = Color(0xFFF0F0F0)
        ),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (enabled) borderColor else Color(0xFFBDBDBD)
        )
    ) {
        Text(
            text = text,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) borderColor else Color(0xFFBDBDBD),
            textAlign = TextAlign.Center
        )
    }
}