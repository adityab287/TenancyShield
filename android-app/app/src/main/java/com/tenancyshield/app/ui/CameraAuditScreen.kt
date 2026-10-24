package com.tenancyshield.app.ui

import android.content.Context
import android.os.Build
import android.view.OrientationEventListener
import android.view.Surface
import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@Composable
fun CameraAuditScreen(
    captureMode: String,
    itemId: String = "wall_1",
    baselineUri: String? = null,
    onClose: () -> Unit = {},
    onCapture: (ByteArray) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var imageAnalyzerUseCase by remember { mutableStateOf<ImageAnalysis?>(null) }

    var showGhost by remember { mutableStateOf(true) }
    var ghostOpacity by remember { mutableStateOf(0.35f) }
    var isRecaptureRisk by remember { mutableStateOf(false) }
    var isTooDark by remember { mutableStateOf(false) }
    var isBlurry by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Physical device rotation tracker using OrientationEventListener
    val windowManager = remember { context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager }
    val initialDisplayRotation = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display.rotation
            } catch (e: Exception) {
                Surface.ROTATION_0
            }
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }
    }
    val currentRotationRef = remember { AtomicInteger(initialDisplayRotation) }

    DisposableEffect(lifecycleOwner) {
        val orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                if (currentRotationRef.get() != rotation) {
                    currentRotationRef.set(rotation)
                    imageCapture?.targetRotation = rotation
                    imageAnalyzerUseCase?.targetRotation = rotation
                }
            }
        }
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
        }
        onDispose {
            orientationEventListener.disable()
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F5EF)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Camera Permission Required",
                    color = Color(0xFF343831),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "TenancyShield requires camera access for forensic evidence anchoring.",
                    color = Color(0xFFA78352),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263A2E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Grant Permission", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    val isMoveOut = captureMode.equals("move_out", ignoreCase = true) || captureMode.equals("MOVE_OUT", ignoreCase = true)
    val displayModeTitle = if (isMoveOut) "MOVE-OUT GHOST ALIGNMENT" else "MOVE-IN BASELINE"
    val itemTitle = when (itemId) {
        "wall_1" -> "Wall 1 & Baseboards"
        "flooring" -> "Hardwood Flooring"
        "ceiling" -> "Ceiling & Light Fixtures"
        "meters" -> "Utility Meters & Sockets"
        else -> itemId.replace('_', ' ').uppercase()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Layer 1: Camera Preview with fallback
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val targetRot = currentRotationRef.get()
                    val captureUseCase = ImageCapture.Builder()
                        .setTargetRotation(targetRot)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    // ML Kit Labeler for Screen / Display Detection
                    val options = com.google.mlkit.vision.label.defaults.ImageLabelerOptions.Builder()
                        .setConfidenceThreshold(0.40f)
                        .build()
                    val labeler = com.google.mlkit.vision.label.ImageLabeling.getClient(options)

                    var lastDetectionTime = 0L

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetRotation(targetRot)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                try {
                                    // Luminance & Sharpness Heuristics (Phase D3)
                                    val buffer = imageProxy.planes[0].buffer
                                    buffer.rewind()
                                    val data = ByteArray(buffer.remaining())
                                    buffer.get(data)
                                    buffer.rewind()

                                    val step = 10
                                    var sum = 0L
                                    var count = 0
                                    var i = 0
                                    while (i < data.size) {
                                        val pixel = data[i].toInt() and 0xFF
                                        sum += pixel
                                        count++
                                        i += step
                                    }
                                    val meanLuminance = if (count > 0) sum.toDouble() / count else 0.0
                                    val dark = meanLuminance < 50.0

                                    var sumSquaredDiff = 0.0
                                    if (count > 0) {
                                        i = 0
                                        while (i < data.size) {
                                            val pixel = (data[i].toInt() and 0xFF).toDouble()
                                            val diff = pixel - meanLuminance
                                            sumSquaredDiff += diff * diff
                                            i += step
                                        }
                                    }
                                    val variance = if (count > 0) sumSquaredDiff / count else 0.0
                                    val blurry = variance < 150.0

                                    ContextCompat.getMainExecutor(ctx).execute {
                                        isTooDark = dark
                                        isBlurry = blurry
                                    }

                                    // Phase C6: Recapture Defense
                                    @OptIn(ExperimentalGetImage::class)
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null) {
                                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                        val image = com.google.mlkit.vision.common.InputImage.fromMediaImage(mediaImage, rotationDegrees)

                                        labeler.process(image)
                                            .addOnSuccessListener(ContextCompat.getMainExecutor(ctx)) { labels ->
                                                var foundScreen = false
                                                for (label in labels) {
                                                    val text = label.text.lowercase()
                                                    if (text.contains("monitor") || text.contains("screen") ||
                                                        text.contains("display") || text.contains("laptop") ||
                                                        text.contains("computer") || text.contains("television") ||
                                                        text.contains("tv") || text.contains("lcd") ||
                                                        text.contains("desktop") || text.contains("electronic device")) {
                                                        foundScreen = true
                                                        break
                                                    }
                                                }
                                                val currentTime = System.currentTimeMillis()
                                                if (foundScreen) {
                                                    lastDetectionTime = currentTime
                                                    isRecaptureRisk = true
                                                } else {
                                                    isRecaptureRisk = (currentTime - lastDetectionTime) < 2000L
                                                }
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
                                } catch (e: Exception) {
                                    imageProxy.close()
                                }
                            }
                        }

                    // Safe Camera Selector with Emulator Lens Fallback
                    val cameraSelector = when {
                        cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> {
                            val available = cameraProvider.availableCameraInfos
                            if (available.isNotEmpty()) available.first().cameraSelector else CameraSelector.DEFAULT_BACK_CAMERA
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, captureUseCase, imageAnalyzer)
                        imageCapture = captureUseCase
                        imageAnalyzerUseCase = imageAnalyzer
                    } catch (e: Exception) {
                        android.util.Log.e("CameraAuditScreen", "Camera binding fallback error", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Layer 2: Phase C4 Ghost-Overlay (Move-Out Alignment)
        if (isMoveOut && baselineUri != null && showGhost) {
            AsyncImage(
                model = baselineUri,
                contentDescription = "Baseline Move-In Ghost Overlay",
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(ghostOpacity),
                contentScale = ContentScale.Crop
            )
        }

        // Layer 3: Viewfinder Framing Brackets (Center Reticle)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 120.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bracketLen = 36f
                val strokeW = 4f
                val reticleColor = if (isRecaptureRisk) Color(0xFFDC2626) else Color(0xCCA78352)

                // Top-Left
                drawLine(reticleColor, Offset(0f, 0f), Offset(bracketLen, 0f), strokeW)
                drawLine(reticleColor, Offset(0f, 0f), Offset(0f, bracketLen), strokeW)

                // Top-Right
                drawLine(reticleColor, Offset(size.width, 0f), Offset(size.width - bracketLen, 0f), strokeW)
                drawLine(reticleColor, Offset(size.width, 0f), Offset(size.width, bracketLen), strokeW)

                // Bottom-Left
                drawLine(reticleColor, Offset(0f, size.height), Offset(bracketLen, size.height), strokeW)
                drawLine(reticleColor, Offset(0f, size.height), Offset(0f, size.height - bracketLen), strokeW)

                // Bottom-Right
                drawLine(reticleColor, Offset(size.width, size.height), Offset(size.width - bracketLen, size.height), strokeW)
                drawLine(reticleColor, Offset(size.width, size.height), Offset(size.width, size.height - bracketLen), strokeW)
            }
        }

        // Layer 4: Top Forensic Telemetry Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xEE263A2E), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFA78352), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = displayModeTitle,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = itemTitle,
                        color = Color(0xFFF7F5EF),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0x33FFFFFF), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Capture",
                        tint = Color(0xFFF7F5EF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Real-Time Environment & Security Banner Stack
            AnimatedVisibility(visible = isRecaptureRisk, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFDC2626), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Screen detected — please photograph the actual room directly",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (!isRecaptureRisk) {
                if (isTooDark || isBlurry) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFD97706), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        val warningText = if (isTooDark && isBlurry) {
                            "Low light & image looks blurry — hold steady and turn on a light"
                        } else if (isTooDark) {
                            "Low light — turn on a room light if possible"
                        } else {
                            "Hold steady — image looks slightly blurry"
                        }
                        Text(
                            text = warningText,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xEE263A2E), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Optimal quality • Ready to anchor",
                            color = Color(0xFFF7F5EF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Ghost Overlay Controls in Move-Out Mode
            if (isMoveOut && baselineUri != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xEE263A2E), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable { showGhost = !showGhost }
                    ) {
                        Text(
                            text = if (showGhost) "👁 Ghost Overlay: ON" else "👁 Ghost Overlay: OFF",
                            color = Color(0xFFA78352),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (showGhost) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.width(180.dp)
                        ) {
                            Text("15%", color = Color(0xFFEAE7DF), fontSize = 10.sp)
                            Slider(
                                value = ghostOpacity,
                                onValueChange = { ghostOpacity = it },
                                valueRange = 0.15f..0.65f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFA78352),
                                    activeTrackColor = Color(0xFFA78352),
                                    inactiveTrackColor = Color(0x66EAE7DF)
                                )
                            )
                            Text("65%", color = Color(0xFFEAE7DF), fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // Layer 5: Bottom Shutter & Anchoring Control Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xEE263A2E))
                .padding(vertical = 24.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            val shutterEnabled = !isRecaptureRisk && !isSaving
            val ringColor = if (isRecaptureRisk) Color(0xFFDC2626) else if (shutterEnabled) Color(0xFFA78352) else Color(0xFF88857E)

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .border(4.dp, ringColor, CircleShape)
                    .clickable(enabled = shutterEnabled) {
                        if (isRecaptureRisk) return@clickable
                        isSaving = true
                        val currentRot = currentRotationRef.get()
                        imageCapture?.targetRotation = currentRot
                        val contextExec = ContextCompat.getMainExecutor(context)
                        val photoFile = File(
                            context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES),
                            "raw_sensor_${UUID.randomUUID()}.jpg"
                        )
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                        imageCapture?.takePicture(
                            outputOptions,
                            contextExec,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    val bytes = photoFile.readBytes()
                                    onCapture(bytes)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    android.util.Log.e("CameraAuditScreen", "Capture error", exception)
                                    isSaving = false
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(if (shutterEnabled) Color(0xFFF7F5EF) else Color(0xFF5C6058))
                )
            }
        }

        // Layer 6: Anchoring Progress Overlay
        if (isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F5EF)),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFF263A2E), modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Signing with Hardware Keystore & Anchoring to Ledger...",
                            color = Color(0xFF343831),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "BSA 2023 Sec 63 Provenance Manifest Injecting",
                            color = Color(0xFFA78352),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
