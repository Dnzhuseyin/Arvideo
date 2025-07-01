package com.example.arvideo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ARCoreActivity : ComponentActivity(), GLSurfaceView.Renderer {
    
    private var arSession: Session? = null
    private var surfaceView: GLSurfaceView? = null
    private var exoPlayer: ExoPlayer? = null
    private var isVideoPlaying by mutableStateOf(false)
    private var trackedImageName by mutableStateOf("")
    private var trackingState by mutableStateOf("NONE")
    private var sessionState by mutableStateOf("INITIALIZING")
    private var hasPermission by mutableStateOf(false)
    
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
        Box(modifier = Modifier.fillMaxSize()) {
            
            // ARCore GL Surface View
            if (hasPermission && sessionState == "READY") {
                AndroidView(
                    factory = { context ->
                        GLSurfaceView(context).apply {
                            surfaceView = this
                            preserveEGLContextOnPause = true
                            setEGLContextClientVersion(2)
                            setRenderer(this@ARCoreActivity)
                            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
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
                        .fillMaxSize()
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
                            text = "🚀 ARCore Professional",
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
                            Log.d("ARCore", "Image position: ($imageX, $imageY) ${imageWidth}x${imageHeight}")
                        }
                    ) {
                        Text("Debug")
                    }
                    
                    Button(onClick = { finish() }) {
                        Text("Geri")
                    }
                }
            }
        }
    }

    // GLSurfaceView.Renderer implementations
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        Log.d("ARCore", "GL Surface oluşturuldu")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Log.d("ARCore", "GL Surface boyutu değişti: ${width}x${height}")
    }

    override fun onDrawFrame(gl: GL10?) {
        // Clear screen
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        val session = arSession ?: return
        
        try {
            // Update AR session
            session.setCameraTextureName(0)
            val frame = session.update()
            
            // Process tracked images
            processTrackedImages(frame)
            
        } catch (e: CameraNotAvailableException) {
            Log.e("ARCore", "Kamera kullanılamıyor", e)
        } catch (e: Exception) {
            Log.e("ARCore", "Frame işleme hatası", e)
        }
    }
    
    private fun processTrackedImages(frame: Frame) {
        val updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)
        
        for (augmentedImage in updatedAugmentedImages) {
            when (augmentedImage.trackingState) {
                TrackingState.TRACKING -> {
                    Log.d("ARCore", "Image tracking: ${augmentedImage.name}")
                    
                    runOnUiThread {
                        trackedImageName = augmentedImage.name ?: ""
                        trackingState = "TRACKING"
                        
                        // Image pozisyonunu hesapla
                        val pose = augmentedImage.centerPose
                        val translation = pose.translation
                        
                        // Screen koordinatlarına dönüştür (basitleştirilmiş)
                        imageX = translation[0] * 100 + 200 // Merkeze yakın
                        imageY = translation[1] * 100 + 300
                        imageWidth = augmentedImage.extentX * 200 // Ölçekle
                        imageHeight = augmentedImage.extentZ * 200
                        
                        // Video başlat
                        if (!isVideoPlaying) {
                            startVideo()
                        }
                    }
                }
                TrackingState.PAUSED -> {
                    runOnUiThread {
                        trackingState = "PAUSED"
                    }
                    Log.d("ARCore", "Image tracking paused")
                }
                TrackingState.STOPPED -> {
                    runOnUiThread {
                        trackingState = "STOPPED"
                        if (isVideoPlaying) {
                            stopVideo()
                        }
                    }
                    Log.d("ARCore", "Image tracking stopped")
                }
            }
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

    override fun onResume() {
        super.onResume()
        try {
            arSession?.resume()
            surfaceView?.onResume()
        } catch (e: CameraNotAvailableException) {
            Log.e("ARCore", "Resume sırasında kamera hatası", e)
        }
    }

    override fun onPause() {
        super.onPause()
        arSession?.pause()
        surfaceView?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        arSession?.close()
        exoPlayer?.release()
        Log.d("ARCore", "ARCore Activity kapatıldı")
    }
} 