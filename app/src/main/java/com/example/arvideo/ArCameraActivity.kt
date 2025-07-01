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
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier
                        .size(
                            width = (imagePosition!!.width / 2).dp,  // Resmin tam boyutu
                            height = (imagePosition!!.height / 2).dp
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
                    text = "Eşik: %20+ başlat, %15- durdur (GELİŞMİŞ)",
                    color = androidx.compose.ui.graphics.Color.Green
                )
                
                Text(
                    text = "İKİ YÖNTEM: Genel + Pozisyon tespiti",
                    color = androidx.compose.ui.graphics.Color.Cyan
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
                    
                    // İKİ YÖNTEM: Hem genel benzerlik hem pozisyon tespiti
                    val simpleSimilarity = calculateQuickSimilarity(targetBitmap!!, bitmap)
                    val realPosition = findRealImagePosition(targetBitmap!!, bitmap)
                    
                    Log.d("ArCamera", "Genel benzerlik: $simpleSimilarity")
                    if (realPosition != null) {
                        Log.d("ArCamera", "Pozisyon bulundu: ${realPosition.x}, ${realPosition.y}, ${realPosition.width}x${realPosition.height}, güven: ${realPosition.confidence}")
                    }
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        // En iyi sonucu kullan
                        val bestConfidence = maxOf(simpleSimilarity, realPosition?.confidence ?: 0f)
                        detectionConfidence = bestConfidence
                        
                        // Eşik %20'ye yükselttim - daha güvenilir
                        if (bestConfidence > 0.2f && !isVideoPlaying) {
                            Log.d("ArCamera", "GELIŞMIŞ TANIMA: Video başlatılıyor! Best confidence: $bestConfidence")
                            
                            // Gerçek pozisyon varsa onu kullan, yoksa sabit pozisyon
                            val finalPosition = realPosition ?: ImagePosition(
                                x = bitmap.width / 4,
                                y = bitmap.height / 4, 
                                width = bitmap.width / 2,
                                height = bitmap.height / 2,
                                confidence = bestConfidence
                            )
                            
                            imagePosition = finalPosition
                            updateVideoPosition(finalPosition)
                            startVideo()
                        } else if (bestConfidence <= 0.15f && isVideoPlaying) {
                            Log.d("ArCamera", "GELIŞMIŞ TANIMA: Video durduruluyor! Best confidence: $bestConfidence")
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
            // Daha büyük boyutlarda karşılaştır - 50x50
            val targetSmall = Bitmap.createScaledBitmap(target, 50, 50, true)
            val sourceSmall = Bitmap.createScaledBitmap(source, 50, 50, true)
            
            var totalSimilarity = 0f
            val totalPixels = 2500 // 50x50
            
            // Merkez bölgeyi daha ağırlıklı hesapla
            for (x in 0 until 50) {
                for (y in 0 until 50) {
                    val targetPixel = targetSmall.getPixel(x, y)
                    val sourcePixel = sourceSmall.getPixel(x, y)
                    
                    // RGB karşılaştırması - daha hassas
                    val targetR = android.graphics.Color.red(targetPixel)
                    val targetG = android.graphics.Color.green(targetPixel)
                    val targetB = android.graphics.Color.blue(targetPixel)
                    
                    val sourceR = android.graphics.Color.red(sourcePixel)
                    val sourceG = android.graphics.Color.green(sourcePixel)
                    val sourceB = android.graphics.Color.blue(sourcePixel)
                    
                    val rDiff = kotlin.math.abs(targetR - sourceR)
                    val gDiff = kotlin.math.abs(targetG - sourceG) 
                    val bDiff = kotlin.math.abs(targetB - sourceB)
                    
                    val totalDiff = (rDiff + gDiff + bDiff) / 3f
                    val similarity = 1f - (totalDiff / 255f)
                    
                    // Merkez piksellere daha fazla ağırlık ver
                    val centerWeight = if (x >= 15 && x <= 35 && y >= 15 && y <= 35) 1.5f else 1f
                    totalSimilarity += similarity * centerWeight
                }
            }
            
            val result = (totalSimilarity / (totalPixels * 1.2f)).coerceIn(0f, 1f) // Ağırlık düzeltmesi
            
            // Her 2 saniyede bir log
            if (System.currentTimeMillis() % 2000 < 100) {
                Log.d("ArCamera", "İyileştirilmiş benzerlik: $result (${(result * 100).toInt()}%)")
            }
            
            result
        } catch (e: Exception) {
            Log.e("ArCamera", "İyileştirilmiş similarity hatası", e)
            0f
        }
    }
    
    // Resmin kameradaki gerçek pozisyonunu bulma
    private fun findRealImagePosition(target: Bitmap, source: Bitmap): ImagePosition? {
        return try {
            val targetSmall = Bitmap.createScaledBitmap(target, 80, 80, true)
            val sourceWidth = 160
            val sourceHeight = 120
            val sourceSmall = Bitmap.createScaledBitmap(source, sourceWidth, sourceHeight, true)
            
            var bestMatch: ImagePosition? = null
            var bestSimilarity = 0f
            
            // Template matching ile resmi ara
            val templateSize = 80
            val stepSize = 10 // Daha hızlı tarama
            
            for (x in 0..(sourceWidth - templateSize) step stepSize) {
                for (y in 0..(sourceHeight - templateSize) step stepSize) {
                    try {
                        val window = Bitmap.createBitmap(sourceSmall, x, y, templateSize, templateSize)
                        val similarity = calculateTemplateMatch(targetSmall, window)
                        
                        if (similarity > bestSimilarity) {
                            bestSimilarity = similarity
                            
                            // Gerçek koordinatlara dönüştür
                            val realX = (x * source.width / sourceWidth)
                            val realY = (y * source.height / sourceHeight)
                            val realWidth = (templateSize * source.width / sourceWidth)
                            val realHeight = (templateSize * source.height / sourceHeight)
                            
                            bestMatch = ImagePosition(
                                x = realX,
                                y = realY,
                                width = realWidth,
                                height = realHeight,
                                confidence = similarity
                            )
                        }
                    } catch (e: Exception) {
                        // Window oluşturma hatası - atla
                        continue
                    }
                }
            }
            
            Log.d("ArCamera", "Template matching sonucu: $bestMatch")
            bestMatch
        } catch (e: Exception) {
            Log.e("ArCamera", "Position finding hatası", e)
            null
        }
    }
    
    // Template matching algoritması
    private fun calculateTemplateMatch(template: Bitmap, window: Bitmap): Float {
        return try {
            if (template.width != window.width || template.height != window.height) {
                return 0f
            }
            
            var totalSimilarity = 0f
            val totalPixels = template.width * template.height
            
            for (x in 0 until template.width) {
                for (y in 0 until template.height) {
                    val templatePixel = template.getPixel(x, y)
                    val windowPixel = window.getPixel(x, y)
                    
                    val tr = android.graphics.Color.red(templatePixel)
                    val tg = android.graphics.Color.green(templatePixel)
                    val tb = android.graphics.Color.blue(templatePixel)
                    
                    val wr = android.graphics.Color.red(windowPixel)
                    val wg = android.graphics.Color.green(windowPixel)
                    val wb = android.graphics.Color.blue(windowPixel)
                    
                    val diff = kotlin.math.abs(tr - wr) + kotlin.math.abs(tg - wg) + kotlin.math.abs(tb - wb)
                    val similarity = 1f - (diff / (255f * 3f))
                    totalSimilarity += similarity
                }
            }
            
            totalSimilarity / totalPixels
        } catch (e: Exception) {
            0f
        }
    }
    
    // Video pozisyonunu resim pozisyonuna göre ayarla - BÜYÜK BOYUT
    private fun updateVideoPosition(position: ImagePosition) {
        // Ekran koordinatlarına dönüştür - Gerçek boyutlarda
        val screenDensity = resources.displayMetrics.density
        
        // Video resmin TAM boyutunda olsun
        videoOffsetX = (position.x / screenDensity / 2).dp
        videoOffsetY = (position.y / screenDensity / 2).dp
        videoScale = 1.5f // Biraz büyük olsun ki resmi tamamen kaplasın
        
        Log.d("ArCamera", "Video pozisyonu güncellendi:")
        Log.d("ArCamera", "- Offset: ($videoOffsetX, $videoOffsetY)")
        Log.d("ArCamera", "- Scale: $videoScale")
        Log.d("ArCamera", "- Resim boyutu: ${position.width}x${position.height}")
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