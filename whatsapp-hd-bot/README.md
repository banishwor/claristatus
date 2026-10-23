# ClariStatus PC Companion (WhatsApp 1080p Bot)

A lightweight local Node.js companion service using [Baileys](https://github.com/WhiskeySockets/Baileys) (WhatsApp Web Multi-Device protocol). 

It allows the **ClariStatus Android App** to upload compressed videos directly to your **WhatsApp Status in true 1080p Full HD (1152 × 1920 @ 4.1 Mbps)** over your local Wi-Fi network, completely bypassing Android's mobile compression limits.

---

## 🚀 Why is this needed?

WhatsApp for Android enforces aggressive compression:
* **Direct Status Upload:** Compressed down to **508 × 848 (SD) @ 1.5 Mbps** (blurry).
* **In-App HD Chat (2-Step):** Capped at **768 × 1280 (HD) @ 2.6 Mbps**.
* **PC Companion (This Bot):** Connects via the WhatsApp Web Multi-Device protocol and transmits directly to WhatsApp CDN at **1152 × 1920 (Full HD) @ 4.1 Mbps**. Identical quality to paid apps like PureStatus, but 100% free and open-source!

---

## 📋 Prerequisites

* **Node.js**: Version 18 or newer ([Download Node.js](https://nodejs.org/)).
* Both your **PC and Android phone must be connected to the same Wi-Fi network**.

---

## ⚡ Quick Start Guide (3 Simple Steps)

### Step 1: Install Dependencies
Open a terminal (PowerShell, Command Prompt, or Terminal) in this `whatsapp-hd-bot` directory:

```bash
npm install
```

### Step 2: Start the Companion Server
Run:

```bash
npm start
```

### Step 3: Link Your WhatsApp
1. A QR code will appear directly in your terminal.
2. Open WhatsApp on your phone:
   * **Android:** Tap the three dots `⋮` at the top right $\rightarrow$ **Linked devices** $\rightarrow$ **Link a device**.
   * **iOS:** Go to **Settings** $\rightarrow$ **Linked Devices** $\rightarrow$ **Link a Device**.
3. Point your camera at the QR code in your terminal.
4. Once scanned, the terminal will show:
   ```text
   ✅ WhatsApp Multi-Device connection established! Ready to post 1080p statuses.
   🚀 ClariStatus Companion listening on http://0.0.0.0:3000
   ```

> [!NOTE]
> Authentication credentials are stored locally in the `auth_info/` folder. You only need to scan the QR code once! On future runs, it reconnects automatically.

---

## 📱 Connecting with the ClariStatus Android App

### 1. Find Your PC's Local IP Address
* **Windows:**
  1. Open Command Prompt or PowerShell.
  2. Type `ipconfig` and press Enter.
  3. Look for **IPv4 Address** under your active Wi-Fi adapter (e.g., `192.168.1.5`).
* **Mac/Linux:**
  1. Run `ifconfig` or `ip addr`.
  2. Look for the `inet` address under `en0` or `wlan0`.

### 2. Send Status from ClariStatus
1. In ClariStatus on your phone, pick and compress any video under 30 seconds.
2. On the **Compression Complete** dialog, tap the **1080p** button.
3. Enter your PC's IP address (e.g., `192.168.1.5`) and your phone number with country code (e.g., `+1234567890`).
4. Tap **Send to WhatsApp Status**.
5. Within seconds, your 1080p Full HD video will be live on your WhatsApp Status!

---

## 🖥️ Standalone CLI Usage (Optional)

You can also send any video directly from your PC terminal without using the phone app:

```bash
node send-hd.js "C:\path\to\your\video.mp4"
```

---

## 🔧 Troubleshooting

| Issue | Solution |
| :--- | :--- |
| **Phone says "Connection failed" or times out** | Make sure your phone and PC are on the same Wi-Fi. Check Windows Firewall: allow port 3000 or allow Node.js through the firewall. |
| **QR code expired** | The QR code refreshes automatically every 20 seconds. If needed, restart the bot with `npm start`. |
| **Session disconnected or revoked** | Delete the `auth_info` folder in `whatsapp-hd-bot/` and run `npm start` to generate a fresh QR code. |
| **Video rejected by WhatsApp** | WhatsApp Status clips must be **30 seconds or less** for 1080p. Clips between 31s–60s should use the 720p phone share method. |

---

## 🔒 Privacy & Security

* **100% Local**: The companion server runs exclusively on your local area network (LAN).
* **Direct Connection**: No third-party cloud servers, telemetry, or external APIs are used. Videos travel directly from your phone to your PC, and from your PC to WhatsApp servers via official encrypted WebSockets.
* **Credentials Protected**: Session keys in `auth_info/` never leave your computer and are excluded from version control via `.gitignore`.
