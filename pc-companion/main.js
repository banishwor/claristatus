import { app, BrowserWindow, Tray, Menu, ipcMain, clipboard, Notification, nativeImage } from 'electron';
import path from 'path';
import { fileURLToPath } from 'url';
import { startCompanionServer, getCompanionStatus, logoutAndReset } from './server.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

let mainWindow = null;
let tray = null;
let serverInstance = null;
let latestQrDataUrl = null;

function createTrayIcon() {
    // 16x16 emerald dot for Windows tray
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16">
        <circle cx="8" cy="8" r="6" fill="#10B981" />
    </svg>`;
    return nativeImage.createFromBuffer(Buffer.from(svg));
}

function createWindow() {
    mainWindow = new BrowserWindow({
        width: 520,
        height: 680,
        resizable: false,
        frame: false,
        backgroundColor: '#090D14',
        webPreferences: {
            preload: path.resolve(__dirname, 'preload.js'),
            contextIsolation: true,
            nodeIntegration: false
        }
    });

    mainWindow.loadFile(path.resolve(__dirname, 'ui', 'index.html'));

    mainWindow.on('close', (event) => {
        if (!app.isQuitting) {
            event.preventDefault();
            mainWindow.hide();
        }
        return false;
    });
}

function setupTray() {
    const icon = createTrayIcon();
    tray = new Tray(icon);
    tray.setToolTip('ClariStatus Companion (Online)');

    const contextMenu = Menu.buildFromTemplate([
        {
            label: 'Open ClariStatus Companion',
            click: () => {
                if (mainWindow) {
                    mainWindow.show();
                    mainWindow.focus();
                }
            }
        },
        {
            label: 'Copy Local IP',
            click: () => {
                const status = getCompanionStatus();
                clipboard.writeText(`${status.localIp}:${status.port}`);
            }
        },
        { type: 'separator' },
        {
            label: 'Quit',
            click: () => {
                app.isQuitting = true;
                app.quit();
            }
        }
    ]);

    tray.setContextMenu(contextMenu);
    tray.on('double-click', () => {
        if (mainWindow) {
            mainWindow.show();
            mainWindow.focus();
        }
    });
}

// IPC Handlers
ipcMain.on('window-minimize', () => {
    if (mainWindow) mainWindow.minimize();
});

ipcMain.on('window-close-to-tray', () => {
    if (mainWindow) {
        mainWindow.hide();
        if (Notification.isSupported()) {
            new Notification({
                title: 'ClariStatus is Running in Background',
                body: 'Your PC is ready to receive 1080p statuses from your phone.'
            }).show();
        }
    }
});

ipcMain.on('copy-clipboard', (_event, text) => {
    clipboard.writeText(text);
});

ipcMain.handle('unlink-device', async () => {
    return await logoutAndReset();
});

ipcMain.handle('get-initial-state', () => {
    const status = getCompanionStatus();
    return {
        ...status,
        qrDataUrl: latestQrDataUrl
    };
});

// App Lifecycle
app.whenReady().then(() => {
    createWindow();
    setupTray();

    // Start background Baileys & Express Server
    serverInstance = startCompanionServer({
        onQr: ({ dataUrl }) => {
            latestQrDataUrl = dataUrl;
            if (mainWindow && !mainWindow.isDestroyed()) {
                mainWindow.webContents.send('qr-update', { dataUrl });
            }
        },
        onConnectionChange: ({ isConnected }) => {
            if (isConnected) latestQrDataUrl = null;
            if (mainWindow && !mainWindow.isDestroyed()) {
                mainWindow.webContents.send('status-update', getCompanionStatus());
            }
        },
        onUploadEvent: (event) => {
            if (mainWindow && !mainWindow.isDestroyed()) {
                mainWindow.webContents.send('upload-event', event);
            }
            if (event.type === 'upload_success' && Notification.isSupported()) {
                new Notification({
                    title: '1080p Status Delivered! 🎬',
                    body: `Delivered ${event.sizeMb} MB to WhatsApp. Forward it to My Status!`
                }).show();
            }
        }
    });

    app.on('activate', () => {
        if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
});

app.on('before-quit', () => {
    app.isQuitting = true;
});

app.on('window-all-closed', () => {
    // Keep running in system tray on Windows
});
