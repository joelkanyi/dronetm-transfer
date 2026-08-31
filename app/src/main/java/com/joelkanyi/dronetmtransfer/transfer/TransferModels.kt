package com.joelkanyi.dronetmtransfer.transfer

data class Entry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
)

data class DirectoryListing(
    val rootName: String,
    val entries: List<Entry>,
)

sealed interface TransferResult {
    val name: String

    data class Copied(override val name: String, val bytes: Long) : TransferResult

    data class Failed(override val name: String, val reason: String) : TransferResult
}

data class TransferReport(val results: List<TransferResult>) {
    val copied: List<TransferResult.Copied>
        get() = results.filterIsInstance<TransferResult.Copied>()

    val failed: List<TransferResult.Failed>
        get() = results.filterIsInstance<TransferResult.Failed>()
}

sealed interface CopyOutcome {
    data class Completed(val report: TransferReport) : CopyOutcome

    data object SourceUnavailable : CopyOutcome

    data object DestinationUnavailable : CopyOutcome
}
