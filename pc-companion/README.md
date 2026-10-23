# ClariStatus Desktop Companion (WhatsApp 1080p Engine) 🎬💻

A modern desktop application built with Electron and [Baileys](https://github.com/WhiskeySockets/Baileys) (WhatsApp Web Multi-Device protocol).

It allows the **ClariStatus Android App** to upload compressed videos directly to your **WhatsApp Status in true 1080p Full HD (1152 × 1920 @ 4.1 Mbps)** over your local Wi-Fi network, completely bypassing Android's mobile compression limits.

---

## ✨ Features

* **🖥️ Modern Obsidian GUI**: Sleek, frameless dark-themed desktop interface.
* **📱 Visual QR Code Pairing**: Scan the dynamic QR code cleanly on screen—no messy terminal windows.
* **📡 Zero-Config Wi-Fi Auto-Discovery**: Built-in UDP beacon automatically announces the companion to your phone. **Zero IP typing required!**
* **🔔 Windows System Tray**: Minimizes quietly to the system tray and runs in the background.
* **⚡ Native Notifications**: Pops up a Windows notification whenever a 1080p video is received from your phone.

---

## ⚡ Quick Start

### Option A: Portable Standalone Executable (No Node.js Required!)
1. Download **`ClariStatus-Companion-v1.0.exe`** from the [GitHub Releases](https://github.com/banishwor/claristatus/releases).
2. Double-click to launch (no installation wizard, runs portably).
3. Scan the on-screen QR code with WhatsApp (**Linked Devices $\rightarrow$ Link a Device**).
4. That's it! The app minimizes to your system tray and is ready to receive statuses.

---

### Option B: Run from Source (Developers)

Prerequisites: Node.js 18+ ([Download Node.js](https://nodejs.org/)).

```bash
cd pc-companion

# Install dependencies
npm install

# Launch Desktop GUI App
npm start

# (Optional) Build standalone Windows .exe
npm run dist
```

---

## 📱 How Phone Pairing Works

Thanks to **UDP Auto-Discovery**:
1. Keep the Desktop Companion running on your PC (both phone and PC on the same Wi-Fi).
2. On your phone, compress any video under 30 seconds and tap **1080p**.
3. ClariStatus Android immediately finds your PC:
   > **🟢 Connected to: Your-PC-Name (192.168.1.5)**
4. Tap **Send to Status**. Within seconds, your 1080p Full HD video will be live on your WhatsApp Status!

---

## 🔒 Privacy & Security

* **100% Local**: Runs strictly on your Local Area Network (LAN). No cloud servers, proxies, or analytics.
* **Direct Socket**: Videos travel directly from your phone to your PC over Wi-Fi, and from your PC to WhatsApp servers via official encrypted WebSockets.
* **Credentials Protected**: Session keys in `auth_info/` never leave your computer and are excluded from git.
