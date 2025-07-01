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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.arvideo.ui.theme.ArvideoTheme
import kotlinx.coroutines.*
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ArCameraActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var targetBitmap: Bitmap? = null
    private var isVideoPlaying by mutableStateOf(false)
    private var detectionConfidence by mutableStateOf(0f)
    private var cameraEnabled by mutableStateOf(false)
    private lateinit var cameraExecutor: ExecutorService
    private var isProcessingImage = false

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
        Log.d("ArCamera", "ArCameraActivity başlatılıyor...")
        
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
            // Kamera önizlemesi (sadece izin varsa)
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
                            
                            // Image analysis ekliyoruz - ama çok dikkatli
                            val imageAnalyzer = ImageAnalysis.Builder()
                                .setTargetResolution(android.util.Size(640, 480))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                                        processImageSafely(imageProxy)
                                    }
                                }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner, cameraSelector, preview, imageAnalyzer
                            )
                            
                            Log.d("ArCamera", "Kamera ve image analysis başarıyla başlatıldı")
                            
                        } catch (exc: Exception) {
                            Log.e("ArCamera", "Kamera başlatma hatası", exc)
                        }

                    }, ContextCompat.getMainExecutor(context))
                }
            } else {
                // Kamera yoksa boş alan
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Kamera İzni Bekleniyor...",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
            
            // Video Overlay - resim tanındığında gösterilecek
            if (isVideoPlaying) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                600
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
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
                    text = "Kamera: ${if (cameraEnabled) "✓ Aktif" else "✗ İzin Yok"}",
                    color = if (cameraEnabled) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
                )
                
                Text(
                    text = "Hedef Resim: ${if (targetBitmap != null) "✓ Yüklendi" else "✗ Yüklenemedi"}",
                    color = if (targetBitmap != null) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
                )
                
                Text(
                    text = "Güven: ${(detectionConfidence * 100).toInt()}%",
                    color = androidx.compose.ui.graphics.Color.White
                )
                
                Text(
                    text = "Video: ${if (isVideoPlaying) "▶️ Oynatılıyor" else "⏸️ Durduruldu"}",
                    color = if (isVideoPlaying) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.White
                )
                
                Text(
                    text = "Eşik: %15+ başlat, %10- durdur",
                    color = androidx.compose.ui.graphics.Color.Cyan
                )
                
                Text(
                    text = "Test: 5sn video başlar, 8sn durur",
                    color = androidx.compose.ui.graphics.Color.Magenta
                )
                
                Text(
                    text = "Fırat Üniversitesi plaketini kameraya gösterin",
                    color = androidx.compose.ui.graphics.Color.Yellow
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            if (isVideoPlaying) stopVideo() else startVideo()
                        }
                    ) {
                        Text(if (isVideoPlaying) "Durdur" else "Manuel")
                    }
                    
                    Button(
                        onClick = {
                            Log.d("ArCamera", "=== DEBUG BİLGİLERİ ===")
                            Log.d("ArCamera", "Target bitmap: ${targetBitmap != null}")
                            Log.d("ArCamera", "Camera enabled: $cameraEnabled")
                            Log.d("ArCamera", "Video playing: $isVideoPlaying")
                            Log.d("ArCamera", "Detection confidence: $detectionConfidence")
                            Log.d("ArCamera", "Is processing: $isProcessingImage")
                            if (targetBitmap != null) {
                                Log.d("ArCamera", "Target size: ${targetBitmap!!.width}x${targetBitmap!!.height}")
                            }
                            Toast.makeText(this@ArCameraActivity, "Debug logları gönderildi - Logcat'e bakın", Toast.LENGTH_LONG).show()
                        }
                    ) {
                        Text("Debug")
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
    }

    // Güvenli image processing - channel broken önlemek için
    private fun processImageSafely(imageProxy: ImageProxy) {
        // Eğer hala işleme devam ediyorsa, bu frame'i atla
        if (isProcessingImage) {
            imageProxy.close()
            return
        }
        
        isProcessingImage = true
        
        try {
            // BASİT TEST: Her 5 saniyede otomatik video başlat (eğer resim tanıma çalışmıyorsa)
            val currentTime = System.currentTimeMillis()
            if (currentTime % 5000 < 100 && !isVideoPlaying) {
                Log.d("ArCamera", "5 saniye test - Otomatik video başlatılıyor!")
                CoroutineScope(Dispatchers.Main).launch {
                    startVideo()
                }
            } else if (currentTime % 8000 < 100 && isVideoPlaying) {
                Log.d("ArCamera", "8 saniye test - Otomatik video durduruluyor!")
                CoroutineScope(Dispatchers.Main).launch {
                    stopVideo()
                }
            }
            
            if (targetBitmap != null) {
                val bitmap = imageProxyToBitmap(imageProxy)
                if (bitmap != null) {
                    Log.d("ArCamera", "Bitmap oluşturuldu: ${bitmap.width}x${bitmap.height}")
                    
                    // Basit renk karşılaştırması
                    val similarity = calculateSimpleSimilarity(targetBitmap!!, bitmap)
                    
                    // Her 30 frame'de bir detaylı log
                    if (System.currentTimeMillis() % 1000 < 50) {
                        Log.d("ArCamera", "Similarity check: $similarity (${(similarity * 100).toInt()}%)")
                    }
                    
                    // UI thread'de güncelle
                    CoroutineScope(Dispatchers.Main).launch {
                        detectionConfidence = similarity
                        
                        // ÇOK DÜŞÜK EŞİK - TEST İÇİN %15
                        if (similarity > 0.15f && !isVideoPlaying) {
                            Log.d("ArCamera", "DÜŞÜK EŞİK: Plakat tanındı! Video başlatılıyor. Benzerlik: $similarity")
                            startVideo()
                        } else if (similarity <= 0.10f && isVideoPlaying) {
                            Log.d("ArCamera", "DÜŞÜK EŞİK: Plakat kayboldu. Video durduruluyor. Benzerlik: $similarity")
                            stopVideo()
                        }
                    }
                } else {
                    Log.w("ArCamera", "Bitmap oluşturulamadı!")
                }
            } else {
                Log.w("ArCamera", "Target bitmap NULL!")
            }
        } catch (e: Exception) {
            Log.e("ArCamera", "Image processing hatası", e)
        } finally {
            isProcessingImage = false
            imageProxy.close()
        }
    }
    
    // Basit ve hızlı bitmap dönüştürme
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            // Önce JPEG decode etmeyi dene
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                Log.d("ArCamera", "JPEG decode başarılı: ${bitmap.width}x${bitmap.height}")
                return bitmap
            }
            
            // JPEG çalışmazsa YUV formatını dene
            Log.d("ArCamera", "JPEG başarısız, YUV deneniyor...")
            
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
            
            if (resultBitmap != null) {
                Log.d("ArCamera", "YUV decode başarılı: ${resultBitmap.width}x${resultBitmap.height}")
            } else {
                Log.e("ArCamera", "YUV decode de başarısız!")
            }
            
            resultBitmap
            
        } catch (e: Exception) {
            Log.e("ArCamera", "Bitmap dönüştürme hatası", e)
            null
        }
    }
    
    // Basit renk benzerliği - performanslı
    private fun calculateSimpleSimilarity(target: Bitmap, source: Bitmap): Float {
        return try {
            // 20x20 küçük sample
            val targetSmall = Bitmap.createScaledBitmap(target, 20, 20, true)
            val sourceSmall = Bitmap.createScaledBitmap(source, 20, 20, true)
            
            var totalSimilarity = 0f
            val totalPixels = 400 // 20x20
            
            // İlk pixel'leri kontrol et - debug için
            val targetFirstPixel = targetSmall.getPixel(0, 0)
            val sourceFirstPixel = sourceSmall.getPixel(0, 0)
            
            for (x in 0 until 20) {
                for (y in 0 until 20) {
                    val targetPixel = targetSmall.getPixel(x, y)
                    val sourcePixel = sourceSmall.getPixel(x, y)
                    
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
            
            // Her 50 hesaplamada bir detaylı log
            if (System.currentTimeMillis() % 5000 < 100) {
                Log.d("ArCamera", "=== SİMİLARİTY DETAY ===")
                Log.d("ArCamera", "Target first pixel: R=${android.graphics.Color.red(targetFirstPixel)} G=${android.graphics.Color.green(targetFirstPixel)} B=${android.graphics.Color.blue(targetFirstPixel)}")
                Log.d("ArCamera", "Source first pixel: R=${android.graphics.Color.red(sourceFirstPixel)} G=${android.graphics.Color.green(sourceFirstPixel)} B=${android.graphics.Color.blue(sourceFirstPixel)}")
                Log.d("ArCamera", "Final similarity: $result (${(result * 100).toInt()}%)")
            }
            
            result
        } catch (e: Exception) {
            Log.e("ArCamera", "Similarity hesaplama hatası", e)
            0f
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
        cameraExecutor.shutdown()
        exoPlayer?.release()
    }
} 