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
import androidx.compose.material3.OutlinedButton
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
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import com.example.myapplication.local.map.MapaPoligonoParcela
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.max

internal data class ImportedPolygonPoint(
    val level: Int,
    val lat: Double,
    val lon: Double
)

@Composable
internal fun DialogImportarPoligonoExcelGestion(
    guardando: Boolean,
    parcelas: List<LocalPlotEntity>,
    ranchos: List<LocalRanchEntity>,
    parcelaInicial: LocalPlotEntity?,
    onDismiss: () -> Unit,
    onAceptar: (LocalPlotEntity, List<ImportedPolygonPoint>) -> Unit,
    onMensaje: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var parcelaSeleccionada by remember(parcelaInicial?.idLocalPlot, parcelas) {
        mutableStateOf(
            parcelas.firstOrNull { it.idLocalPlot == parcelaInicial?.idLocalPlot }
                ?: parcelaInicial
                ?: parcelas.firstOrNull()
        )
    }
    var puntosImportados by remember { mutableStateOf<List<ImportedPolygonPoint>>(emptyList()) }
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
                    parsePolygonFileFromUri(
                        context = context,
                        uri = uri
                    )
                }

                puntosImportados = resultado
                nombreArchivo = uri.lastPathSegment ?: "Archivo seleccionado"
                onMensaje("Archivo cargado con ${resultado.size} vértices")
            } catch (e: Exception) {
                e.printStackTrace()
                puntosImportados = emptyList()
                errorArchivo = e.message ?: "No se pudo leer el archivo"
                onMensaje("No se pudo leer el archivo: ${e.message}")
            } finally {
                cargandoArchivo = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!guardando && !cargandoArchivo) onDismiss()
        },
        confirmButton = {
            Button(
                onClick = {
                    val parcela = parcelaSeleccionada
                    when {
                        parcela == null -> onMensaje("Selecciona una parcela")
                        puntosImportados.size < 3 -> onMensaje("Carga un CSV, Excel o nota con mínimo 3 vértices")
                        else -> onAceptar(parcela, puntosImportados)
                    }
                },
                enabled = !guardando && !cargandoArchivo && parcelaSeleccionada != null && puntosImportados.size >= 3,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (guardando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardando")
                } else {
                    Text("Aceptar y guardar")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !guardando && !cargandoArchivo
            ) {
                Text("Cancelar", color = Color(0xFF2E7D32))
            }
        },
        title = {
            Text(
                text = "Importar polígono desde CSV, Excel o notas",
                fontWeight = FontWeight.Black,
                color = Color(0xFF1B5E20)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Selecciona la parcela, carga un archivo .csv, .xlsx, .txt o notas, revisa el dibujo del polígono y confirma con Aceptar. Si cancelas, no se guarda nada.",
                    fontSize = 12.sp,
                    color = Color(0xFF5E6D58),
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                PolygonExcelSelectorField(
                    etiqueta = "Parcela destino",
                    valor = parcelaSeleccionada?.let { parcela ->
                        val rancho = ranchos.firstOrNull { it.idLocalRanch == parcela.idLocalRanch }
                        "${parcela.code?.takeIf { it.isNotBlank() } ?: parcela.name} (${rancho?.name ?: "Rancho no encontrado"})"
                    } ?: "Seleccionar parcela",
                    opciones = parcelas,
                    textoOpcion = { parcela ->
                        val rancho = ranchos.firstOrNull { it.idLocalRanch == parcela.idLocalRanch }
                        "${parcela.code?.takeIf { it.isNotBlank() } ?: parcela.name} (${rancho?.name ?: "Rancho no encontrado"})"
                    },
                    habilitado = !guardando && !cargandoArchivo && parcelas.isNotEmpty(),
                    onSeleccionar = { parcelaSeleccionada = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        launcher.launch(arrayOf("*/*"))
                    },
                    enabled = !guardando && !cargandoArchivo,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    )
                ) {
                    if (cargandoArchivo) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Leyendo archivo")
                    } else {
                        Text(if (puntosImportados.isEmpty()) "Seleccionar CSV/Excel/Notas" else "Cambiar archivo")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Formato recomendado: columnas nivel, lat, lon. También funciona con lat, lon sin encabezados o texto de notas. Puedes usar CSV, TXT o XLSX.",
                    fontSize = 11.sp,
                    color = Color(0xFF5E6D58),
                    lineHeight = 14.sp
                )

                if (!nombreArchivo.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Archivo: $nombreArchivo",
                        fontSize = 11.sp,
                        color = Color(0xFF263B1E),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!errorArchivo.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorArchivo ?: "",
                        color = Color(0xFFD32F2F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (puntosImportados.isNotEmpty()) {
                    PolygonPreviewCardGestion(puntos = puntosImportados)
                    Spacer(modifier = Modifier.height(10.dp))
                    PolygonPointsSummaryGestion(puntos = puntosImportados)
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                    ) {
                        Text(
                            text = "Todavía no hay archivo cargado. Aquí aparecerá la vista previa del polígono antes de guardar.",
                            modifier = Modifier.padding(14.dp),
                            color = Color(0xFFE65100),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

@Composable
internal fun PoligonoParcelaItemGestion(
    parcela: LocalPlotEntity,
    rancho: LocalRanchEntity?,
    vertices: List<LocalPlotVertexEntity>,
    onCargarExcel: () -> Unit,
    onEliminarPoligono: () -> Unit
) {
    val puntos = remember(vertices) {
        vertices
            .sortedBy { it.level }
            .map { ImportedPolygonPoint(level = it.level, lat = it.lat, lon = it.lon) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBF6)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFD9E7D1), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🗺️", fontSize = 24.sp)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = parcela.code?.takeIf { it.isNotBlank() } ?: parcela.name,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF263B1E),
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Rancho: ${rancho?.name ?: "No encontrado"}  •  Vértices: ${puntos.size}",
                        color = Color(0xFF5E6D58),
                        fontSize = 12.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (puntos.size >= 3) {
                PolygonPreviewCardGestion(puntos = puntos, altura = 180)
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Text(
                        text = "Esta parcela todavía no tiene polígono completo.",
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFFE65100),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCargarExcel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32))
                ) {
                    Text(
                        text = if (puntos.isEmpty()) "Cargar archivo" else "Reemplazar archivo",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onEliminarPoligono,
                    enabled = puntos.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
                ) {
                    Text("Eliminar polígono", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


@Composable
private fun MapaPoligonoParcelaPreview(
    puntos: List<ImportedPolygonPoint>,
    altura: Int
) {
    val verticesPreview = remember(puntos) {
        puntos
            .sortedBy { it.level }
            .map { punto ->
                LocalPlotVertexEntity(
                    idLocalPlotVertex = 0L,
                    extId = null,
                    level = punto.level,
                    lat = punto.lat,
                    lon = punto.lon,
                    idLocalPlot = 0L
                )
            }
    }

    MapaPoligonoParcela(
        vertices = verticesPreview,
        titulo = "Vista satelital del polígono",
        modifier = Modifier
            .fillMaxWidth()
            .height(altura.dp)
            .clip(RoundedCornerShape(16.dp))
    )
}

@Composable
internal fun PolygonPreviewCardGestion(
    puntos: List<ImportedPolygonPoint>,
    altura: Int = 240
) {
    val puntosOrdenados = remember(puntos) { puntos.sortedBy { it.level } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "Mapa del polígono",
                color = Color(0xFF1B5E20),
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (puntosOrdenados.size >= 3) {
                MapaPoligonoParcelaPreview(
                    puntos = puntosOrdenados,
                    altura = altura
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(altura.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF8FBF6))
                        .border(1.dp, Color(0xFFD9E7D1), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Carga mínimo 3 coordenadas para ver el polígono en el mapa",
                        color = Color(0xFF5E6D58),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}


@Composable
private fun PolygonPointsSummaryGestion(puntos: List<ImportedPolygonPoint>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBF6))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Vértices detectados: ${puntos.size}",
                color = Color(0xFF1B5E20),
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = Color(0xFFD9E7D1))
            Spacer(modifier = Modifier.height(6.dp))

            puntos.take(8).forEach { punto ->
                Text(
                    text = "${punto.level}. ${punto.lat}, ${punto.lon}",
                    color = Color(0xFF263B1E),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }

            if (puntos.size > 8) {
                Text(
                    text = "... y ${puntos.size - 8} vértices más",
                    color = Color(0xFF5E6D58),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun <T> PolygonExcelSelectorField(
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
                Text(
                    text = valor,
                    color = if (habilitado) Color(0xFF263B1E) else Color.Gray,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "⌄", color = Color(0xFF2E7D32), fontWeight = FontWeight.Black)
            }

            DropdownMenu(
                expanded = abierto,
                onDismissRequest = { abierto = false },
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                if (opciones.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Sin opciones disponibles") },
                        onClick = { abierto = false }
                    )
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

internal fun parsePolygonFileFromUri(
    context: android.content.Context,
    uri: Uri
): List<ImportedPolygonPoint> {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado")

    if (isOldBinaryExcelGestion(bytes)) {
        throw IllegalStateException(
            "El archivo parece ser Excel antiguo .xls. Guárdalo como .xlsx o .csv para que Android pueda leerlo sin librerías extra."
        )
    }

    val rows = if (isZipXlsxGestion(bytes)) {
        parseXlsxRowsGestion(bytes)
    } else {
        parseCsvRowsGestion(bytes)
    }

    var puntos = rowsToPolygonPointsGestion(rows)

    if (puntos.size < 3 && !isZipXlsxGestion(bytes)) {
        puntos = extractPolygonPointsFromFreeTextGestion(bytesToTextGestion(bytes))
    }

    if (puntos.size < 3) {
        throw IllegalStateException(
            "El archivo debe tener mínimo 3 vértices válidos. Usa columnas nivel, lat, lon o líneas como: 1 21.014252 -101.497125"
        )
    }

    return puntos
}

internal fun isZipXlsxGestion(bytes: ByteArray): Boolean {
    return bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
}

internal fun isOldBinaryExcelGestion(bytes: ByteArray): Boolean {
    return bytes.size >= 8 &&
            bytes[0] == 0xD0.toByte() &&
            bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() &&
            bytes[3] == 0xE0.toByte()
}

private fun bytesToTextGestion(bytes: ByteArray): String {
    return bytes.toString(Charsets.UTF_8)
        .removePrefix("\uFEFF")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
}

internal fun parseCsvRowsGestion(bytes: ByteArray): List<List<String>> {
    val text = bytesToTextGestion(bytes)
    return BufferedReader(InputStreamReader(ByteArrayInputStream(text.toByteArray()), Charsets.UTF_8))
        .readLines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { line -> splitCsvLineGestion(line) }
}

private fun splitCsvLineGestion(line: String): List<String> {
    val delimiter = when {
        line.count { it == ';' } >= 2 -> ';'
        line.count { it == '\t' } >= 2 -> '\t'
        line.count { it == ',' } >= 2 -> ','
        else -> null
    }

    if (delimiter == null) {
        return line
            .trim()
            .split(Regex("\\s+"))
            .map { it.trim().trim('"') }
            .filter { it.isNotBlank() }
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

internal fun parseXlsxRowsGestion(bytes: ByteArray): List<List<String>> {
    val entries = mutableMapOf<String, ByteArray>()

    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && (
                        entry.name == "xl/sharedStrings.xml" ||
                                entry.name == "xl/worksheets/sheet1.xml" ||
                                entry.name.startsWith("xl/worksheets/sheet")
                        )
            ) {
                entries[entry.name] = zip.readBytes()
            }
        }
    }

    val sheetName = entries.keys
        .filter { it.startsWith("xl/worksheets/sheet") }
        .sorted()
        .firstOrNull()
        ?: throw IllegalStateException("No se encontró ninguna hoja dentro del Excel")

    val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStringsGestion(it) } ?: emptyList()
    return parseSheetRowsGestion(entries[sheetName] ?: ByteArray(0), sharedStrings)
}

private fun parseSharedStringsGestion(bytes: ByteArray): List<String> {
    val parser = XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

    val strings = mutableListOf<String>()
    val current = StringBuilder()
    var insideT = false

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                if (parser.name == "si") current.clear()
                if (parser.name == "t") insideT = true
            }
            XmlPullParser.TEXT -> {
                if (insideT) current.append(parser.text)
            }
            XmlPullParser.END_TAG -> {
                if (parser.name == "t") insideT = false
                if (parser.name == "si") strings.add(current.toString())
            }
        }
        event = parser.next()
    }

    return strings
}

private fun parseSheetRowsGestion(
    bytes: ByteArray,
    sharedStrings: List<String>
): List<List<String>> {
    val parser = XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

    val rows = mutableListOf<List<String>>()
    var currentRow = mutableMapOf<Int, String>()
    var currentColumn = 0
    var currentType: String? = null
    var insideV = false
    var value = ""

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                when (parser.name) {
                    "row" -> currentRow = mutableMapOf()
                    "c" -> {
                        val ref = parser.getAttributeValue(null, "r") ?: "A1"
                        currentColumn = columnIndexFromCellRefGestion(ref)
                        currentType = parser.getAttributeValue(null, "t")
                        value = ""
                    }
                    "v", "t" -> insideV = true
                }
            }
            XmlPullParser.TEXT -> {
                if (insideV) value += parser.text
            }
            XmlPullParser.END_TAG -> {
                when (parser.name) {
                    "v", "t" -> insideV = false
                    "c" -> {
                        val cellValue = if (currentType == "s") {
                            value.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: value
                        } else {
                            value
                        }
                        if (cellValue.isNotBlank()) currentRow[currentColumn] = cellValue.trim()
                    }
                    "row" -> {
                        if (currentRow.isNotEmpty()) {
                            val maxCol = currentRow.keys.maxOrNull() ?: 0
                            rows.add((0..maxCol).map { currentRow[it] ?: "" })
                        }
                    }
                }
            }
        }
        event = parser.next()
    }

    return rows
}

private fun columnIndexFromCellRefGestion(ref: String): Int {
    var result = 0
    ref.takeWhile { it.isLetter() }.uppercase(Locale.US).forEach { char ->
        result = result * 26 + (char - 'A' + 1)
    }
    return max(result - 1, 0)
}

private fun rowsToPolygonPointsGestion(rows: List<List<String>>): List<ImportedPolygonPoint> {
    val cleanRows = rows
        .map { row -> row.map { it.trim() } }
        .filter { row -> row.any { it.isNotBlank() } }

    if (cleanRows.isEmpty()) return emptyList()

    val header = cleanRows.first()
    val normalizedHeader = header.map { normalizeHeaderGestion(it) }
    val latIndex = normalizedHeader.indexOfFirst { it in setOf("lat", "latitud", "latitude", "y") }
    val lonIndex = normalizedHeader.indexOfFirst { it in setOf("lon", "lng", "longitud", "longitude", "x") }
    val levelIndex = normalizedHeader.indexOfFirst { it in setOf("nivel", "level", "orden", "order", "punto", "vertice") }

    val dataRows = if (latIndex >= 0 && lonIndex >= 0) cleanRows.drop(1) else cleanRows

    val puntos = mutableListOf<ImportedPolygonPoint>()

    dataRows.forEachIndexed { index, row ->
        val parsed = if (latIndex >= 0 && lonIndex >= 0) {
            val lat = row.getOrNull(latIndex).parseFlexibleDoubleGestion()
            val lon = row.getOrNull(lonIndex).parseFlexibleDoubleGestion()
            val level = row.getOrNull(levelIndex).parseFlexibleIntGestion() ?: (index + 1)
            if (lat != null && lon != null) ImportedPolygonPoint(level, lat, lon) else null
        } else {
            parsePointWithoutHeaderGestion(row, index + 1)
        }

        if (parsed != null && parsed.lat in -90.0..90.0 && parsed.lon in -180.0..180.0) {
            puntos.add(parsed)
        }
    }

    return puntos
        .distinctBy { "${"%.7f".format(Locale.US, it.lat)}_${"%.7f".format(Locale.US, it.lon)}" }
        .sortedBy { it.level }
        .mapIndexed { index, punto -> punto.copy(level = index + 1) }
}


private fun extractPolygonPointsFromFreeTextGestion(text: String): List<ImportedPolygonPoint> {
    val decimalRegex = Regex("[-+]?\\d{1,3}(?:[.,]\\d+)?")
    val puntos = mutableListOf<ImportedPolygonPoint>()

    text.lines().forEachIndexed { index, rawLine ->
        val line = rawLine.trim()
        if (line.isBlank()) return@forEachIndexed

        val numbers = decimalRegex
            .findAll(line)
            .mapNotNull { it.value.parseFlexibleDoubleGestion() }
            .toList()

        if (numbers.size >= 2) {
            val parsed = when {
                numbers.size >= 3 && numbers[1] in -90.0..90.0 && numbers[2] in -180.0..180.0 -> {
                    ImportedPolygonPoint(
                        level = numbers[0].toInt().takeIf { it > 0 } ?: (index + 1),
                        lat = numbers[1],
                        lon = numbers[2]
                    )
                }
                numbers[0] in -90.0..90.0 && numbers[1] in -180.0..180.0 -> {
                    ImportedPolygonPoint(
                        level = index + 1,
                        lat = numbers[0],
                        lon = numbers[1]
                    )
                }
                else -> null
            }

            if (parsed != null) puntos.add(parsed)
        }
    }

    return puntos
        .distinctBy { "${"%.7f".format(Locale.US, it.lat)}_${"%.7f".format(Locale.US, it.lon)}" }
        .sortedBy { it.level }
        .mapIndexed { index, punto -> punto.copy(level = index + 1) }
}

private fun parsePointWithoutHeaderGestion(
    row: List<String>,
    fallbackLevel: Int
): ImportedPolygonPoint? {
    val numbers = row.mapNotNull { it.parseFlexibleDoubleGestion() }
    if (numbers.size < 2) return null

    return if (numbers.size >= 3 && numbers[1] in -90.0..90.0 && numbers[2] in -180.0..180.0) {
        ImportedPolygonPoint(
            level = numbers[0].toInt().takeIf { it > 0 } ?: fallbackLevel,
            lat = numbers[1],
            lon = numbers[2]
        )
    } else {
        ImportedPolygonPoint(
            level = fallbackLevel,
            lat = numbers[0],
            lon = numbers[1]
        )
    }
}

private fun String?.parseFlexibleDoubleGestion(): Double? {
    val raw = this?.trim()?.ifBlank { null } ?: return null
    val normalized = raw
        .replace(" ", "")
        .replace("°", "")
        .replace(",", ".")
    return normalized.toDoubleOrNull()
}

private fun String?.parseFlexibleIntGestion(): Int? {
    return this?.trim()?.toDoubleOrNull()?.toInt()
}

private fun normalizeHeaderGestion(value: String): String {
    val normalized = Normalizer.normalize(value.trim().lowercase(Locale.getDefault()), Normalizer.Form.NFD)
    return normalized
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace(Regex("[^a-z0-9]"), "")
}
