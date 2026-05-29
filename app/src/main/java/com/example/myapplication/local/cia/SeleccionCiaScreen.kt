package com.example.myapplication.local.cia

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.entities.LocalCiaEntity
import com.example.myapplication.local.entities.LocalParentCiaEntity
import java.util.Locale

@Composable
fun SeleccionCiaScreen(
    nombreUsuario: String,
    rolUsuario: String = "",

    parentCias: List<LocalParentCiaEntity>,
    parentCiaSeleccionada: LocalParentCiaEntity?,
    onParentCiaChange: (LocalParentCiaEntity) -> Unit,

    cias: List<LocalCiaEntity>,
    ciaSeleccionada: LocalCiaEntity?,

    seleccionarPreferente: Boolean,
    onPreferenteChange: (Boolean) -> Unit,

    onCiaChange: (LocalCiaEntity) -> Unit,
    onSeleccionarClick: () -> Unit,
    onCerrarSesionClick: () -> Unit,
    onPerfilClick: () -> Unit = {},
    onMonitoreosClick: () -> Unit = {},
    onAdminClick: () -> Unit = {}
) {
    val rolNormalizado = remember(rolUsuario) {
        normalizarRolSeleccionCia(rolUsuario)
    }

    val esSupervisor = rolNormalizado == "supervisor"
    val puedeContinuar = if (esSupervisor) {
        ciaSeleccionada != null
    } else {
        parentCiaSeleccionada != null && ciaSeleccionada != null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EncabezadoApp(
                nombreUsuario = nombreUsuario,
                rolUsuario = rolUsuario,
                onPerfilClick = onPerfilClick,
                onMonitoreosClick = onMonitoreosClick,
                onAdminClick = onAdminClick,
                onCerrarSesionClick = onCerrarSesionClick
            )

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TituloSeleccionCia(esSupervisor = esSupervisor)

                Spacer(modifier = Modifier.height(18.dp))

                if (!esSupervisor) {
                    SelectorCiaPadre(
                        parentCias = parentCias,
                        parentCiaSeleccionada = parentCiaSeleccionada,
                        onParentCiaChange = onParentCiaChange
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                }

                SelectorCiaHija(
                    esSupervisor = esSupervisor,
                    parentCiaSeleccionada = parentCiaSeleccionada,
                    cias = cias,
                    ciaSeleccionada = ciaSeleccionada,
                    onCiaChange = onCiaChange
                )

                Spacer(modifier = Modifier.height(14.dp))

                MensajeEstadoSeleccionCia(
                    esSupervisor = esSupervisor,
                    parentCiaSeleccionada = parentCiaSeleccionada,
                    ciaSeleccionada = ciaSeleccionada
                )

                Spacer(modifier = Modifier.height(12.dp))

                TarjetaCiaPreferente(
                    seleccionarPreferente = seleccionarPreferente,
                    onPreferenteChange = onPreferenteChange
                )

                Spacer(modifier = Modifier.height(22.dp))

                BotonesSeleccionCia(
                    puedeContinuar = puedeContinuar,
                    onSeleccionarClick = onSeleccionarClick
                )

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun TituloSeleccionCia(
    esSupervisor: Boolean
) {
    Text(
        text = if (esSupervisor) "Seleccionar CIA hija" else "Seleccionar organización",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1F331F),
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(6.dp))

    Text(
        text = if (esSupervisor) {
            "Selecciona una de las CIAS hijas disponibles para consultar sus monitoreos."
        } else {
            "Primero elige la CIA padre y después selecciona una CIA hija."
        },
        fontSize = 13.sp,
        color = Color(0xFF666666),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

@Composable
private fun MensajeEstadoSeleccionCia(
    esSupervisor: Boolean,
    parentCiaSeleccionada: LocalParentCiaEntity?,
    ciaSeleccionada: LocalCiaEntity?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF8E8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ⓘ",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4A8F2A)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = when {
                    esSupervisor && ciaSeleccionada == null -> {
                        "Selecciona una CIA hija asignada para continuar."
                    }

                    !esSupervisor && parentCiaSeleccionada == null -> {
                        "Primero selecciona una CIA padre."
                    }

                    ciaSeleccionada == null -> {
                        "Selecciona una CIA hija del árbol para continuar."
                    }

                    else -> {
                        "CIA hija seleccionada: ${ciaSeleccionada.nombre}"
                    }
                },
                fontSize = 13.sp,
                color = Color(0xFF3F5F35),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TarjetaCiaPreferente(
    seleccionarPreferente: Boolean,
    onPreferenteChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "⭐", fontSize = 22.sp)

            Spacer(modifier = Modifier.width(8.dp))

            Checkbox(
                checked = seleccionarPreferente,
                onCheckedChange = onPreferenteChange,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = "Seleccionar CIA hija preferente",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Text(
                    text = "La app recordará esta CIA hija para este usuario.",
                    fontSize = 11.sp,
                    color = Color(0xFF777777)
                )
            }
        }
    }
}

@Composable
private fun BotonesSeleccionCia(
    puedeContinuar: Boolean,
    onSeleccionarClick: () -> Unit
)  {
    Button(
        onClick = onSeleccionarClick,
        enabled = puedeContinuar,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2F7D20),
            disabledContainerColor = Color(0xFFE0E0E0)
        ),
        shape = RoundedCornerShape(30.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text(
            text = "Continuar",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

private fun normalizarRolSeleccionCia(rol: String): String {
    val limpio = rol
        .trim()
        .lowercase(Locale.getDefault())
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace(".", "")
        .replace("_", " ")
        .replace(Regex("\\s+"), " ")

    return when (limpio) {
        "super admin", "admin", "administrador" -> "admin"
        "gerente" -> "gerente"
        "ingy supervision", "ing y supervision", "supervisor" -> "supervisor"
        "tecnico", "tecnicos", "técnico", "técnicos" -> "tecnico"
        "invitado" -> "invitado"
        else -> limpio
    }
}
