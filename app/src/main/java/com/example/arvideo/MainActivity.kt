package com.example.arvideo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.arvideo.ui.theme.ArvideoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArvideoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    @Composable
    fun MainScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AR Video Uygulaması",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Fırat Üniversitesi videosu için olan buton
            Button(
                onClick = {
                    val intent = Intent(this@MainActivity, SimpleVideoActivity::class.java)
                    startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Basit Video Test")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    val intent = Intent(this@MainActivity, VideoTestActivity::class.java)
                    startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Video Dosyası Test")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    val intent = Intent(this@MainActivity, ArCameraActivity::class.java)
                    startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Custom AR Kamera (Eski)")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ARCore - Professional version
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "🚀 PROFESSIONAL ARCore",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "Google ARCore ile gerçek AR deneyimi",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = {
                            val intent = Intent(this@MainActivity, ARCoreActivity::class.java)
                            startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ARCore AR Kamera")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Fırat Üniversitesi plaketi ile video oynatılacak",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WelcomeScreenPreview() {
    ArvideoTheme {
        MainScreen()
    }
}