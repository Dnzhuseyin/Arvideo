package com.example.arvideo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.arvideo.ui.theme.ArvideoTheme
import kotlinx.coroutines.*
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.*

// Resim pozisyon bilgisi
data class ImagePosition(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val confidence: Float
)

class ArCameraActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var targetBitmap: Bitmap? = null
    private var isVideoPlaying by mutableStateOf(false)
    private var detectionConfidence by mutableStateOf(0f)
    private var cameraEnabled by mutableStateOf(false)
    private lateinit var cameraExecutor: ExecutorService
    private var isProcessingImage = false
    
    // AR pozisyon bilgileri
    private var imagePosition by mutableStateOf<ImagePosition?>(null)
    private var videoOffsetX by mutableStateOf(0.dp)
    private var videoOffsetY by mutableStateOf(0.dp)
    private var videoScale by mutableStateOf(1f)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraEnabled = true
            Toast.makeText(this, "Kamera izni verildi", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Kamera izni gerekli", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("ArCamera", "Gerçek AR uygulaması başlatılıyor...")
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Referans fotoğrafını yükle
        loadTargetImage()
        
        // Video player'ı başlat
        initializePlayer()
        
        // Kamera izni kontrol et
        checkCameraPermission()

        setContent {
            ArvideoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ArScreen()
                }
            }
        }
    }
    
    private fun checkCameraPermission() {
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        if (cameraPermission == PackageManager.PERMISSION_GRANTED) {
            cameraEnabled = true
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                exoPlayer!!.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
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
    fun ArScreen() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        
        Box(modifier = Modifier.fillMaxSize()) {
            // Kamera önizlemesi 
            if (cameraEnabled) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            
                            val preview = Preview.Builder()
                                .build()
                                .also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                            
                            // Image analysis - resim pozisyonu tespit edecek
                            val imageAnalyzer = ImageAnalysis.Builder()
                                .setTargetResolution(android.util.Size(640, 480))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                                        processImageForAR(imageProxy)
                                    }
                                }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner, cameraSelector, preview, imageAnalyzer
                            )
                            
                            Log.d("ArCamera", "AR kamera sistemi başlatıldı")
                            
                        } catch (exc: Exception) {
                            Log.e("ArCamera", "Kamera başlatma hatası", exc)
                        }

                    }, ContextCompat.getMainExecutor(context))
                }
            }
            
            // AR Video Overlay - resimin pozisyonuna göre yerleştirilecek
            if (isVideoPlaying && imagePosition != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            layoutParams = ViewGroup.LayoutParams(
                                (imagePosition!!.width * videoScale).toInt(),
                                (imagePosition!!.height * videoScale).toInt()
                            )
                        }
                    },
                    modifier = Modifier
                        .size(
                            width = (imagePosition!!.width * videoScale / 2).dp,
                            height = (imagePosition!!.height * videoScale / 2).dp
                        )
                        .offset(
                            x = videoOffsetX,
                            y = videoOffsetY
                        )
                )
            }
            
            // UI kontrolleri
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text(
                    text = "AR MOD: Resim üzerine video",
                    color = androidx.compose.ui.graphics.Color.Green
                )
                
                Text(
                    text = "Güven: ${(detectionConfidence * 100).toInt()}%",
                    color = androidx.compose.ui.graphics.Color.White
                )
                
                imagePosition?.let { pos ->
                    Text(
                        text = "Pozisyon: (${pos.x}, ${pos.y}) ${pos.width}x${pos.height}",
                        color = androidx.compose.ui.graphics.Color.Cyan
                    )
                }
                
                Text(
                    text = "Video: ${if (isVideoPlaying) "▶️ AR'da Oynatılıyor" else "⏸️ Bekleniyor"}",
                    color = if (isVideoPlaying) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.White
                )
                
                Text(
                    text = "Fırat Üniversitesi plaketini tam karşıya tutun",
                    color = androidx.compose.ui.graphics.Color.Yellow
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            Log.d("ArCamera", "=== AR DEBUG ===")
                            Log.d("ArCamera", "Image position: $imagePosition")
                            Log.d("ArCamera", "Video playing: $isVideoPlaying")
                            Log.d("ArCamera", "Detection confidence: $detectionConfidence")
                            Log.d("ArCamera", "Video scale: $videoScale")
                            Log.d("ArCamera", "Video offset: $videoOffsetX, $videoOffsetY")
                        }
                    ) {
                        Text("Debug")
                    }
                    
                    Button(
                        onClick = { finish() }
                    ) {
                        Text("Geri")
                    }
                }
            }
        }
    }

    // Gerçek AR için resim pozisyonu tespit etme
    private fun processImageForAR(imageProxy: ImageProxy) {
        if (isProcessingImage) {
            imageProxy.close()
            return
        }
        
        isProcessingImage = true
        
        try {
            if (targetBitmap != null) {
                val bitmap = imageProxyToBitmap(imageProxy)
                if (bitmap != null) {
                    // Resmin pozisyonunu tespit et
                    val position = findImagePosition(targetBitmap!!, bitmap)
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        if (position != null && position.confidence > 0.3f) {
                            Log.d("ArCamera", "Resim tespit edildi: $position")
                            
                            // AR pozisyonunu güncelle
                            imagePosition = position
                            detectionConfidence = position.confidence
                            
                            // Video pozisyonunu ayarla
                            updateVideoPosition(position)
                            
                            // Video başlat (eğer başlamamışsa)
                            if (!isVideoPlaying) {
                                startVideo()
                            }
                        } else {
                            // Resim bulunamadı
                            detectionConfidence = position?.confidence ?: 0f
                            if (isVideoPlaying && detectionConfidence < 0.2f) {
                                stopVideo()
                                imagePosition = null
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ArCamera", "AR processing hatası", e)
        } finally {
            isProcessingImage = false
            imageProxy.close()
        }
    }
    
    // Resmin kameradaki pozisyonunu bulma algoritması
    private fun findImagePosition(target: Bitmap, source: Bitmap): ImagePosition? {
        return try {
            val targetSmall = Bitmap.createScaledBitmap(target, 100, 100, true)
            val sourceSmall = Bitmap.createScaledBitmap(source, 320, 240, true)
            
            var bestMatch: ImagePosition? = null
            var bestSimilarity = 0f
            
            // Sliding window ile resmi ara
            val windowSize = 100
            for (x in 0..(sourceSmall.width - windowSize)) {
                for (y in 0..(sourceSmall.height - windowSize)) {
                    val window = Bitmap.createBitmap(
                        sourceSmall, x, y, windowSize, windowSize
                    )
                    
                    val similarity = calculateSimilarity(targetSmall, window)
                    
                    if (similarity > bestSimilarity) {
                        bestSimilarity = similarity
                        bestMatch = ImagePosition(
                            x = (x * source.width / sourceSmall.width),
                            y = (y * source.height / sourceSmall.height),
                            width = (windowSize * source.width / sourceSmall.width),
                            height = (windowSize * source.height / sourceSmall.height),
                            confidence = similarity
                        )
                    }
                }
            }
            
            bestMatch
        } catch (e: Exception) {
            Log.e("ArCamera", "Position finding hatası", e)
            null
        }
    }
    
    // Video pozisyonunu resim pozisyonuna göre ayarla
    private fun updateVideoPosition(position: ImagePosition) {
        // Ekran koordinatlarına dönüştür
        videoOffsetX = (position.x / 4).dp // Kabaca ölçekleme
        videoOffsetY = (position.y / 4).dp
        videoScale = 1.2f // Video biraz büyük olsun
        
        Log.d("ArCamera", "Video pozisyonu güncellendi: offset($videoOffsetX, $videoOffsetY) scale($videoScale)")
    }
    
    private fun calculateSimilarity(bitmap1: Bitmap, bitmap2: Bitmap): Float {
        return try {
            var totalSimilarity = 0f
            val size = 50
            val scaled1 = Bitmap.createScaledBitmap(bitmap1, size, size, true)
            val scaled2 = Bitmap.createScaledBitmap(bitmap2, size, size, true)
            
            for (x in 0 until size) {
                for (y in 0 until size) {
                    val pixel1 = scaled1.getPixel(x, y)
                    val pixel2 = scaled2.getPixel(x, y)
                    
                    val r1 = android.graphics.Color.red(pixel1)
                    val g1 = android.graphics.Color.green(pixel1)
                    val b1 = android.graphics.Color.blue(pixel1)
                    
                    val r2 = android.graphics.Color.red(pixel2)
                    val g2 = android.graphics.Color.green(pixel2)
                    val b2 = android.graphics.Color.blue(pixel2)
                    
                    val diff = abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)
                    val similarity = 1f - (diff / (255f * 3f))
                    totalSimilarity += similarity
                }
            }
            
            totalSimilarity / (size * size)
        } catch (e: Exception) {
            0f
        }
    }
    
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e("ArCamera", "Bitmap dönüştürme hatası", e)
            null
        }
    }

    private fun startVideo() {
        try {
            Log.d("ArCamera", "AR video başlatılıyor...")
            isVideoPlaying = true
            exoPlayer?.play()
            Log.d("ArCamera", "AR video başlatıldı")
        } catch (e: Exception) {
            Log.e("ArCamera", "Video başlatma hatası", e)
            isVideoPlaying = false
        }
    }

    private fun stopVideo() {
        try {
            Log.d("ArCamera", "AR video durduruluyor...")
            isVideoPlaying = false
            exoPlayer?.pause()
            Log.d("ArCamera", "AR video durduruldu")
        } catch (e: Exception) {
            Log.e("ArCamera", "Video durdurma hatası", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ArCamera", "AR Activity kapatılıyor...")
        cameraExecutor.shutdown()
        exoPlayer?.release()
    }
} 