package com.example.caloriestracker.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.example.caloriestracker.ai.CalorieEstimate
import com.example.caloriestracker.ai.CalorieEstimator
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CameraScreen(
    apiKey: String,
    onClose: () -> Unit,
    onCaloriesLogged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(Surface.ROTATION_0)
            .build()
    }
    var hasCameraPermission by remember { mutableStateOf(hasCameraPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var capturedImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var currentEstimate by remember { mutableStateOf<CalorieEstimate?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val executor = remember { ContextCompat.getMainExecutor(context) }

    fun capture() {
        val photoFile = File.createTempFile("meal-", ".jpg", context.cacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        isProcessing = true
        currentEstimate = null

        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    errorMessage = exception.message ?: "Unable to capture meal"
                    isProcessing = false
                    photoFile.delete()
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val bitmap = photoFile.toBitmap()
                    photoFile.delete()
                    if (bitmap == null) {
                        errorMessage = "Could not read the photo"
                        isProcessing = false
                        return
                    }
                    capturedImage = bitmap.asImageBitmap()
                    scope.launch {
                        runCatching { CalorieEstimator.estimate(bitmap, apiKey) }
                            .onSuccess { currentEstimate = it }
                            .onFailure { throwable ->
                                errorMessage = throwable.localizedMessage ?: "AI estimate failed"
                            }
                        isProcessing = false
                    }
                }
            }
        )
    }

    fun reset() {
        capturedImage = null
        currentEstimate = null
        errorMessage = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (capturedImage == null) {
            if (hasCameraPermission) {
                CameraPreview(imageCapture = imageCapture, modifier = Modifier.fillMaxSize())
            }
        } else {
            Image(
                bitmap = capturedImage!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.4f)) {
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ResultCard(
                estimate = currentEstimate,
                isProcessing = isProcessing,
                apiKeyMissing = apiKey.isBlank(),
                onLogMeal = {
                    currentEstimate?.let {
                        onCaloriesLogged(it.calories)
                        reset()
                        onClose()
                    }
                }
            )

            CaptureButton(
                enabled = hasCameraPermission && !isProcessing,
                onClick = {
                    if (capturedImage == null) capture() else reset()
                },
                label = if (capturedImage == null) "Snap" else "Retake"
            )
        }

        AnimatedVisibility(
            visible = errorMessage != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            errorMessage?.let { message ->
                Surface(
                    color = Color(0xFFFF5C5C),
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = message, color = Color.White, modifier = Modifier.weight(1f))
                        Text(
                            text = "Dismiss",
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clickable { errorMessage = null }
                        )
                    }
                }
            }
        }

        if (!hasCameraPermission) {
            PermissionInfo(onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        }
    }
}

@Composable
private fun ResultCard(
    estimate: CalorieEstimate?,
    isProcessing: Boolean,
    apiKeyMissing: Boolean,
    onLogMeal: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Calorie estimate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when {
                isProcessing -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        Text("Analyzing meal photo...")
                    }
                }

                estimate != null -> {
                    Text(text = "${estimate.calories} kcal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(text = estimate.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "Confidence ${(estimate.confidence * 100).toInt()}%",
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                        )
                    }
                    androidx.compose.material3.Button(onClick = onLogMeal, modifier = Modifier.fillMaxWidth()) {
                        Text("Add to today")
                    }
                }

                apiKeyMissing -> {
                    Text(
                        text = "Add your Ollama API key in Settings to enable AI estimates.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    Text(text = "Snap a photo to analyze your meal.")
                }
            }
        }
    }
}

@Composable
private fun PermissionInfo(onGrant: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Camera permission needed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = "We use the camera to capture your meals and generate calorie estimates.", textAlign = TextAlign.Center)
                androidx.compose.material3.Button(onClick = onGrant) {
                    Text("Grant access")
                }
            }
        }
    }
}

@Composable
private fun CaptureButton(enabled: Boolean, onClick: () -> Unit, label: String) {
    Surface(
        modifier = Modifier.size(96.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.85f)
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxSize()) {
            Icon(imageVector = Icons.Rounded.CameraAlt, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
        }
    }
}

@Composable
private fun CameraPreview(imageCapture: ImageCapture, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(lifecycleOwner, imageCapture) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (_: Exception) {
            }
        }
        cameraProviderFuture.addListener(listener, executor)
        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }
}

private fun hasCameraPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED

private fun File.toBitmap(): Bitmap? {
    val bitmap = BitmapFactory.decodeFile(absolutePath) ?: return null
    val exif = runCatching { ExifInterface(absolutePath) }.getOrNull()
    val rotation = exif?.rotationDegrees ?: 0
    return if (rotation == 0) bitmap else bitmap.rotate(rotation.toFloat())
}

private fun Bitmap.rotate(angle: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(angle) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private val ExifInterface.rotationDegrees: Int
    get() = when (getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
