package com.joelkanyi.dronetmtransfer

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.usb.UsbManager
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.joelkanyi.dronetmtransfer.transfer.CopyOutcome
import com.joelkanyi.dronetmtransfer.transfer.TransferReport
import com.joelkanyi.dronetmtransfer.transfer.TransferResult
import com.joelkanyi.dronetmtransfer.transfer.UsbFileTransfer
import com.joelkanyi.dronetmtransfer.ui.theme.DronetmtransferTheme
import com.joelkanyi.dronetmtransfer.usb.UsbDeviceInfo
import com.joelkanyi.dronetmtransfer.usb.UsbDiagnostics
import com.joelkanyi.dronetmtransfer.usb.UsbInspector
import kotlinx.coroutines.launch

@Composable
fun TransferScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transfer = remember { UsbFileTransfer(context) }
    val inspector = remember { UsbInspector(context) }
    val diagnostics = rememberUsbDiagnostics(inspector)
    var state by remember { mutableStateOf(TransferUiState()) }

    val sourcePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        scope.launch {
            val listing = transfer.list(treeUri)
            state = state.copy(
                source = PickedSource(treeUri, listing.rootName, listing.entries.size),
                phase = Phase.Idle,
            )
        }
    }

    val destinationPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val name = DocumentFile.fromTreeUri(context, treeUri)?.name ?: "?"
        state = state.copy(
            destination = PickedFolder(treeUri, name),
            phase = Phase.Idle,
        )
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        TransferContent(
            state = state,
            diagnostics = diagnostics,
            modifier = Modifier.padding(innerPadding),
            onPickSource = { sourcePicker.launch(null) },
            onPickDestination = { destinationPicker.launch(null) },
            onCopy = {
                val source = state.source ?: return@TransferContent
                val destination = state.destination ?: return@TransferContent
                scope.launch {
                    val outcome = transfer.copyTree(
                        sourceTreeUri = source.treeUri,
                        destTreeUri = destination.treeUri,
                    ) { name, done, total ->
                        state = state.copy(phase = Phase.Copying(name, done, total))
                    }
                    state = state.copy(phase = outcome.toPhase(destination.name))
                }
            },
            onOpenDestination = {
                state.destination?.let { openInFiles(context, it.treeUri) }
            },
        )
    }
}

@Composable
private fun rememberUsbDiagnostics(inspector: UsbInspector): UsbDiagnostics {
    val context = LocalContext.current
    var diagnostics by remember { mutableStateOf(inspector.inspect()) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                diagnostics = inspector.inspect()
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return diagnostics
}

@Composable
fun TransferContent(
    state: TransferUiState,
    diagnostics: UsbDiagnostics,
    modifier: Modifier = Modifier,
    onPickSource: () -> Unit,
    onPickDestination: () -> Unit,
    onCopy: () -> Unit,
    onOpenDestination: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("USB file transfer", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Copy files from a USB device or folder to a place you can open.",
            style = MaterialTheme.typography.bodyMedium,
        )

        DiagnosticsCard(diagnostics, state.source?.itemCount)

        StepCard(
            title = "1. Source",
            chosen = state.source?.let { "${it.name}  (${itemCount(it.itemCount)})" },
            chooseLabel = diagnostics.device?.let { "Open ${it.name}" } ?: "Choose source",
            onChoose = onPickSource,
        )
        StepCard(
            title = "2. Destination",
            chosen = state.destination?.name,
            chooseLabel = "Choose destination",
            onChoose = onPickDestination,
        )

        Button(
            onClick = onCopy,
            enabled = state.canCopy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Copy files") }

        when (val phase = state.phase) {
            Phase.Idle -> Unit
            is Phase.Copying -> CopyingBody(phase)
            is Phase.Done -> DoneBody(phase, onOpenDestination)
            is Phase.Failed -> FailedBody(phase)
        }
    }
}

private fun CopyOutcome.toPhase(destinationName: String): Phase =
    when (this) {
        is CopyOutcome.Completed -> Phase.Done(report, destinationName)
        CopyOutcome.SourceUnavailable ->
            Phase.Failed("Source is not reachable. Reconnect the device and pick it again.")
        CopyOutcome.DestinationUnavailable ->
            Phase.Failed("Destination is not writable. Pick the destination again.")
    }

@Composable
private fun DiagnosticsCard(diagnostics: UsbDiagnostics, sourceItemCount: Int?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
            val device = diagnostics.device
            DiagnosticsRow("USB host support", if (diagnostics.hostSupported) "Yes" else "No")
            DiagnosticsRow(
                "Device attached",
                device?.let { "${it.name}  (${hex(it.vendorId)}/${hex(it.productId)})" } ?: "None",
            )
            DiagnosticsRow("Interface class", device?.let(::interfaceLabel) ?: "-")
            DiagnosticsRow(
                "File access (SAF)",
                sourceItemCount?.let { "OK - ${itemCount(it)}" } ?: "-",
            )
        }
    }
}

@Composable
private fun DiagnosticsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StepCard(
    title: String,
    chosen: String?,
    chooseLabel: String,
    onChoose: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (chosen == null) {
                Button(onClick = onChoose) { Text(chooseLabel) }
            } else {
                Text(chosen, maxLines = 1, overflow = TextOverflow.Ellipsis)
                OutlinedButton(onClick = onChoose) { Text("Change") }
            }
        }
    }
}

@Composable
private fun CopyingBody(phase: Phase.Copying) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Copying ${phase.done + 1} of ${phase.total}")
        Text(phase.current, maxLines = 1, overflow = TextOverflow.Ellipsis)
        LinearProgressIndicator(
            progress = { if (phase.total == 0) 0f else phase.done.toFloat() / phase.total },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DoneBody(phase: Phase.Done, onOpenDestination: () -> Unit) {
    val report = phase.report
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Copied ${report.copied.size} file(s) to ${phase.destinationName}." +
                if (report.failed.isNotEmpty()) "  ${report.failed.size} failed." else "",
            style = MaterialTheme.typography.titleMedium,
        )
        Button(onClick = onOpenDestination) { Text("Open in Files") }
        if (report.failed.isNotEmpty()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text("Failed:", color = MaterialTheme.colorScheme.error)
                }
                items(report.failed) { failure ->
                    Text(
                        "${failure.name}: ${failure.reason}",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun FailedBody(phase: Phase.Failed) {
    Text(phase.message, color = MaterialTheme.colorScheme.error)
}

private fun itemCount(count: Int): String = if (count == 1) "1 item" else "$count items"

private fun hex(value: Int): String = "0x%04x".format(value)

private fun interfaceLabel(device: UsbDeviceInfo): String =
    "0x%02x".format(device.interfaceClass) +
        if (device.isMtpOrPtp) " (Still Image / MTP)" else ""

private fun openInFiles(context: Context, treeUri: Uri) {
    val documentUri = DocumentsContract.buildDocumentUriUsingTree(
        treeUri,
        DocumentsContract.getTreeDocumentId(treeUri),
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Unit
    }
}

private val sampleDiagnostics = UsbDiagnostics(
    hostSupported = true,
    device = UsbDeviceInfo(
        name = "DJI RC2",
        vendorId = 0x2ca3,
        productId = 0x001f,
        interfaceClass = 0x06,
        isMtpOrPtp = true,
    ),
)

@Preview(name = "No device", showBackground = true)
@Preview(name = "No device dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NoDevicePreview() {
    DronetmtransferTheme {
        TransferContent(
            state = TransferUiState(),
            diagnostics = UsbDiagnostics(hostSupported = true, device = null),
            onPickSource = {},
            onPickDestination = {},
            onCopy = {},
            onOpenDestination = {},
        )
    }
}

@Preview(name = "Ready", showBackground = true)
@Preview(name = "Ready dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadyPreview() {
    DronetmtransferTheme {
        TransferContent(
            state = TransferUiState(
                source = PickedSource(Uri.EMPTY, "DJI RC2", itemCount = 47),
                destination = PickedFolder(Uri.EMPTY, "Downloads/DroneTM"),
            ),
            diagnostics = sampleDiagnostics,
            onPickSource = {},
            onPickDestination = {},
            onCopy = {},
            onOpenDestination = {},
        )
    }
}

@Preview(name = "Copying", showBackground = true)
@Preview(name = "Copying dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CopyingPreview() {
    DronetmtransferTheme {
        TransferContent(
            state = TransferUiState(
                source = PickedSource(Uri.EMPTY, "DJI RC2", itemCount = 47),
                destination = PickedFolder(Uri.EMPTY, "Downloads/DroneTM"),
                phase = Phase.Copying(current = "DCIM/100MEDIA/DJI_0001.JPG", done = 3, total = 47),
            ),
            diagnostics = sampleDiagnostics,
            onPickSource = {},
            onPickDestination = {},
            onCopy = {},
            onOpenDestination = {},
        )
    }
}

@Preview(name = "Done", showBackground = true)
@Preview(name = "Done dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DonePreview() {
    DronetmtransferTheme {
        TransferContent(
            state = TransferUiState(
                source = PickedSource(Uri.EMPTY, "DJI RC2", itemCount = 47),
                destination = PickedFolder(Uri.EMPTY, "Downloads/DroneTM"),
                phase = Phase.Done(
                    report = TransferReport(
                        listOf(
                            TransferResult.Copied("DJI_0001.JPG", bytes = 4_812_345),
                            TransferResult.Failed("DJI_0002.JPG", reason = "could not open stream"),
                        ),
                    ),
                    destinationName = "Downloads/DroneTM",
                ),
            ),
            diagnostics = sampleDiagnostics,
            onPickSource = {},
            onPickDestination = {},
            onCopy = {},
            onOpenDestination = {},
        )
    }
}
