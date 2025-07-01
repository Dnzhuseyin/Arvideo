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
                    text = "Hedef Resim: ${if (targetBitmap != null) "✓ Yüklendi" else "✗ Yüklenemedi"}",
                    color = if (targetBitmap != null) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
                )
                
                Button(
                    onClick = { 
                        if (isVideoPlaying) {
                            stopVideo()
                        } else {
                            startVideo()
                        }
                    }
                ) {
                    Text(if (isVideoPlaying) "Videoyu Durdur" else "Test Video Oynat")
                }
            }
        }
    }

    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            try {
                Log.d("ArCamera", "Görüntü işleniyor...")
                // Camera image'ı bitmap'e çevir
                val bitmap = imageProxyToBitmap(imageProxy)
                Log.d("ArCamera", "Bitmap oluşturuldu: ${bitmap.width}x${bitmap.height}")
                
                // Hedef fotoğraf ile karşılaştır
                if (targetBitmap != null) {
                    Log.d("ArCamera", "Hedef fotoğraf ile karşılaştırılıyor...")
                    val imageMatcher = ImageMatcher()
                    val similarity = imageMatcher.calculateSimilarity(targetBitmap, bitmap)
                    val histogramSimilarity = imageMatcher.calculateHistogramSimilarity(targetBitmap, bitmap)
                    
                    // İki farklı yöntemin ortalamasını al daha güvenilir sonuç için
                    val averageSimilarity = (similarity + histogramSimilarity) / 2f
                    detectionConfidence = averageSimilarity
                    
                    Log.d("ArCamera", "Benzerlik: $averageSimilarity (${(averageSimilarity * 100).toInt()}%)")
                    
                    // Eğer benzerlik %60'ın üzerindeyse video oynat
                    if (averageSimilarity > 0.3f && !isVideoPlaying) {
                        Log.d("ArCamera", "Video başlatılıyor!")
                        startVideo()
                    } else if (averageSimilarity <= 0.2f && isVideoPlaying) {
                        Log.d("ArCamera", "Video durduruluyor!")
                        stopVideo()
                    }
                } else {
                    Log.w("ArCamera", "Hedef fotoğraf yüklenmemiş, ML Kit kullanılıyor")
                    // ML Kit ile genel image labeling (fallback)
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                    labeler.process(image)
                        .addOnSuccessListener { labels ->
                            var maxConfidence = 0f
                            for (label in labels) {
                                if (label.confidence > maxConfidence) {
                                    maxConfidence = label.confidence
                                }
                            }
                            detectionConfidence = maxConfidence
                            
                            if (maxConfidence > 0.7f && !isVideoPlaying) {
                                startVideo()
                            } else if (maxConfidence <= 0.5f && isVideoPlaying) {
                                stopVideo()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("ArCamera", "Image labeling hatası", e)
                        }
                }
            } catch (e: Exception) {
                Log.e("ArCamera", "Image processing hatası", e)
            }
        } else {
            Log.w("ArCamera", "MediaImage null")
        }
        imageProxy.close()
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer // Y
        val vuBuffer = imageProxy.planes[2].buffer // V
        val uBuffer = imageProxy.planes[1].buffer // U

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()
        val uSize = uBuffer.remaining()

        val nv21 = ByteArray(ySize + vuSize + uSize)

        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)
        uBuffer.get(nv21, ySize + vuSize, uSize)

        val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun getExoPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(this).build().apply {
                // Raw klasöründen video dosyası yükle (varsayılan "ar_video.mp4")
                val videoUri = Uri.parse("android.resource://$packageName/${R.raw.ar_video}")
                val mediaItem = MediaItem.fromUri(videoUri)
                setMediaItem(mediaItem)
                repeatMode = Player.REPEAT_MODE_ALL
                prepare()
            }
        }
        return exoPlayer!!
    }

    private fun startVideo() {
        isVideoPlaying = true
        getExoPlayer().play()
    }

    private fun stopVideo() {
        isVideoPlaying = false
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        exoPlayer?.release()
    }
} 