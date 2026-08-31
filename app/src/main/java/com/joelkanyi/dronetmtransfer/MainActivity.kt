package com.joelkanyi.dronetmtransfer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.joelkanyi.dronetmtransfer.ui.theme.DronetmtransferTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DronetmtransferTheme {
                TransferScreen()
            }
        }
    }
}
