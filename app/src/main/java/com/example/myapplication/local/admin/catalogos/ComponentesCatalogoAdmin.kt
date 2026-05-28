package com.example.myapplication.local.admin.catalogos

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class AdminCatalogoTab {
    CULTIVOS,
    FITOSANITARIO
}

internal data class ResultadoOperacionCatalogoAdmin(
    val exito: Boolean,
    val mensaje: String
)

@Composable
internal fun AdminCatalogTabs(
    tabActual: AdminCatalogoTab,
    onTabChange: (AdminCatalogoTab) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CatalogTabButton("Cultivos", "🌱", tabActual == AdminCatalogoTab.CULTIVOS, Modifier.weight(1f)) {
                onTabChange(AdminCatalogoTab.CULTIVOS)
            }
            CatalogTabButton("Fito", "🐛", tabActual == AdminCatalogoTab.FITOSANITARIO, Modifier.weight(1f)) {
                onTabChange(AdminCatalogoTab.FITOSANITARIO)
            }
        }
    }
}

@Composable
private fun CatalogTabButton(
    text: String,
    icon: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color(0xFF2E7D32) else Color(0xFFF1F5EE))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            color = if (selected) Color.White else Color(0xFF2E4D22),
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun AdminCatalogLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = Color(0xFF2E7D32))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Cargando catálogos...", fontWeight = FontWeight.Bold, color = Color(0xFF2E4D22))
        }
    }
}

@Composable
internal fun CatalogSectionCard(
    icono: String,
    titulo: String,
    descripcion: String,
    botonTexto: String,
    onAgregarClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8F5E9)),
                    contentAlignment = Alignment.Center
                ) { Text(text = icono, fontSize = 22.sp) }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = titulo, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF1B5E20))
                    Text(text = descripcion, fontSize = 12.sp, color = Color(0xFF5E6D58), lineHeight = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onAgregarClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White)
            ) {
                Text(text = "+ $botonTexto", fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 4.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFFD9E7D1))
            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}

@Composable
internal fun EmptyCatalogMessage(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            textAlign = TextAlign.Center,
            color = Color(0xFFE65100),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
internal fun CatalogInfoItemAdmin(
    icono: String,
    photo: String?,
    titulo: String,
    subtitulo: String,
    onEditar: (() -> Unit)?,
    textoBloqueado: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBF6)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CatalogPhotoBoxAdmin(photo = photo, fallbackIcon = icono, sizeDp = 46)

            Column(modifier = Modifier.weight(1f)) {
                Text(text = titulo, fontWeight = FontWeight.Black, color = Color(0xFF263B1E), fontSize = 14.sp)
                Text(text = subtitulo, color = Color(0xFF5E6D58), fontSize = 12.sp, lineHeight = 15.sp)
            }

            if (onEditar != null) {
                OutlinedButton(
                    onClick = onEditar,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32))
                ) {
                    Text("Editar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else if (!textoBloqueado.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFE8F5E9))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = textoBloqueado,
                        color = Color(0xFF1B5E20),
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
internal fun AdminImagePickerField(
    photo: String?,
    guardando: Boolean,
    onPhotoChange: (String?) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Algunos proveedores no permiten permiso persistente; aun así guardamos el URI.
            }
            onPhotoChange(uri.toString())
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBF6))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Imagen",
                fontWeight = FontWeight.Black,
                color = Color(0xFF1B5E20),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CatalogPhotoBoxAdmin(photo = photo, fallbackIcon = "🖼️", sizeDp = 70)
                Column(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { launcher.launch(arrayOf("image/*")) },
                        enabled = !guardando,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32),
                            contentColor = Color.White
                        )
                    ) {
                        Text(if (photo.isNullOrBlank()) "Seleccionar imagen" else "Cambiar imagen")
                    }
                    AnimatedVisibility(visible = !photo.isNullOrBlank()) {
                        TextButton(
                            onClick = { onPhotoChange(null) },
                            enabled = !guardando
                        ) {
                            Text("Quitar imagen", color = Color(0xFFD84315))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CatalogPhotoBoxAdmin(
    photo: String?,
    fallbackIcon: String,
    sizeDp: Int
) {
    val context = LocalContext.current
    var bitmap by remember(photo) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photo) {
        bitmap = if (photo.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(Uri.parse(photo)).use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(
                width = 1.dp,
                color = Color(0xFFD9E7D1),
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        val img = bitmap
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = "Imagen del catálogo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(text = fallbackIcon, fontSize = (sizeDp / 2).sp)
        }
    }
}

@Composable
internal fun AdminDialogConfirmButton(
    text: String,
    guardando: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !guardando && enabled,
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
            Text(text)
        }
    }
}

@Composable
internal fun AdminDialogTitle(text: String) {
    Text(text = text, fontWeight = FontWeight.Black, color = Color(0xFF1B5E20))
}

@Composable
internal fun DialogInfoText(text: String) {
    Text(text = text, fontSize = 12.sp, color = Color(0xFF5E6D58), lineHeight = 15.sp)
}

@Composable
internal fun AdminDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    guardando: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        enabled = !guardando,
        singleLine = true
    )
}

@Composable
internal fun AdminDialogNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    guardando: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { char -> char.isDigit() }) },
        modifier = modifier,
        label = { Text(label) },
        enabled = !guardando,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
internal fun <T> AdminSelectorFieldCatalog(
    etiqueta: String,
    valor: String,
    opciones: List<T>,
    textoOpcion: (T) -> String,
    habilitado: Boolean,
    permitirGeneral: Boolean,
    onGeneral: () -> Unit,
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
                if (permitirGeneral) {
                    DropdownMenuItem(
                        text = { Text("General / sin cultivo específico") },
                        onClick = {
                            abierto = false
                            onGeneral()
                        }
                    )
                }

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
