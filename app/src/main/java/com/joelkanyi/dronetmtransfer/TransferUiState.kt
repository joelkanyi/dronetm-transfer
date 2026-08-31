package com.joelkanyi.dronetmtransfer

import android.net.Uri
import com.joelkanyi.dronetmtransfer.transfer.TransferReport

data class TransferUiState(
    val source: PickedSource? = null,
    val destination: PickedFolder? = null,
    val phase: Phase = Phase.Idle,
) {
    val canCopy: Boolean
        get() = source != null && destination != null && phase !is Phase.Copying
}

data class PickedSource(val treeUri: Uri, val name: String, val itemCount: Int)

data class PickedFolder(val treeUri: Uri, val name: String)

sealed interface Phase {
    data object Idle : Phase

    data class Copying(val current: String, val done: Int, val total: Int) : Phase

    data class Done(val report: TransferReport, val destinationName: String) : Phase

    data class Failed(val message: String) : Phase
}
