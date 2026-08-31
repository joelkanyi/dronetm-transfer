# dronetm-transfer

Copies files off a USB-connected device (drone controller, camera, phone) to a
folder on the phone.

## How it works

1. Plug the device into the phone through an OTG adapter. The app detects it and
   the Diagnostics panel shows USB host support, the attached device, its
   interface class (mass storage or MTP), and whether it can be read.
2. Tap **Open device** and pick it in the system picker. The app lists it over
   `content://`, no mount path.
3. Tap **Choose destination** and pick a folder on the phone.
4. Tap **Copy files**. Each file is streamed across, subfolders preserved, with
   per-file progress and a result showing what copied and what failed.
5. Tap **Open in Files** to see the copied files.

## Build

```
./gradlew installDebug
```
