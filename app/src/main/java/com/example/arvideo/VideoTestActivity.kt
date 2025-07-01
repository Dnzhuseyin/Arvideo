package com.example.arvideo

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.arvideo.ui.theme.ArvideoTheme

class VideoTestActivity : ComponentActivity() {
    
    private var exoPlayer: ExoPlayer? = null
    private var isPlaying by mutableStateOf(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("VideoTest", "VideoTestActivity başlatılıyor...")
        
        setContent {
            ArvideoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VideoTestScreen()
                }
            }
        }
    }
    
    @Composable
    fun VideoTestScreen() {
        val context = LocalContext.current
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            Text(
                text = "Video Test",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            // Video oynatıcı
            if (isPlaying) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = getExoPlayer()
                            useController = true
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                600
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Text(
                text = "Video Durumu: ${if (isPlaying) "▶️ Oynatılıyor" else "⏸️ Durduruldu"}",
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        if (isPlaying) {
                            stopVideo()
                        } else {
                            startVideo()
                        }
                    }
                ) {
                    Text(if (isPlaying) "Durdur" else "Oynat")
                }
                
                Button(
                    onClick = {
                        finish()
                    }
                ) {
                    Text("Geri")
                }
            }
        }
    }
    
    private fun getExoPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            try {
                Log.d("VideoTest", "ExoPlayer oluşturuluyor...")
                exoPlayer = ExoPlayer.Builder(this).build()
                
                // Farklı URI formatlarını dene
                val videoUris = listOf(
                    "android.resource://$packageName/raw/ar_video",
                    "android.resource://$packageName/${R.raw.ar_video}",
                    "android.resource://$packageName/raw/ar_video.mp4"
                )
                
                for (uri in videoUris) {
                    try {
                        Log.d("VideoTest", "Denenen URI: $uri")
                        val mediaItem = MediaItem.fromUri(Uri.parse(uri))
                        exoPlayer!!.setMediaItem(mediaItem)
                        exoPlayer!!.prepare()
                        Log.d("VideoTest", "Video başarıyla yüklendi: $uri")
                        break
                    } catch (e: Exception) {
                        Log.w("VideoTest", "URI başarısız: $uri - ${e.message}")
                    }
                }
                
                exoPlayer!!.repeatMode = Player.REPEAT_MODE_ALL
                Log.d("VideoTest", "ExoPlayer hazır")
                
            } catch (e: Exception) {
                Log.e("VideoTest", "ExoPlayer oluşturma hatası", e)
                Toast.makeText(this, "Video oynatıcı başlatılamadı: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        return exoPlayer!!
    }
    
    private fun startVideo() {
        try {
            Log.d("VideoTest", "Video başlatılıyor...")
            isPlaying = true
            val player = getExoPlayer()
            player.playWhenReady = true
            player.play()
            Log.d("VideoTest", "Video başlatıldı")
        } catch (e: Exception) {
            Log.e("VideoTest", "Video başlatma hatası", e)
            isPlaying = false
            Toast.makeText(this, "Video başlatılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopVideo() {
        try {
            Log.d("VideoTest", "Video durduruluyor...")
            isPlaying = false
            exoPlayer?.pause()
            Log.d("VideoTest", "Video durduruldu")
        } catch (e: Exception) {
            Log.e("VideoTest", "Video durdurma hatası", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        Log.d("VideoTest", "VideoTestActivity kapatıldı")
    }
} 