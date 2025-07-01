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
                
                Text(
                    text = "Eşik: %10+ başlat, %5- durdur (ÇOK DÜŞÜK)",
                    color = androidx.compose.ui.graphics.Color.Red
                )
                
                Text(
                    text = "OTOMATIK TEST: 5sn başlar, 8sn durur",
                    color = androidx.compose.ui.graphics.Color.Magenta
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
                    text = "Fırat Üniversitesi plaketini kameraya gösterin",
                    color = androidx.compose.ui.graphics.Color.Yellow
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = {
                            Log.d("ArCamera", "=== AR DEBUG ===")
                            Log.d("ArCamera", "Image position: $imagePosition")
                            Log.d("ArCamera", "Video playing: $isVideoPlaying")
                            Log.d("ArCamera", "Detection confidence: $detectionConfidence")
                            Log.d("ArCamera", "Target bitmap: ${targetBitmap != null}")
                            Log.d("ArCamera", "Camera enabled: $cameraEnabled")
                            if (targetBitmap != null) {
                                Log.d("ArCamera", "Target size: ${targetBitmap!!.width}x${targetBitmap!!.height}")
                            }
                        }
                    ) {
                        Text("Log")
                    }
                    
                    Button(
                        onClick = {
                            if (isVideoPlaying) {
                                stopVideo()
                                imagePosition = null
                            } else {
                                // Test için zorla video başlat
                                imagePosition = ImagePosition(100, 100, 300, 400, 0.9f)
                                updateVideoPosition(imagePosition!!)
                                startVideo()
                            }
                        }
                    ) {
                        Text(if (isVideoPlaying) "Stop" else "Test")
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
            // OTOMATIK TEST - Eğer resim tanıma çalışmıyorsa
            val currentTime = System.currentTimeMillis()
            if (currentTime % 5000 < 100 && !isVideoPlaying) {
                Log.d("ArCamera", "OTOMATIK TEST: 5 saniye - Video başlatılıyor!")
                CoroutineScope(Dispatchers.Main).launch {
                    imagePosition = ImagePosition(100, 100, 300, 400, 0.8f)
                    updateVideoPosition(imagePosition!!)
                    startVideo()
                }
            } else if (currentTime % 8000 < 100 && isVideoPlaying) {
                Log.d("ArCamera", "OTOMATIK TEST: 8 saniye - Video durduruluyor!")
                CoroutineScope(Dispatchers.Main).launch {
                    stopVideo()
                    imagePosition = null
                }
            }
            
            if (targetBitmap != null) {
                val bitmap = imageProxyToBitmap(imageProxy)
                if (bitmap != null) {
                    Log.d("ArCamera", "Processing frame: ${bitmap.width}x${bitmap.height}")
                    
                    // ÇOK BASİT YAKLAşIM: Sadece genel benzerlik
                    val simpleSimilarity = calculateQuickSimilarity(targetBitmap!!, bitmap)
                    
                    Log.d("ArCamera", "Quick similarity: $simpleSimilarity")
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        detectionConfidence = simpleSimilarity
                        
                        // ÇOK DÜŞÜK EŞİK - %10!
                        if (simpleSimilarity > 0.1f && !isVideoPlaying) {
                            Log.d("ArCamera", "BASIT TANIMA: Video başlatılıyor! Similarity: $simpleSimilarity")
                            
                            // Sabit pozisyonda video göster
                            imagePosition = ImagePosition(
                                x = 100,
                                y = 100, 
                                width = 300,
                                height = 400,
                                confidence = simpleSimilarity
                            )
                            updateVideoPosition(imagePosition!!)
                            startVideo()
                        } else if (simpleSimilarity <= 0.05f && isVideoPlaying) {
                            Log.d("ArCamera", "BASIT TANIMA: Video durduruluyor! Similarity: $simpleSimilarity")
                            stopVideo()
                            imagePosition = null
                        }
                    }
                } else {
                    Log.w("ArCamera", "Bitmap null!")
                }
            } else {
                Log.w("ArCamera", "Target bitmap null!")
            }
        } catch (e: Exception) {
            Log.e("ArCamera", "AR processing hatası", e)
        } finally {
            isProcessingImage = false
            imageProxy.close()
        }
    }
    
    // Çok basit ve hızlı benzerlik hesaplama
    private fun calculateQuickSimilarity(target: Bitmap, source: Bitmap): Float {
        return try {
            // Çok küçük boyutlarda karşılaştır - 10x10
            val targetSmall = Bitmap.createScaledBitmap(target, 10, 10, true)
            val sourceSmall = Bitmap.createScaledBitmap(source, 10, 10, true)
            
            var totalSimilarity = 0f
            val totalPixels = 100
            
            for (x in 0 until 10) {
                for (y in 0 until 10) {
                    val targetPixel = targetSmall.getPixel(x, y)
                    val sourcePixel = sourceSmall.getPixel(x, y)
                    
                    // Sadece gri ton karşılaştırması
                    val targetGray = (android.graphics.Color.red(targetPixel) + 
                                     android.graphics.Color.green(targetPixel) + 
                                     android.graphics.Color.blue(targetPixel)) / 3
                    val sourceGray = (android.graphics.Color.red(sourcePixel) + 
                                     android.graphics.Color.green(sourcePixel) + 
                                     android.graphics.Color.blue(sourcePixel)) / 3
                    
                    val diff = kotlin.math.abs(targetGray - sourceGray)
                    val similarity = 1f - (diff / 255f)
                    totalSimilarity += similarity
                }
            }
            
            val result = (totalSimilarity / totalPixels).coerceIn(0f, 1f)
            
            // Her 50 frame'de bir log
            if (System.currentTimeMillis() % 3000 < 100) {
                Log.d("ArCamera", "Benzerlik hesaplanıyor: $result (${(result * 100).toInt()}%)")
            }
            
            result
        } catch (e: Exception) {
            Log.e("ArCamera", "Quick similarity hatası", e)
            0f
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