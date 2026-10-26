package com.ts.cbdcwalletapp.ui

import android.Manifest
import android.annotation.SuppressLint
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.ts.cbdcwalletapp.data.*
import com.ts.cbdcwalletapp.utils.QRUtils
import java.util.UUID
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OfflineTransferScreen(
    navController: NavController,
    viewModel: MainViewModel,
    currentUser: User?
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    if (cameraPermissionState.status.isGranted) {
        OfflineTransferContent(navController, viewModel, currentUser)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Camera permission is needed for offline transfer", textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
fun OfflineTransferContent(
    navController: NavController,
    viewModel: MainViewModel,
    currentUser: User?
) {
    val context = LocalContext.current
    val offlineState by viewModel.offlineTransferState.collectAsState()
    val localTokens by viewModel.localOfflineTokens.collectAsState()
    val receiverId = currentUser?.phoneNumber ?: currentUser?.userId ?: "unknown"

    var txId by remember { mutableStateOf("") }
    var peerId by remember { mutableStateOf("") }
    var currentStep by remember { mutableStateOf(TransferStep.REQUEST) }
    var qrContent by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("Starting offline transfer") }
    var amount by remember { mutableStateOf(offlineState.transferAmount) }
    var isProcessingScan by remember { mutableStateOf(false) }
    var lastScannedDebug by remember { mutableStateOf("") }
    var requestLogged by remember { mutableStateOf(false) }

    val isSender = offlineState.isSender
    val isReceiver = !offlineState.isSender

    fun showError(message: String) {
        statusMessage = message
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(isSender, currentStep, localTokens) {
        if (isSender) {
            if (currentStep == TransferStep.REQUEST) {
                statusMessage = "Scan receiver request QR"
                qrContent = null
            }
        } else {
            if (currentStep != TransferStep.REQUEST) return@LaunchedEffect
            if (txId.isBlank()) txId = UUID.randomUUID().toString()
            val availableDenominations = localTokens
                .groupBy { it.denomination }
                .map { (denomination, tokens) ->
                    OfflineDenominationQuantity(denomination, tokens.size)
                }
            val request = OfflineRequest(
                tx_id = txId,
                to = receiverId,
                availableDenominations = availableDenominations
            )
            if (!requestLogged) {
                viewModel.logOfflineRequestShown(txId, receiverId)
                requestLogged = true
            }
            qrContent = TransferProtocol.toJson(request)
            statusMessage = "Show this request QR to sender. Change available: INR ${localTokens.sumOf { it.denomination }}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Offline Transfer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (currentStep == TransferStep.SUCCESS) {
            SuccessPanel(
                isSender = isSender,
                amount = amount,
                qrContent = qrContent,
                onDone = { navController.popBackStack() }
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            QrPanel(
                isSender = isSender,
                amount = amount,
                qrContent = qrContent
            )

            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CameraPreview(
                        onCodeScanned = { scannedData ->
                            lastScannedDebug = scannedData
                            if (isProcessingScan) return@CameraPreview

                            val message = TransferProtocol.parseMessage(scannedData)
                                ?: return@CameraPreview

                            val validScan = if (isSender) {
                                message.type == TransferStep.REQUEST ||
                                    message.type == TransferStep.ACK
                            } else {
                                message.type == TransferStep.HANDOFF ||
                                    message.type == TransferStep.SUCCESS
                            }
                            if (!validScan) return@CameraPreview

                            when {
                                isSender &&
                                    currentStep == TransferStep.REQUEST &&
                                    message.type == TransferStep.REQUEST -> {
                                    val request = TransferProtocol.parseRequest(scannedData)
                                        ?: return@CameraPreview
                                    isProcessingScan = true
                                    txId = request.tx_id
                                    peerId = request.to

                                    viewModel.prepareOfflineHandoff(
                                        txId = request.tx_id,
                                        receiverId = request.to,
                                        amount = amount,
                                        receiverAvailableDenominations = request.availableDenominations
                                    ) { result ->
                                        result
                                            .onSuccess { handoff ->
                                                qrContent = TransferProtocol.toJson(handoff)
                                                currentStep = TransferStep.HANDOFF
                                                statusMessage = if (handoff.isExchange) {
                                                    "Sent INR ${handoff.handoffAmount}; receiver will return INR ${handoff.changeAmount}. Show this QR."
                                                } else {
                                                    "Tokens transferred out. Show this QR to receiver."
                                                }
                                            }
                                            .onFailure { error ->
                                                showError(error.message ?: "Cannot prepare offline transfer")
                                            }
                                        isProcessingScan = false
                                    }
                                }

                                isReceiver &&
                                    currentStep == TransferStep.REQUEST &&
                                    message.type == TransferStep.HANDOFF -> {
                                    val handoff = TransferProtocol.parseHandoff(scannedData)
                                        ?: return@CameraPreview
                                    isProcessingScan = true
                                    amount = handoff.amount
                                    peerId = handoff.from

                                    viewModel.acceptOfflineHandoff(handoff) { result ->
                                        result
                                            .onSuccess { ack ->
                                                qrContent = TransferProtocol.toJson(ack)
                                                currentStep = TransferStep.ACK
                                                statusMessage = if (ack.changeAmount > 0) {
                                                    "Tokens received. Returning INR ${ack.changeAmount} change. Show ACK QR."
                                                } else {
                                                    "Tokens received. Show ACK QR to sender."
                                                }
                                            }
                                            .onFailure { error ->
                                                showError(error.message ?: "Cannot accept offline tokens")
                                            }
                                        isProcessingScan = false
                                    }
                                }

                                isSender &&
                                    currentStep == TransferStep.HANDOFF &&
                                    message.type == TransferStep.ACK -> {
                                    val ack = TransferProtocol.parseAck(scannedData)
                                        ?: return@CameraPreview
                                    if (ack.tx_id != txId) return@CameraPreview

                                    isProcessingScan = true
                                    viewModel.completeOfflineSenderAck(ack, peerId, amount)
                                    val success = OfflineSuccess(tx_id = txId)
                                    qrContent = TransferProtocol.toJson(success)
                                    currentStep = TransferStep.SUCCESS
                                    statusMessage = "Transfer complete. Show final confirmation to receiver."
                                    isProcessingScan = false
                                }

                                isReceiver &&
                                    currentStep == TransferStep.ACK &&
                                    message.type == TransferStep.SUCCESS -> {
                                    val success = TransferProtocol.parseSuccess(scannedData)
                                        ?: return@CameraPreview
                                    if (success.tx_id != txId) return@CameraPreview

                                    currentStep = TransferStep.SUCCESS
                                    qrContent = null
                                    statusMessage = "Transfer complete"
                                }
                            }
                        }
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(200.dp)
                            .border(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(12.dp)
                            )
                    )

//                    ScanHint(
//                        modifier = Modifier
//                            .align(Alignment.TopStart)
//                            .padding(16.dp),
//                        text = scanHint(isSender, currentStep),
//                        lastScan = lastScannedDebug
//                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { navController.popBackStack() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun QrPanel(
    isSender: Boolean,
    amount: Int,
    qrContent: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isSender) "Sender" else "Receiver",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            if (isSender) {
                Text("Amount: INR $amount", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (qrContent == null) {
                Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val bitmap = QRUtils.generateQRCode(qrContent)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(260.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SuccessPanel(
    isSender: Boolean,
    amount: Int,
    qrContent: String?,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Transaction Complete",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isSender) "Sent INR $amount using offline tokens." else "Received INR $amount as offline tokens.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        if (isSender && qrContent != null) {
            Spacer(modifier = Modifier.height(24.dp))
            val bitmap = QRUtils.generateQRCode(qrContent)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Final confirmation QR",
                    modifier = Modifier.size(220.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onDone) {
            Text("Done")
        }
    }
}

@Composable
private fun ScanHint(
    modifier: Modifier,
    text: String,
    lastScan: String
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (lastScan.isNotEmpty()) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Last: ${lastScan.take(24)}...",
                    color = Color.Green,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun scanHint(isSender: Boolean, step: TransferStep): String {
    return if (isSender) {
        when (step) {
            TransferStep.REQUEST -> "Scan receiver request"
            TransferStep.HANDOFF -> "Scan receiver ACK"
            else -> "Complete"
        }
    } else {
        when (step) {
            TransferStep.REQUEST -> "Scan sender token handoff"
            TransferStep.ACK -> "Scan final confirmation"
            else -> "Complete"
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@SuppressLint("RestrictedApi")
@Composable
fun CameraPreview(
    onCodeScanned: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )
                                BarcodeScanning.getClient().process(image)
                                    .addOnSuccessListener { barcodes ->
                                        for (barcode in barcodes) {
                                            barcode.rawValue?.let { value -> onCodeScanned(value) }
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
