package com.euforia.jine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.euforia.jine.ui.theme.ZpixFont

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            JineScreen()
        }
    }
}

@Composable
fun JineScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFE8F5))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "JINE",
            fontFamily = ZpixFont,
            fontSize = 32.sp,
            color = Color(0xFF7646A8)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "p-chaaaan!!",
            fontFamily = ZpixFont,
            fontSize = 18.sp
        )

        Text(
            text = "Are you there?",
            fontFamily = ZpixFont,
            fontSize = 18.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun JinePreview() {
    JineScreen()
}