package com.example.myapplication.local.admin.agricola

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.Locale

internal data class ImportedRanchCsv(
    val productorRef: String?,
    val name: String,
    val code: String,
    val lat: Double?,
    val lon: Double?
)

internal data class ImportedPlotCsv(
    val ranchoRef: String?,
    val code: String,
    val description: String?,
    val lat: Double?,
    val lon: Double?
)

@Composable
internal fun DialogImportarRanchosCsvGestion(
    guardando: Boolean,
    productores: List<LocalAgroUnitEntity>,
    onDismiss: () -> Unit,
    onAceptar: (LocalAgroUnitEntity?, List<ImportedRanchCsv>) -> Unit,
    onMensaje: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var productorDefault by remember(productores) { mutableStateOf(productores.firstOrNull()) }
    var filasImportadas by remember { mutableStateOf<List<ImportedRanchCsv>>(emptyList()) }
    var cargandoArchivo by remember { mutableStateOf(false) }
    var nombreArchivo by remember { mutableStateOf<String?>(null) }
    var errorArchivo by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        coroutineScope.launch {
            cargandoArchivo = true
            errorArchivo = null
            try {
                val resultado = withContext(Dispatchers.IO) {
                    parseRanchCsvFromUriGestion(context, uri)
                }
                filasImportadas = resultado
                nombreArchivo = uri.lastPathSegment ?: "Archivo seleccionado"
                onMensaje("Archivo cargado con ${resultado.size} ranchos")
            } catch (e: Exception) {
                e.printStackTrace()
                filasImportadas = emptyList()
                errorArchivo = e.message ?: "No se pudo leer el archivo"
                onMensaje("No se pudo leer el archivo: ${e.message}")
            } finally {
                cargandoArchivo = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!guardando && !cargandoArchivo) onDismiss() },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        productores.isEmpty() -> onMensaje("Primero registra un productor")
                        filasImportadas.isEmpty() -> onMensaje("Carga un archivo con ranchos válidos")
                        else -> onAceptar(productorDefault, filasImportadas)
                    }
                },
                enabled = !guardando && !cargandoArchivo && productores.isNotEmpty() && filasImportadas.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (guardando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardando")
                } else {
                    Text("Aceptar e importar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !guardando && !cargandoArchivo) {
                Text("Cancelar", color = Color(0xFF2E7D32))
            }
        },
        title = { ImportCsvDialogTitleGestion("Importar ranchos desde CSV, Excel o notas") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Formato recomendado: productor, nombre, codigo, lat, lon. Puedes usar CSV, TXT o XLSX. Si el archivo no trae productor, se usará el productor seleccionado aquí.",
                    fontSize = 12.sp,
                    color = Color(0xFF5E6D58),
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                ImportCsvSelectorFieldGestion(
                    etiqueta = "Productor por defecto",
                    valor = productorDefault?.commercial_name ?: "Seleccionar productor",
                    opciones = productores,
                    textoOpcion = { it.commercial_name },
                    habilitado = !guardando && !cargandoArchivo && productores.isNotEmpty(),
                    onSeleccionar = { productorDefault = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                ImportCsvPickButtonGestion(
                    cargandoArchivo = cargandoArchivo,
                    tieneArchivo = filasImportadas.isNotEmpty(),
                    textoInicial = "Seleccionar archivo de ranchos",
                    textoCambiar = "Cambiar archivo",
                    enabled = !guardando,
                    onClick = {
                        launcher.launch(arrayOf("*/*"))
                    }
                )

                ImportCsvStatusGestion(nombreArchivo, errorArchivo)

                Spacer(modifier = Modifier.height(12.dp))

                if (filasImportadas.isNotEmpty()) {
                    ImportCsvPreviewGestion(
                        titulo = "Ranchos detectados: ${filasImportadas.size}",
                        filas = filasImportadas.take(8).map { rancho ->
                            val productor = rancho.productorRef?.takeIf { it.isNotBlank() } ?: productorDefault?.commercial_name ?: "Productor default"
                            "${rancho.code} • ${rancho.name} • $productor"
                        },
                        total = filasImportadas.size
                    )
                } else {
                    ImportCsvEmptyGestion("Aquí aparecerá la vista previa de los ranchos antes de guardarlos.")
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

@Composable
internal fun DialogImportarParcelasCsvGestion(
    guardando: Boolean,
    ranchos: List<LocalRanchEntity>,
    onDismiss: () -> Unit,
    onAceptar: (LocalRanchEntity?, List<ImportedPlotCsv>) -> Unit,
    onMensaje: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var ranchoDefault by remember(ranchos) { mutableStateOf(ranchos.firstOrNull()) }
    var filasImportadas by remember { mutableStateOf<List<ImportedPlotCsv>>(emptyList()) }
    var cargandoArchivo by remember { mutableStateOf(false) }
    var nombreArchivo by remember { mutableStateOf<String?>(null) }
    var errorArchivo by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        coroutineScope.launch {
            cargandoArchivo = true
            errorArchivo = null
            try {
                val resultado = withContext(Dispatchers.IO) {
                    parsePlotCsvFromUriGestion(context, uri)
                }
                filasImportadas = resultado
                nombreArchivo = uri.lastPathSegment ?: "Archivo seleccionado"
                onMensaje("Archivo cargado con ${resultado.size} parcelas")
            } catch (e: Exception) {
                e.printStackTrace()
                filasImportadas = emptyList()
                errorArchivo = e.message ?: "No se pudo leer el archivo"
                onMensaje("No se pudo leer el archivo: ${e.message}")
            } finally {
                cargandoArchivo = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!guardando && !cargandoArchivo) onDismiss() },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        ranchos.isEmpty() -> onMensaje("Primero registra un rancho")
                        filasImportadas.isEmpty() -> onMensaje("Carga un archivo con parcelas válidas")
                        else -> onAceptar(ranchoDefault, filasImportadas)
                    }
                },
                enabled = !guardando && !cargandoArchivo && ranchos.isNotEmpty() && filasImportadas.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (guardando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardando")
                } else {
                    Text("Aceptar e importar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !guardando && !cargandoArchivo) {
                Text("Cancelar", color = Color(0xFF2E7D32))
            }
        },
        title = { ImportCsvDialogTitleGestion("Importar parcelas desde CSV, Excel o notas") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Formato recomendado: rancho, codigo, descripcion, lat, lon. Puedes usar CSV, TXT o XLSX. Si el archivo no trae rancho, se usará el rancho seleccionado aquí.",
                    fontSize = 12.sp,
                    color = Color(0xFF5E6D58),
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                ImportCsvSelectorFieldGestion(
                    etiqueta = "Rancho por defecto",
                    valor = ranchoDefault?.let { "${it.name} (${it.code})" } ?: "Seleccionar rancho",
                    opciones = ranchos,
                    textoOpcion = { "${it.name} (${it.code})" },
                    habilitado = !guardando && !cargandoArchivo && ranchos.isNotEmpty(),
                    onSeleccionar = { ranchoDefault = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                ImportCsvPickButtonGestion(
                    cargandoArchivo = cargandoArchivo,
                    tieneArchivo = filasImportadas.isNotEmpty(),
                    textoInicial = "Seleccionar archivo de parcelas",
                    textoCambiar = "Cambiar archivo",
                    enabled = !guardando,
                    onClick = {
                        launcher.launch(arrayOf("*/*"))
                    }
                )

                ImportCsvStatusGestion(nombreArchivo, errorArchivo)

                Spacer(modifier = Modifier.height(12.dp))

                if (filasImportadas.isNotEmpty()) {
                    ImportCsvPreviewGestion(
                        titulo = "Parcelas detectadas: ${filasImportadas.size}",
                        filas = filasImportadas.take(8).map { parcela ->
                            val rancho = parcela.ranchoRef?.takeIf { it.isNotBlank() } ?: ranchoDefault?.code ?: "Rancho default"
                            "${parcela.code} • $rancho • ${parcela.description ?: "Sin descripción"}"
                        },
                        total = filasImportadas.size
                    )
                } else {
                    ImportCsvEmptyGestion("Aquí aparecerá la vista previa de las parcelas antes de guardarlas.")
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

@Composable
private fun ImportCsvPickButtonGestion(
    cargandoArchivo: Boolean,
    tieneArchivo: Boolean,
    textoInicial: String,
    textoCambiar: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled && !cargandoArchivo,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White)
    ) {
        if (cargandoArchivo) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Leyendo archivo")
        } else {
            Text(if (tieneArchivo) textoCambiar else textoInicial)
        }
    }
}

@Composable
private fun ImportCsvStatusGestion(nombreArchivo: String?, errorArchivo: String?) {
    if (!nombreArchivo.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Archivo: $nombreArchivo", fontSize = 11.sp, color = Color(0xFF263B1E), fontWeight = FontWeight.Bold)
    }

    if (!errorArchivo.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = errorArchivo, color = Color(0xFFD32F2F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ImportCsvPreviewGestion(
    titulo: String,
    filas: List<String>,
    total: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBF6))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = titulo, color = Color(0xFF1B5E20), fontWeight = FontWeight.Black, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = Color(0xFFD9E7D1))
            Spacer(modifier = Modifier.height(6.dp))
            filas.forEachIndexed { index, fila ->
                Text(text = "${index + 1}. $fila", color = Color(0xFF263B1E), fontSize = 11.sp, lineHeight = 14.sp)
            }
            if (total > filas.size) {
                Text(text = "... y ${total - filas.size} registros más", color = Color(0xFF5E6D58), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ImportCsvEmptyGestion(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            color = Color(0xFFE65100),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ImportCsvDialogTitleGestion(text: String) {
    Text(text = text, fontWeight = FontWeight.Black, color = Color(0xFF1B5E20))
}

@Composable
private fun <T> ImportCsvSelectorFieldGestion(
    etiqueta: String,
    valor: String,
    opciones: List<T>,
    textoOpcion: (T) -> String,
    habilitado: Boolean,
    onSeleccionar: (T) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = etiqueta,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E4D22),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = if (habilitado) Color(0xFFB6C8AA) else Color(0xFFE0E0E0),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .background(if (habilitado) Color.White else Color(0xFFF5F5F5))
                    .clickable(enabled = habilitado) { abierto = true }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = valor, color = if (habilitado) Color(0xFF263B1E) else Color.Gray, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(text = "⌄", color = Color(0xFF2E7D32), fontWeight = FontWeight.Black)
            }

            DropdownMenu(
                expanded = abierto,
                onDismissRequest = { abierto = false },
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                if (opciones.isEmpty()) {
                    DropdownMenuItem(text = { Text("Sin opciones disponibles") }, onClick = { abierto = false })
                } else {
                    opciones.forEach { opcion ->
                        DropdownMenuItem(
                            text = { Text(textoOpcion(opcion)) },
                            onClick = {
                                abierto = false
                                onSeleccionar(opcion)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun parseRanchCsvFromUriGestion(
    context: android.content.Context,
    uri: Uri
): List<ImportedRanchCsv> {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado")

    if (isOldBinaryExcelGestion(bytes)) {
        throw IllegalStateException("El archivo parece ser Excel antiguo .xls. Guárdalo como .xlsx, .csv o .txt.")
    }

    val rows = if (isZipXlsxGestion(bytes)) parseXlsxRowsGestion(bytes) else parseCsvRowsGestion(bytes)
    val ranchos = rowsToRanchCsvGestion(rows)
    if (ranchos.isEmpty()) throw IllegalStateException("El archivo no contiene ranchos válidos")
    return ranchos
}

private fun parsePlotCsvFromUriGestion(
    context: android.content.Context,
    uri: Uri
): List<ImportedPlotCsv> {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado")

    if (isOldBinaryExcelGestion(bytes)) {
        throw IllegalStateException("El archivo parece ser Excel antiguo .xls. Guárdalo como .xlsx, .csv o .txt.")
    }

    val rows = if (isZipXlsxGestion(bytes)) parseXlsxRowsGestion(bytes) else parseCsvRowsGestion(bytes)
    val parcelas = rowsToPlotCsvGestion(rows)
    if (parcelas.isEmpty()) throw IllegalStateException("El archivo no contiene parcelas válidas")
    return parcelas
}

private fun parseCsvRowsGeneralGestion(bytes: ByteArray): List<List<String>> {
    val text = bytes.toString(Charsets.UTF_8)
    return BufferedReader(InputStreamReader(ByteArrayInputStream(text.toByteArray()), Charsets.UTF_8))
        .readLines()
        .filter { it.isNotBlank() }
        .map { line -> splitCsvLineGeneralGestion(line) }
}

private fun splitCsvLineGeneralGestion(line: String): List<String> {
    val delimiter = when {
        line.count { it == ';' } >= 2 -> ';'
        line.count { it == '\t' } >= 2 -> '\t'
        else -> ','
    }

    val result = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false

    line.forEach { char ->
        when {
            char == '"' -> inQuotes = !inQuotes
            char == delimiter && !inQuotes -> {
                result.add(current.toString().trim().trim('"'))
                current.clear()
            }
            else -> current.append(char)
        }
    }
    result.add(current.toString().trim().trim('"'))
    return result
}

private fun rowsToRanchCsvGestion(rows: List<List<String>>): List<ImportedRanchCsv> {
    val cleanRows = rows.map { row -> row.map { it.trim() } }.filter { row -> row.any { it.isNotBlank() } }
    if (cleanRows.isEmpty()) return emptyList()

    val header = cleanRows.first().map { normalizeCsvHeaderGestion(it) }
    val nameIndex = header.findCsvIndexGestion("nombre", "name", "rancho", "ranch")
    val codeIndex = header.findCsvIndexGestion("codigo", "code", "clave", "codigoranch", "codigorancho")
    val productorIndex = header.findCsvIndexGestion("productor", "agrounit", "unidadagricola", "productorslug", "slugproductor")
    val latIndex = header.findCsvIndexGestion("lat", "latitud", "latitude")
    val lonIndex = header.findCsvIndexGestion("lon", "lng", "longitud", "longitude")

    val hasHeader = nameIndex >= 0 && codeIndex >= 0
    val dataRows = if (hasHeader) cleanRows.drop(1) else cleanRows

    return dataRows.mapNotNull { row ->
        if (hasHeader) {
            val name = row.getOrNull(nameIndex).orEmpty().trim()
            val code = row.getOrNull(codeIndex).orEmpty().trim().uppercase(Locale.getDefault())
            if (name.isBlank() || code.isBlank()) return@mapNotNull null
            ImportedRanchCsv(
                productorRef = row.getOrNull(productorIndex)?.trim()?.ifBlank { null },
                name = name,
                code = code,
                lat = row.getOrNull(latIndex).parseFlexibleDoubleCsvGestion(),
                lon = row.getOrNull(lonIndex).parseFlexibleDoubleCsvGestion()
            )
        } else {
            val name = row.getOrNull(0).orEmpty().trim()
            val code = row.getOrNull(1).orEmpty().trim().uppercase(Locale.getDefault())
            if (name.isBlank() || code.isBlank()) return@mapNotNull null
            ImportedRanchCsv(
                productorRef = null,
                name = name,
                code = code,
                lat = row.getOrNull(2).parseFlexibleDoubleCsvGestion(),
                lon = row.getOrNull(3).parseFlexibleDoubleCsvGestion()
            )
        }
    }.distinctBy { it.code }
}

private fun rowsToPlotCsvGestion(rows: List<List<String>>): List<ImportedPlotCsv> {
    val cleanRows = rows.map { row -> row.map { it.trim() } }.filter { row -> row.any { it.isNotBlank() } }
    if (cleanRows.isEmpty()) return emptyList()

    val header = cleanRows.first().map { normalizeCsvHeaderGestion(it) }
    val codeIndex = header.findCsvIndexGestion("codigo", "code", "clave", "parcela", "plot")
    val descriptionIndex = header.findCsvIndexGestion("descripcion", "description", "desc")
    val ranchoIndex = header.findCsvIndexGestion("rancho", "ranch", "codigoranch", "codigorancho")
    val latIndex = header.findCsvIndexGestion("lat", "latitud", "latitude")
    val lonIndex = header.findCsvIndexGestion("lon", "lng", "longitud", "longitude")

    val hasHeader = codeIndex >= 0
    val dataRows = if (hasHeader) cleanRows.drop(1) else cleanRows

    return dataRows.mapNotNull { row ->
        if (hasHeader) {
            val code = row.getOrNull(codeIndex).orEmpty().trim().uppercase(Locale.getDefault())
            if (code.isBlank()) return@mapNotNull null
            ImportedPlotCsv(
                ranchoRef = row.getOrNull(ranchoIndex)?.trim()?.ifBlank { null },
                code = code,
                description = row.getOrNull(descriptionIndex)?.trim()?.ifBlank { null },
                lat = row.getOrNull(latIndex).parseFlexibleDoubleCsvGestion(),
                lon = row.getOrNull(lonIndex).parseFlexibleDoubleCsvGestion()
            )
        } else {
            val code = row.getOrNull(0).orEmpty().trim().uppercase(Locale.getDefault())
            if (code.isBlank()) return@mapNotNull null
            ImportedPlotCsv(
                ranchoRef = null,
                code = code,
                description = row.getOrNull(1)?.trim()?.ifBlank { null },
                lat = row.getOrNull(2).parseFlexibleDoubleCsvGestion(),
                lon = row.getOrNull(3).parseFlexibleDoubleCsvGestion()
            )
        }
    }.distinctBy { it.code }
}

private fun List<String>.findCsvIndexGestion(vararg names: String): Int {
    val normalizedNames = names.map { normalizeCsvHeaderGestion(it) }.toSet()
    return indexOfFirst { it in normalizedNames }
}

private fun String?.parseFlexibleDoubleCsvGestion(): Double? {
    val raw = this?.trim()?.ifBlank { null } ?: return null
    val normalized = raw.replace(" ", "").replace("°", "").replace(",", ".")
    return normalized.toDoubleOrNull()
}

private fun normalizeCsvHeaderGestion(value: String): String {
    val normalized = Normalizer.normalize(value.trim().lowercase(Locale.getDefault()), Normalizer.Form.NFD)
    return normalized
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace(Regex("[^a-z0-9]"), "")
}
