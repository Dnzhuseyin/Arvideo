package com.example.arvideo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.arvideo.ui.theme.ArvideoTheme
import java.io.IOException

class ArCameraActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var targetBitmap: Bitmap? = null
    private var isVideoPlaying by mutableStateOf(false)
    private var detectionConfidence by mutableStateOf(0f)
    private var testCounter by mutableStateOf(0)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Kamera izni verildi", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Kamera izni gerekli", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("ArCamera", "ArCameraActivity başlatılıyor (Basit Mod)...")
        
        // Referans fotoğrafını yükle
        loadTargetImage()
        
        // Video player'ı başlat
        initializePlayer()

        setContent {
            ArvideoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SimpleTestScreen()
                }
            }
        }
    }

    private fun loadTargetImage() {
        try {
            Log.d("ArCamera", "Hedef resim yükleniyor...")
            val inputStream = assets.open("target_image.jpg")
            targetBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (targetBitmap != null) {
                Log.d("ArCamera", "Hedef resim başarıyla yüklendi: ${targetBitmap!!.width}x${targetBitmap!!.height}")
            } else {
                Log.e("ArCamera", "Hedef resim decode edilemedi")
            }
        } catch (e: IOException) {
            Log.e("ArCamera", "Target image bulunamadı: ${e.message}")
        }
    }
    
    private fun initializePlayer() {
        try {
            Log.d("ArCamera", "Player başlatılıyor...")
            exoPlayer = ExoPlayer.Builder(this).build()
            
            val resourceId = resources.getIdentifier("ar_video", "raw", packageName)
            Log.d("ArCamera", "Resource ID: $resourceId")
            
            if (resourceId != 0) {
                val videoUri = Uri.parse("android.resource://$packageName/$resourceId")
                Log.d("ArCamera", "Video URI: $videoUri")
                
                val mediaItem = MediaItem.fromUri(videoUri)
                exoPlayer!!.setMediaItem(mediaItem)
                exoPlayer!!.prepare()
                
                Log.d("ArCamera", "Player hazır")
            } else {
                Log.e("ArCamera", "Video dosyası bulunamadı!")
            }
            
        } catch (e: Exception) {
            Log.e("ArCamera", "Player başlatma hatası", e)
        }
    }

    @Composable
    fun SimpleTestScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            Text(
                text = "AR Camera Test (Basit Mod)",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            Text(
                text = "Hedef Resim: ${if (targetBitmap != null) "✓ Yüklendi (${targetBitmap!!.width}x${targetBitmap!!.height})" else "✗ Yüklenemedi"}",
                color = if (targetBitmap != null) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = "Video Player: ${if (exoPlayer != null) "✓ Hazır" else "✗ Hata"}",
                color = if (exoPlayer != null) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = "Video Durumu: ${if (isVideoPlaying) "▶️ Oynatılıyor" else "⏸️ Durduruldu"}",
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = "Test Sayacı: $testCounter",
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        testCounter++
                        startVideo()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Video Başlat")
                }
                
                Button(
                    onClick = {
                        testCounter++
                        stopVideo()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Video Durdur")
                }
                
                Button(
                    onClick = {
                        testCounter++
                        detectionConfidence = (0..100).random() / 100f
                        Log.d("ArCamera", "Sahte detection: $detectionConfidence")
                        Toast.makeText(this@ArCameraActivity, "Sahte tanıma: ${(detectionConfidence * 100).toInt()}%", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sahte Tanıma Testi")
                }
                
                Button(
                    onClick = {
                        Log.d("ArCamera", "Debug bilgileri:")
                        Log.d("ArCamera", "- Target bitmap: ${targetBitmap != null}")
                        Log.d("ArCamera", "- ExoPlayer: ${exoPlayer != null}")
                        Log.d("ArCamera", "- Video playing: $isVideoPlaying")
                        Log.d("ArCamera", "- Test counter: $testCounter")
                        Toast.makeText(this@ArCameraActivity, "Debug logları gönderildi", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Debug Logları")
                }
                
                Button(
                    onClick = {
                        finish()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Geri")
                }
            }
        }
    }

    private fun startVideo() {
        try {
            Log.d("ArCamera", "Video başlatılıyor...")
            isVideoPlaying = true
            exoPlayer?.play()
            Log.d("ArCamera", "Video başlatıldı")
        } catch (e: Exception) {
            Log.e("ArCamera", "Video başlatma hatası", e)
            isVideoPlaying = false
        }
    }

    private fun stopVideo() {
        try {
            Log.d("ArCamera", "Video durduruluyor...")
            isVideoPlaying = false
            exoPlayer?.pause()
            Log.d("ArCamera", "Video durduruldu")
        } catch (e: Exception) {
            Log.e("ArCamera", "Video durdurma hatası", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ArCamera", "Activity kapatılıyor...")
        exoPlayer?.release()
    }
} 