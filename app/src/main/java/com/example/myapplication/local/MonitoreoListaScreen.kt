package com.example.myapplication.local

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonitoreoListaScreen(
    nombreUsuario: String,
    nombreCia: String,
    busquedaFueConSaltoFiltros: Boolean,

    monitoreos: List<LocalPhytomonitoringHeaderEntity>,
    productores: List<LocalAgroUnitEntity>,
    ranchos: List<LocalRanchEntity>,
    parcelas: List<LocalPlotEntity>,
    programas: List<LocalProgramEntity>,

    onBackClick: () -> Unit,
    onAbrirMapaClick: (LocalPhytomonitoringHeaderEntity) -> Unit
) {
    val productoresMap = remember(productores) {
        productores.associateBy { it.idLocalAgroUnit }
    }

    val ranchosMap = remember(ranchos) {
        ranchos.associateBy { it.idLocalRanch }
    }

    val parcelasMap = remember(parcelas) {
        parcelas.associateBy { it.idLocalPlot }
    }

    val programasMap = remember(programas) {
        programas.associateBy { it.idProgram }
    }

    var monitoreoInfoSeleccionado by remember {
        mutableStateOf<LocalPhytomonitoringHeaderEntity?>(null)
    }

    val primerHeader = monitoreos.firstOrNull()
    val primerPrograma = primerHeader?.let { programasMap[it.idProgram] }
    val primeraParcela = primerHeader?.let { parcelasMap[it.idLocalPlot] }
    val primerRancho = primeraParcela?.let { ranchosMap[it.idLocalRanch] }
    val primerProductor = primerPrograma?.let { productoresMap[it.idLocalAgroUnit] }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            HeaderLista(
                nombreUsuario = nombreUsuario,
                onBackClick = onBackClick
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Text(
                        text = "Información general",
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF0E7E7))
                            .padding(10.dp),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (busquedaFueConSaltoFiltros) {
                        TextoInfo(
                            titulo = "CIA:",
                            valor = nombreCia
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Se muestran monitoreos disponibles sin aplicar filtros de productor, rancho o parcela.",
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )
                    } else {
                        TextoInfo(
                            titulo = "Productor:",
                            valor = primerProductor?.commercial_name ?: "-"
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TextoInfo(
                            titulo = "Rancho:",
                            valor = primerRancho?.name ?: "-"
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TextoInfo(
                            titulo = "Parcela:",
                            valor = primeraParcela?.code ?: "-"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "Monitoreos disponibles:",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color.Black,
                        shape = RoundedCornerShape(4.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HeaderTabla("Monitoreo", Modifier.weight(1f))
                        HeaderTabla("Estado", Modifier.weight(1.2f))
                        HeaderTabla("Fecha", Modifier.weight(1.3f))
                        HeaderTabla("Info.", Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (monitoreos.isEmpty()) {
                        Text(
                            text = "No se encontraron monitoreos con esos filtros.",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        monitoreos.forEach { header ->

                            val programa = programasMap[header.idProgram]
                            val nombreMonitoreo = programa?.cycle ?: "M-${header.idHeader}"

                            FilaMonitoreo(
                                nombreMonitoreo = nombreMonitoreo,
                                status = header.status,
                                fecha = header.est_start_date,
                                onAbrirMapaClick = {
                                    onAbrirMapaClick(header)
                                },
                                onInfoClick = {
                                    monitoreoInfoSeleccionado = header
                                }
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }
        }

        val headerDetalle = monitoreoInfoSeleccionado

        if (headerDetalle != null) {
            val programa = programasMap[headerDetalle.idProgram]
            val parcela = parcelasMap[headerDetalle.idLocalPlot]
            val rancho = parcela?.let { ranchosMap[it.idLocalRanch] }
            val productor = programa?.let { productoresMap[it.idLocalAgroUnit] }

            DetalleMonitoreoDialog(
                header = headerDetalle,
                programa = programa,
                productor = productor,
                rancho = rancho,
                parcela = parcela,
                nombreCia = nombreCia,
                onDismiss = {
                    monitoreoInfoSeleccionado = null
                }
            )
        }
    }
}

@Composable
private fun HeaderLista(
    nombreUsuario: String,
    onBackClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF52AFC4)),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("↩", color = Color.White, fontSize = 22.sp)
        }

        Text(
            text = "☰",
            modifier = Modifier.align(Alignment.TopEnd),
            fontSize = 28.sp,
            color = Color.Black
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(22.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🟩", fontSize = 34.sp)

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = "TIERRA",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )

                    Text(
                        text = "INTELIGENTE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Bienvenido $nombreUsuario",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF666666)
            )
        }
    }

    Spacer(modifier = Modifier.height(125.dp))
}

@Composable
private fun TextoInfo(
    titulo: String,
    valor: String
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = titulo,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color.Black
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = valor,
            fontSize = 12.sp,
            color = Color.DarkGray
        )
    }
}

@Composable
private fun HeaderTabla(
    texto: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = texto,
        modifier = modifier,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun FilaMonitoreo(
    nombreMonitoreo: String,
    status: String,
    fecha: Long,
    onAbrirMapaClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onAbrirMapaClick()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = nombreMonitoreo,
            modifier = Modifier.weight(1f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )

        Text(
            text = textoEstado(status),
            modifier = Modifier.weight(1.2f),
            fontSize = 11.sp,
            color = colorEstado(status),
            textAlign = TextAlign.Center
        )

        Text(
            text = formatearFechaHora(fecha),
            modifier = Modifier.weight(1.3f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onInfoClick,
            modifier = Modifier
                .weight(1f)
                .height(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF7ED957)
            ),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = "Info",
                color = Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DetalleMonitoreoDialog(
    header: LocalPhytomonitoringHeaderEntity,
    programa: LocalProgramEntity?,
    productor: LocalAgroUnitEntity?,
    rancho: LocalRanchEntity?,
    parcela: LocalPlotEntity?,
    nombreCia: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Información del monitoreo",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                DatoDetalle("Monitoreo:", programa?.cycle ?: "Sin ciclo")
                DatoDetalle("Estado:", textoEstado(header.status))
                DatoDetalle("CIA:", nombreCia)
                DatoDetalle("Productor:", productor?.commercial_name ?: "-")
                DatoDetalle("Rancho:", rancho?.name ?: "-")
                DatoDetalle("Parcela:", parcela?.code ?: "-")
                DatoDetalle("Fecha programada:", formatearFechaCompleta(header.est_start_date))
                DatoDetalle("Fecha fin estimada:", formatearFechaCompleta(header.est_finish_date))
                DatoDetalle("Iniciado:", formatearFechaOpcional(header.started_at))
                DatoDetalle("Finalizado:", formatearFechaOpcional(header.finished_at))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
private fun DatoDetalle(
    titulo: String,
    valor: String
) {
    Column(
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Text(
            text = titulo,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Color.Black
        )

        Text(
            text = valor,
            fontSize = 12.sp,
            color = Color.DarkGray
        )
    }
}

private fun textoEstado(status: String): String {
    return when (status.lowercase()) {
        "pending" -> "Pendiente"
        "in_progress" -> "En progreso"
        "completed" -> "Completado"
        "cancelled" -> "Cancelado"
        "pendiente" -> "Pendiente"
        "vigente" -> "En progreso"
        "finalizado" -> "Completado"
        "cancelado" -> "Cancelado"
        else -> status
    }
}

private fun colorEstado(status: String): Color {
    return when (status.lowercase()) {
        "pending" -> Color(0xFFC9B800)
        "in_progress" -> Color(0xFF4CAF50)
        "completed" -> Color(0xFF1976D2)
        "cancelled" -> Color(0xFFE53935)
        "pendiente" -> Color(0xFFC9B800)
        "vigente" -> Color(0xFF4CAF50)
        "finalizado" -> Color(0xFF1976D2)
        "cancelado" -> Color(0xFFE53935)
        else -> Color.Black
    }
}

private fun formatearFechaHora(fecha: Long): String {
    return try {
        val sdf = SimpleDateFormat("dd-MM-yyyy\nHH:mm", Locale.getDefault())
        sdf.format(Date(fecha))
    } catch (e: Exception) {
        "-"
    }
}

private fun formatearFechaCompleta(fecha: Long): String {
    return try {
        val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(fecha))
    } catch (e: Exception) {
        "-"
    }
}

private fun formatearFechaOpcional(fecha: Long?): String {
    if (fecha == null) return "No registrado"

    return try {
        val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(fecha))
    } catch (e: Exception) {
        "-"
    }
}