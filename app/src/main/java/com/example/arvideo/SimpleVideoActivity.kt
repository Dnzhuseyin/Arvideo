package com.example.arvideo

import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.arvideo.ui.theme.ArvideoTheme

class SimpleVideoActivity : ComponentActivity() {
    
    private var exoPlayer: ExoPlayer? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("SimpleVideo", "Activity başlatılıyor...")
        
        // ExoPlayer'ı burada oluştur
        initializePlayer()
        
        setContent {
            ArvideoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SimpleVideoScreen()
                }
            }
        }
    }
    
    private fun initializePlayer() {
        try {
            Log.d("SimpleVideo", "Player başlatılıyor...")
            
            exoPlayer = ExoPlayer.Builder(this).build()
            
            // Resource ID ile video yükle
            val resourceId = resources.getIdentifier("ar_video", "raw", packageName)
            Log.d("SimpleVideo", "Resource ID: $resourceId")
            
            if (resourceId != 0) {
                val videoUri = Uri.parse("android.resource://$packageName/$resourceId")
                Log.d("SimpleVideo", "Video URI: $videoUri")
                
                val mediaItem = MediaItem.fromUri(videoUri)
                exoPlayer!!.setMediaItem(mediaItem)
                exoPlayer!!.prepare()
                
                Log.d("SimpleVideo", "Player hazır")
            } else {
                Log.e("SimpleVideo", "Video bulunamadı!")
            }
            
        } catch (e: Exception) {
            Log.e("SimpleVideo", "Player başlatma hatası", e)
        }
    }
    
    @Composable
    fun SimpleVideoScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            Text(
                text = "Basit Video Test",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            // Video player - sadece AndroidView
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        Log.d("SimpleVideo", "Play butonuna basıldı")
                        exoPlayer?.play()
                    }
                ) {
                    Text("Oynat")
                }
                
                Button(
                    onClick = {
                        Log.d("SimpleVideo", "Pause butonuna basıldı") 
                        exoPlayer?.pause()
                    }
                ) {
                    Text("Durdur")
                }
                
                Button(
                    onClick = {
                        Log.d("SimpleVideo", "Geri butonuna basıldı")
                        finish()
                    }
                ) {
                    Text("Geri")
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("SimpleVideo", "Activity kapatılıyor...")
        exoPlayer?.release()
    }
} 