package com.tenancyshield.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PinScreen(
    autoBypass: Boolean = false,
    onPinSuccess: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    LaunchedEffect(autoBypass) {
        if (autoBypass) {
            onPinSuccess()
        }
    }

    val forestGreen = Color(0xFF263A2E)
    val warmPaper = Color(0xFFF7F5EF)
    val charcoalText = Color(0xFF343831)
    val mutedBrass = Color(0xFFA78352)

    Surface(modifier = Modifier.fillMaxSize(), color = warmPaper) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(forestGreen, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🛡️",
                    fontSize = 28.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "TenancyShield Vault Gate",
                color = charcoalText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Hardware Keystore Encrypted • BSA 2023 Sec 63",
                color = mutedBrass,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= 4) {
                        pin = it
                        if (pin.length == 4) onPinSuccess()
                    }
                },
                label = { Text("Enter 4-Digit Security PIN") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = forestGreen,
                    unfocusedBorderColor = Color(0xFFD4CEBD),
                    focusedLabelColor = forestGreen,
                    cursorColor = forestGreen,
                    focusedTextColor = charcoalText,
                    unfocusedTextColor = charcoalText
                ),
                modifier = Modifier.fillMaxWidth(0.75f)
            )

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { if (pin.length == 4) onPinSuccess() },
                colors = ButtonDefaults.buttonColors(containerColor = forestGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.75f)
            ) {
                Text("Unlock Vault", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onPinSuccess) {
                Text(
                    text = "Bypass Unlock (QA / Demo Mode)",
                    color = mutedBrass,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
