package com.retinasight.ai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.retinasight.ai.R
import com.retinasight.ai.ui.components.BigActionButton
import com.retinasight.ai.ui.components.SecondaryActionButton

/**
 * Captures the fundus photo.
 *
 * The camera is preferred but never required: if permission is refused, the
 * user can still import a photo taken with a fundus lens or another device.
 * A rural health worker must not be blocked by a permission dialog.
 */
@Composable
fun CaptureScreen(
    onImageReady: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var decodeFailed by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    // The system photo picker. Needs NO storage permission on Android 13+,
    // and on older versions the support library falls back automatically.
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bitmap = loadBitmapFromUri(context, uri)
        if (bitmap != null) {
            onImageReady(bitmap)
        } else {
            decodeFailed = true
        }
    }

    val imageCapture = remember { ImageCapture.Builder().build() }

    // Ask once on first entry rather than making the user find a button.
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Scrollable because in landscape the preview plus the buttons
                // are taller than the screen; without this the shutter and the
                // upload button are simply unreachable.
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.capture_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // Height is capped rather than proportional: the preview must never
            // grow so tall that the capture buttons fall below the fold.
            val previewHeight = if (isLandscape) 260.dp else 380.dp
            val ringSize = if (isLandscape) 200.dp else 300.dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    CameraPreview(
                        imageCapture = imageCapture,
                        onBind = { previewView ->
                            bindCamera(context, lifecycleOwner, previewView, imageCapture)
                        }
                    )
                    // Alignment guide: the user centres the eye inside this ring.
                    Box(
                        modifier = Modifier
                            .size(ringSize)
                            .border(4.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.capture_permission_rationale),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        SecondaryActionButton(
                            text = stringResource(R.string.capture_grant_permission),
                            icon = Icons.Filled.PhotoLibrary,
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.capture_instruction),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // Optics, not software: the lens cannot reach the retina through an
            // undilated pupil on its own. Saying so on the capture screen keeps
            // the app honest about what it is - the grading layer, not a camera.
            Text(
                text = stringResource(R.string.capture_adapter_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            if (hasCameraPermission) {
                BigActionButton(
                    text = stringResource(R.string.capture_shutter),
                    icon = Icons.Filled.PhotoCamera,
                    onClick = { takePhoto(context, imageCapture, onImageReady) }
                )
                Spacer(Modifier.height(12.dp))
            }

            BigActionButton(
                text = stringResource(R.string.capture_gallery),
                icon = Icons.Filled.PhotoLibrary,
                onClick = {
                    decodeFailed = false
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )

            if (decodeFailed) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.capture_decode_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(
    imageCapture: ImageCapture,
    onBind: (PreviewView) -> Unit
) {
    val previewView = remember { mutableStateOf<PreviewView?>(null) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).also {
                previewView.value = it
                onBind(it)
            }
        }
    )

    DisposableEffect(imageCapture) {
        onDispose { /* provider unbinding is handled by the lifecycle owner */ }
    }
}

private fun bindCamera(
    context: android.content.Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    imageCapture: ImageCapture
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        runCatching {
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun takePhoto(
    context: android.content.Context,
    imageCapture: ImageCapture,
    onImageReady: (Bitmap) -> Unit
) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = image.toBitmapCompat()
                image.close()
                bitmap?.let(onImageReady)
            }

            override fun onError(exception: ImageCaptureException) {
                // Capture failures must not crash the app; the user can retry
                // or fall back to importing a photo.
                Log.e(TAG, "Photo capture failed", exception)
            }
        }
    )
}

private const val TAG = "CaptureScreen"

/** JPEG ImageProxy -> Bitmap, applying the reported rotation. */
private fun ImageProxy.toBitmapCompat(): Bitmap? {
    val buffer = planes.firstOrNull()?.buffer ?: return null
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return decoded

    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}

/**
 * Decodes a picked image as a SOFTWARE bitmap.
 *
 * This matters: ImageDecoder returns a hardware bitmap by default, and hardware
 * bitmaps have no readable pixels - every getPixel call and every attempt to
 * feed the model would throw. Forcing SOFTWARE keeps the pixels accessible.
 */
private fun loadBitmapFromUri(context: android.content.Context, uri: Uri): Bitmap? =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }.getOrElse {
        Log.e(TAG, "Could not decode picked image", it)
        null
    }
