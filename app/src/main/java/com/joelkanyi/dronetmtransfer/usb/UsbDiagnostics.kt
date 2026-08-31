package com.joelkanyi.dronetmtransfer.usb

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

data class UsbDeviceInfo(
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val interfaceClass: Int,
    val isMtpOrPtp: Boolean,
)

data class UsbDiagnostics(
    val hostSupported: Boolean,
    val device: UsbDeviceInfo?,
)

class UsbInspector(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    fun inspect(): UsbDiagnostics =
        UsbDiagnostics(
            hostSupported = context.packageManager
                .hasSystemFeature(PackageManager.FEATURE_USB_HOST),
            device = usbManager.deviceList.values.firstOrNull()?.let(::describe),
        )

    private fun describe(device: UsbDevice): UsbDeviceInfo {
        val stillImage = (0 until device.interfaceCount)
            .map { device.getInterface(it) }
            .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE }
        return UsbDeviceInfo(
            name = device.productName ?: "USB device",
            vendorId = device.vendorId,
            productId = device.productId,
            interfaceClass = stillImage?.interfaceClass ?: device.getInterface(0).interfaceClass,
            isMtpOrPtp = stillImage != null,
        )
    }
}
