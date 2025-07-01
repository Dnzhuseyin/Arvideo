package com.example.arvideo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.arvideo.ui.theme.ArvideoTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ArCameraActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var exoPlayer: ExoPlayer? = null
    private var targetBitmap: Bitmap? = null
    private var isVideoPlaying by mutableStateOf(false)
    private var detectionConfidence by mutableStateOf(0f)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // İzin verildi, kamerayı başlat
            setupContent()
        } else {
            Toast.makeText(this, "Kamera izni gerekli", Toast.LENGTH_SHORT).show()
            finish() // Activity'yi kapat
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("ArCamera", "ArCameraActivity başlatılıyor...")
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Referans fotoğrafını assets'tan yükle (varsayılan olarak "target_image.jpg" kullanacağım)
        loadTargetImage()

        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        Log.d("ArCamera", "Kamera izni durumu: $cameraPermission")
        
        if (cameraPermission == PackageManager.PERMISSION_GRANTED) {
            Log.d("ArCamera", "Kamera izni var, content setup ediliyor...")
            setupContent()
        } else {
            Log.d("ArCamera", "Kamera izni yok, izin isteniyor...")
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
            Toast.makeText(this, "Hedef resim yüklenemedi!", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupContent() {
        setContent {
            ArvideoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ArCameraScreen()
                }
            }
        }
    }

    @Composable
    fun ArCameraScreen() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        
        Box(modifier = Modifier.fillMaxSize()) {
            // Kamera önizlemesi
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    
                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setTargetResolution(android.util.Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build().also {
                            it.setAnalyzer(cameraExecutor, { imageProxy ->
                                processImage(imageProxy)
                            })
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    
                    Log.d("ArCamera", "Kamera başlatılıyor...")

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelector, preview, imageAnalyzer
                        )
                        Log.d("ArCamera", "Kamera başarıyla başlatıldı")
                    } catch (exc: Exception) {
                        Log.e("ArCamera", "Kamera başlatma hatası", exc)
                        Toast.makeText(context, "Kamera başlatılamadı: ${exc.message}", Toast.LENGTH_LONG).show()
                    }

                }, ContextCompat.getMainExecutor(context))
            }

            // Video oynatıcı (overlay)
            if (isVideoPlaying) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = getExoPlayer()
                            useController = false
                            layoutParams = ViewGroup.LayoutParams(
                                400,
                                300
                            )
                        }
                    },
                    modifier = Modifier
                        .size(400.dp, 300.dp)
                        .align(Alignment.Center)
                )
            }

            // UI kontrolleri
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Güven: ${(detectionConfidence * 100).toInt()}%",
                    color = androidx.compose.ui.graphics.Color.White
                )
                
                Text(
                    text = "Hedef Resim: ${if (targetBitmap != null) "✓ Yüklendi (${targetBitmap!!.width}x${targetBitmap!!.height})" else "✗ Yüklenemedi"}",
                    color = if (targetBitmap != null) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
                )
                
                Text(
                    text = "Video Durumu: ${if (isVideoPlaying) "▶️ Oynatılıyor" else "⏸️ Durduruldu"}",
                    color = androidx.compose.ui.graphics.Color.White
                )
                
                Text(
                    text = "Test: 3sn'de video başlar, 6sn'de durur",
                    color = androidx.compose.ui.graphics.Color.Yellow
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = { 
                            if (isVideoPlaying) {
                                stopVideo()
                            } else {
                                startVideo()
                            }
                        }
                    ) {
                        Text("Video")
                    }
                    
                    Button(
                        onClick = { 
                            Log.d("ArCamera", "Manuel test başlatıldı")
                            detectionConfidence = 0.8f
                            startVideo()
                        }
                    ) {
                        Text("Zorla")
                    }
                    
                    Button(
                        onClick = {
                            Log.d("ArCamera", "Test: targetBitmap = ${targetBitmap != null}")
                            if (targetBitmap != null) {
                                Log.d("ArCamera", "Target boyut: ${targetBitmap!!.width}x${targetBitmap!!.height}")
                            }
                            Toast.makeText(this@ArCameraActivity, "Test logları gönderildi", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Test")
                    }
                }
            }
        }
    }

    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            try {
                Log.d("ArCamera", "Görüntü işleniyor...")
                
                // BASİT TEST: Her 3 saniyede bir video başlat
                val currentTime = System.currentTimeMillis()
                if (currentTime % 3000 < 100) { // Her 3 saniyede tetikle
                    if (!isVideoPlaying) {
                        Log.d("ArCamera", "3 saniye test - Video başlatılıyor!")
                        detectionConfidence = 0.9f
                        startVideo()
                    }
                } else if (currentTime % 6000 < 100) { // Her 6 saniyede durdur
                    if (isVideoPlaying) {
                        Log.d("ArCamera", "6 saniye test - Video durduruluyor!")
                        detectionConfidence = 0.1f
                        stopVideo()
                    }
                }
                
                // Hedef fotoğraf yüklendi mi kontrolü
                if (targetBitmap != null) {
                    Log.d("ArCamera", "Hedef fotoğraf mevcut: ${targetBitmap!!.width}x${targetBitmap!!.height}")
                    
                    // Basit renk analizi yap
                    try {
                        val bitmap = imageProxyToBitmap(imageProxy)
                        Log.d("ArCamera", "Kamera bitmap: ${bitmap.width}x${bitmap.height}")
                        
                        // Basit renk karşılaştırması
                        val similarity = calculateSimpleColorSimilarity(targetBitmap!!, bitmap)
                        detectionConfidence = similarity
                        
                        Log.d("ArCamera", "Basit renk benzerlik: $similarity")
                        
                        // Çok düşük eşik - test için
                        if (similarity > 0.05f && !isVideoPlaying) {
                            Log.d("ArCamera", "RENK MATCH! Video başlatılıyor!")
                            startVideo()
                        }
                        
                    } catch (e: Exception) {
                        Log.e("ArCamera", "Bitmap işleme hatası", e)
                    }
                } else {
                    Log.w("ArCamera", "Hedef fotoğraf NULL!")
                }
                
            } catch (e: Exception) {
                Log.e("ArCamera", "Image processing hatası", e)
            }
        } else {
            Log.w("ArCamera", "MediaImage null")
        }
        imageProxy.close()
    }
    
    // Basit renk benzerliği hesapla
    private fun calculateSimpleColorSimilarity(target: Bitmap, source: Bitmap): Float {
        try {
            // Resimleri küçük boyuta getir
            val targetSmall = Bitmap.createScaledBitmap(target, 10, 10, true)
            val sourceSmall = Bitmap.createScaledBitmap(source, 10, 10, true)
            
            var totalSimilarity = 0f
            val totalPixels = 100 // 10x10
            
            for (x in 0 until 10) {
                for (y in 0 until 10) {
                    val targetPixel = targetSmall.getPixel(x, y)
                    val sourcePixel = sourceSmall.getPixel(x, y)
                    
                    val targetRed = android.graphics.Color.red(targetPixel)
                    val targetGreen = android.graphics.Color.green(targetPixel)
                    val targetBlue = android.graphics.Color.blue(targetPixel)
                    
                    val sourceRed = android.graphics.Color.red(sourcePixel)
                    val sourceGreen = android.graphics.Color.green(sourcePixel)
                    val sourceBlue = android.graphics.Color.blue(sourcePixel)
                    
                    val diff = kotlin.math.abs(targetRed - sourceRed) + 
                              kotlin.math.abs(targetGreen - sourceGreen) + 
                              kotlin.math.abs(targetBlue - sourceBlue)
                              
                    val similarity = 1f - (diff / (255f * 3f))
                    totalSimilarity += similarity
                }
            }
            
            return totalSimilarity / totalPixels
        } catch (e: Exception) {
            Log.e("ArCamera", "Color similarity hesaplama hatası", e)
            return 0f
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        return try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            
            // JPEG decode etmeyi dene
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                Log.d("ArCamera", "JPEG decode başarılı: ${bitmap.width}x${bitmap.height}")
                return bitmap
            }
            
            // Eğer JPEG değilse, YUV formatını dene
            Log.d("ArCamera", "JPEG decode başarısız, YUV deneniyor...")
            
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer  
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            
            yBuffer.get(nv21, 0, ySize)
            uBuffer.get(nv21, ySize, uSize)
            vBuffer.get(nv21, ySize + uSize, vSize)

            val yuvImage = android.graphics.YuvImage(
                nv21, 
                android.graphics.ImageFormat.NV21, 
                imageProxy.width, 
                imageProxy.height, 
                null
            )
            
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height), 
                75, 
                out
            )
            
            val jpegBytes = out.toByteArray()
            val resultBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            
            Log.d("ArCamera", "YUV decode başarılı: ${resultBitmap.width}x${resultBitmap.height}")
            resultBitmap
            
        } catch (e: Exception) {
            Log.e("ArCamera", "imageProxyToBitmap hatası", e)
            // Fallback: boş bitmap döndür
            Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        }
    }

    private fun getExoPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            try {
                Log.d("ArCamera", "ExoPlayer oluşturuluyor...")
                exoPlayer = ExoPlayer.Builder(this).build()
                
                // Doğru URI formatı
                val resourceId = resources.getIdentifier("ar_video", "raw", packageName)
                Log.d("ArCamera", "Resource ID: $resourceId")
                
                if (resourceId != 0) {
                    val videoUri = Uri.parse("android.resource://$packageName/$resourceId")
                    Log.d("ArCamera", "Video URI: $videoUri")
                    
                    val mediaItem = MediaItem.fromUri(videoUri)
                    exoPlayer!!.setMediaItem(mediaItem)
                    exoPlayer!!.repeatMode = Player.REPEAT_MODE_ALL
                    exoPlayer!!.prepare()
                    
                    Log.d("ArCamera", "ExoPlayer hazır")
                } else {
                    Log.e("ArCamera", "Video dosyası bulunamadı!")
                    Toast.makeText(this, "Video dosyası bulunamadı!", Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                Log.e("ArCamera", "ExoPlayer oluşturma hatası", e)
                Toast.makeText(this, "Video oynatıcı başlatılamadı: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        return exoPlayer!!
    }

    private fun startVideo() {
        try {
            Log.d("ArCamera", "Video başlatılıyor...")
            isVideoPlaying = true
            val player = getExoPlayer()
            player.playWhenReady = true
            player.play()
            Log.d("ArCamera", "Video başlatıldı")
        } catch (e: Exception) {
            Log.e("ArCamera", "Video başlatma hatası", e)
            isVideoPlaying = false
            Toast.makeText(this, "Video başlatılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
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
        cameraExecutor.shutdown()
        exoPlayer?.release()
    }
} 