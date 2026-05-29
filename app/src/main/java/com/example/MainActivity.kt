package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.BrowserScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    BrowserScreen(
                        onEmailDeveloper = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_EMAIL, arrayOf("Sabbir347iii@gmail.com"))
                                putExtra(Intent.EXTRA_SUBJECT, "Sabbir Browser Developer Inquiry")
                                putExtra(Intent.EXTRA_TEXT, "Hello Md Sabbir,\n\nI am contacting you regarding your Advanced Browser application.")
                            }
                            try {
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "No email client installed on this device",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }
}
