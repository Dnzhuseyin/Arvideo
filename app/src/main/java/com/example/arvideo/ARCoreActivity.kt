package com.example.arvideo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLSurfaceView
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
import androidx.compose.material3.*
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
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ARCoreActivity : ComponentActivity() {
    
    private var arSession: Session? = null
    private var exoPlayer: ExoPlayer? = null
    private var isVideoPlaying by mutableStateOf(false)
    private var trackedImageName by mutableStateOf("")
    private var trackingState by mutableStateOf("NONE")
    private var sessionState by mutableStateOf("INITIALIZING")
    private var hasPermission by mutableStateOf(false)
    private lateinit var cameraExecutor: ExecutorService
    
    // Tracked image pozisyon bilgileri
    private var imageX by mutableStateOf(0f)
    private var imageY by mutableStateOf(0f)
    private var imageWidth by mutableStateOf(0f)
    private var imageHeight by mutableStateOf(0f)
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            hasPermission = true
            setupARSession()
        } else {
            Toast.makeText(this, "ARCore için kamera izni gerekli", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("ARCore", "ARCore Activity başlatılıyor...")
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Video player'ı hazırla
        initializePlayer()
        
        // Kamera izni kontrol et
        checkCameraPermission()

        setContent {
            ArvideoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ARScreen()
                }
            }
        }
    }
    
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true
            setupARSession()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    private fun setupARSession() {
        try {
            sessionState = "CREATING_SESSION"
            
            // ARCore availability kontrol et
            when (ArCoreApk.getInstance().checkAvailability(this)) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                    Log.d("ARCore", "ARCore destekleniyor ve yüklü")
                }
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD, 
                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                    Log.d("ARCore", "ARCore güncelleme gerekiyor")
                    ArCoreApk.getInstance().requestInstall(this, true)
                    return
                }
                else -> {
                    Log.e("ARCore", "ARCore desteklenmiyor")
                    Toast.makeText(this, "ARCore bu cihazda desteklenmiyor", Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
            }
            
            // AR Session oluştur
            arSession = Session(this)
            Log.d("ARCore", "AR Session oluşturuldu")
            
            // Augmented Images database'i ayarla
            setupAugmentedImagesDatabase()
            
            sessionState = "READY"
            
        } catch (e: UnavailableArcoreNotInstalledException) {
            Log.e("ARCore", "ARCore yüklü değil", e)
            sessionState = "ERROR: ARCore yüklü değil"
        } catch (e: UnavailableApkTooOldException) {
            Log.e("ARCore", "ARCore çok eski", e)
            sessionState = "ERROR: ARCore güncelleme gerekiyor"
        } catch (e: UnavailableSdkTooOldException) {
            Log.e("ARCore", "SDK çok eski", e)
            sessionState = "ERROR: SDK güncelleme gerekiyor"
        } catch (e: Exception) {
            Log.e("ARCore", "AR Session oluşturma hatası", e)
            sessionState = "ERROR: ${e.message}"
        }
    }
    
    private fun setupAugmentedImagesDatabase() {
        try {
            val config = arSession?.config ?: return
            
            // Augmented Images Database oluştur
            val augmentedImageDatabase = AugmentedImageDatabase(arSession)
            
            // Target image'ı yükle
            val inputStream = assets.open("target_image.jpg")
            val targetBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (targetBitmap != null) {
                // Image'ı database'e ekle - "firat_plaketi" adıyla
                val imageIndex = augmentedImageDatabase.addImage("firat_plaketi", targetBitmap, 0.1f) // 10cm fiziksel genişlik
                Log.d("ARCore", "Target image eklendi, index: $imageIndex")
                
                // Config'e database'i ata
                config.augmentedImageDatabase = augmentedImageDatabase
                arSession?.configure(config)
                
                Log.d("ARCore", "Augmented Images Database yapılandırıldı")
            } else {
                Log.e("ARCore", "Target image yüklenemedi")
                sessionState = "ERROR: Target image yüklenemedi"
            }
            
        } catch (e: IOException) {
            Log.e("ARCore", "Target image okuma hatası", e)
            sessionState = "ERROR: Target image okuma hatası"
        } catch (e: Exception) {
            Log.e("ARCore", "Database ayarlama hatası", e)
            sessionState = "ERROR: Database ayarlama hatası"
        }
    }
    
    private fun initializePlayer() {
        try {
            Log.d("ARCore", "ExoPlayer başlatılıyor...")
            exoPlayer = ExoPlayer.Builder(this).build()
            
            val resourceId = resources.getIdentifier("ar_video", "raw", packageName)
            if (resourceId != 0) {
                val videoUri = Uri.parse("android.resource://$packageName/$resourceId")
                val mediaItem = MediaItem.fromUri(videoUri)
                exoPlayer!!.setMediaItem(mediaItem)
                exoPlayer!!.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                exoPlayer!!.prepare()
                Log.d("ARCore", "ExoPlayer hazır")
            }
        } catch (e: Exception) {
            Log.e("ARCore", "ExoPlayer başlatma hatası", e)
        }
    }

    @Composable
    fun ARScreen() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        
        Box(modifier = Modifier.fillMaxSize()) {
            
            // Kamera Preview (CameraX) - ARCore yerine basit çözüm
            if (hasPermission) {
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
                            
                            // Image analysis - ARCore tracking için
                            val imageAnalyzer = ImageAnalysis.Builder()
                                .setTargetResolution(android.util.Size(640, 480))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                                        processARFrame(imageProxy)
                                    }
                                }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner, cameraSelector, preview, imageAnalyzer
                            )
                            
                            Log.d("ARCore", "Kamera başlatıldı!")
                            
                        } catch (exc: Exception) {
                            Log.e("ARCore", "Kamera başlatma hatası", exc)
                        }

                    }, ContextCompat.getMainExecutor(context))
                }
            }
            
            // Video Overlay - Tracked image bulunduğunda
            if (isVideoPlaying && trackingState == "TRACKING") {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier
                        .size(200.dp, 150.dp)
                        .offset(
                            x = imageX.dp,
                            y = imageY.dp
                        )
                )
            }
            
            // UI Info Panel
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "🚀 ARCore + CameraX",
                            color = androidx.compose.ui.graphics.Color.Green
                        )
                        
                        Text(
                            text = "Session: $sessionState",
                            color = androidx.compose.ui.graphics.Color.White
                        )
                        
                        Text(
                            text = "Tracking: $trackingState",
                            color = if (trackingState == "TRACKING") androidx.compose.ui.graphics.Color.Green 
                                   else androidx.compose.ui.graphics.Color.Yellow
                        )
                        
                        if (trackedImageName.isNotEmpty()) {
                            Text(
                                text = "Image: $trackedImageName",
                                color = androidx.compose.ui.graphics.Color.Cyan
                            )
                        }
                        
                        Text(
                            text = "Video: ${if (isVideoPlaying) "▶️ Playing" else "⏸️ Stopped"}",
                            color = androidx.compose.ui.graphics.Color.White
                        )
                        
                        Text(
                            text = "Fırat Üniversitesi plaketini gösterin",
                            color = androidx.compose.ui.graphics.Color.Yellow
                        )
                        
                        Text(
                            text = "Kamera: ${if (hasPermission) "✅ Açık" else "❌ Kapalı"}",
                            color = if (hasPermission) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            Log.d("ARCore", "=== ARCore DEBUG ===")
                            Log.d("ARCore", "Session state: $sessionState")
                            Log.d("ARCore", "Tracking state: $trackingState")
                            Log.d("ARCore", "Video playing: $isVideoPlaying")
                            Log.d("ARCore", "Has permission: $hasPermission")
                            Log.d("ARCore", "Image position: ($imageX, $imageY) ${imageWidth}x${imageHeight}")
                        }
                    ) {
                        Text("Debug")
                    }
                    
                    Button(
                        onClick = {
                            // Test video başlat
                            if (!isVideoPlaying) {
                                trackingState = "TRACKING"
                                trackedImageName = "test_image"
                                imageX = 50f
                                imageY = 100f
                                startVideo()
                            } else {
                                stopVideo()
                                trackingState = "NONE"
                            }
                        }
                    ) {
                        Text(if (isVideoPlaying) "Stop" else "Test")
                    }
                    
                    Button(onClick = { finish() }) {
                        Text("Geri")
                    }
                }
            }
        }
    }

    // ARCore image tracking (background'da çalışır)
    private fun processARFrame(imageProxy: ImageProxy) {
        try {
            val session = arSession
            if (session != null && sessionState == "READY") {
                // Burada ARCore frame tracking yapılabilir
                // Şimdilik basit test
                Log.d("ARCore", "Frame işleniyor... ${imageProxy.width}x${imageProxy.height}")
                
                // Simulated tracking - gerçek ARCore implementation buraya gelecek
                if (System.currentTimeMillis() % 10000 < 5000) { // 10 saniyede 5 saniye tracking
                    if (trackingState != "TRACKING") {
                        runOnUiThread {
                            trackingState = "TRACKING"
                            trackedImageName = "firat_plaketi"
                            imageX = 100f
                            imageY = 200f
                            if (!isVideoPlaying) {
                                startVideo()
                            }
                        }
                    }
                } else {
                    if (trackingState == "TRACKING") {
                        runOnUiThread {
                            trackingState = "NONE"
                            trackedImageName = ""
                            if (isVideoPlaying) {
                                stopVideo()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ARCore", "Frame işleme hatası", e)
        } finally {
            imageProxy.close()
        }
    }
    
    private fun startVideo() {
        try {
            Log.d("ARCore", "ARCore video başlatılıyor...")
            isVideoPlaying = true
            exoPlayer?.play()
        } catch (e: Exception) {
            Log.e("ARCore", "Video başlatma hatası", e)
        }
    }
    
    private fun stopVideo() {
        try {
            Log.d("ARCore", "ARCore video durduruluyor...")
            isVideoPlaying = false
            exoPlayer?.pause()
        } catch (e: Exception) {
            Log.e("ARCore", "Video durdurma hatası", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        arSession?.close()
        exoPlayer?.release()
        Log.d("ARCore", "ARCore Activity kapatıldı")
    }
} 