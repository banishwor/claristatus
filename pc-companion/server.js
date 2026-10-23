import express from 'express';
import cors from 'cors';
import makeWASocket, {
    useMultiFileAuthState,
    DisconnectReason,
    fetchLatestBaileysVersion
} from '@whiskeysockets/baileys';
import qrcodeTerminal from 'qrcode-terminal';
import QRCode from 'qrcode';
import pino from 'pino';
import fs from 'fs';
import path from 'path';
import os from 'os';
import { fileURLToPath } from 'url';
import { startDiscoveryService } from './discovery.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = process.env.PORT ? parseInt(process.env.PORT) : 3000;

const USER_DATA_DIR = process.env.APPDATA 
    ? path.join(process.env.APPDATA, 'ClariStatusCompanion') 
    : path.resolve(__dirname, '.claristatus_data');

if (!fs.existsSync(USER_DATA_DIR)) {
    try { fs.mkdirSync(USER_DATA_DIR, { recursive: true }); } catch (_) {}
}

// Migrate existing auth_info if present
const localAuth = path.resolve(__dirname, 'auth_info');
const AUTH_DIR = path.join(USER_DATA_DIR, 'auth_info');
if (fs.existsSync(localAuth) && !fs.existsSync(AUTH_DIR)) {
    try { fs.cpSync(localAuth, AUTH_DIR, { recursive: true }); } catch (_) {}
}

const CONFIG_FILE = path.join(USER_DATA_DIR, 'config.json');

let config = { phone: '' };
if (fs.existsSync(CONFIG_FILE)) {
    try {
        config = JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8'));
    } catch (_) {}
}

let sock = null;
let isConnected = false;
let currentQRDataUrl = null;
let currentRawQR = null;

let eventListeners = {
    onQr: () => {},
    onConnectionChange: () => {},
    onUploadEvent: () => {}
};

export function getLocalIpAddress() {
    const interfaces = os.networkInterfaces();
    for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name]) {
            if (iface.family === 'IPv4' && !iface.internal) {
                return iface.address;
            }
        }
    }
    return '127.0.0.1';
}

export function getCompanionStatus() {
    return {
        status: 'online',
        service: 'ClariStatus-PC-Companion',
        name: os.hostname(),
        localIp: getLocalIpAddress(),
        port: PORT,
        isWhatsAppConnected: isConnected,
        defaultPhone: config.phone || null,
        hasQr: !!currentQRDataUrl
    };
}

export async function logoutAndReset() {
    try {
        if (sock) {
            try { sock.end(undefined); } catch (_) {}
            sock = null;
        }
        isConnected = false;
        currentQRDataUrl = null;
        currentRawQR = null;

        if (fs.existsSync(AUTH_DIR)) {
            try {
                fs.rmSync(AUTH_DIR, { recursive: true, force: true, maxRetries: 3, retryDelay: 100 });
            } catch (e) {
                console.warn('[Companion] Error removing AUTH_DIR:', e.message);
            }
        }

        if (fs.existsSync(localAuth)) {
            try {
                fs.rmSync(localAuth, { recursive: true, force: true, maxRetries: 3, retryDelay: 100 });
            } catch (e) {
                console.warn('[Companion] Error removing localAuth:', e.message);
            }
        }

        eventListeners.onConnectionChange({ isConnected: false, reason: 'unlinked' });
        setTimeout(() => startWhatsAppClient(), 400);
        return { success: true };
    } catch (err) {
        console.error('[Companion] Reset error:', err);
        return { success: false, error: err.message };
    }
}

async function startWhatsAppClient() {
    try {
        if (!fs.existsSync(AUTH_DIR)) {
            fs.mkdirSync(AUTH_DIR, { recursive: true });
        }
        const { state, saveCreds } = await useMultiFileAuthState(AUTH_DIR);
        let version = [2, 3000, 1015901307];
        try {
            const fetched = await Promise.race([
                fetchLatestBaileysVersion(),
                new Promise((_, reject) => setTimeout(() => reject(new Error('Timeout')), 2500))
            ]);
            if (fetched?.version) version = fetched.version;
        } catch (e) {
            console.log('[Baileys] Using default protocol version:', version.join('.'));
        }

        sock = makeWASocket({
            version,
            auth: state,
            logger: pino({ level: 'silent' }),
            printQRInTerminal: false,
            browser: ['ClariStatus Companion', 'Chrome', '122.0.0']
        });

        sock.ev.on('creds.update', saveCreds);

        sock.ev.on('connection.update', async (update) => {
            const { connection, lastDisconnect, qr } = update;

            if (qr) {
                currentRawQR = qr;
                isConnected = false;
                try {
                    currentQRDataUrl = await QRCode.toDataURL(qr, {
                        width: 320,
                        margin: 2,
                        color: {
                            dark: '#0B0F17',
                            light: '#FFFFFF'
                        }
                    });
                } catch (_) {}

                eventListeners.onQr({ raw: qr, dataUrl: currentQRDataUrl });
                qrcodeTerminal.generate(qr, { small: true });
            }

            if (connection === 'close') {
                isConnected = false;
                const statusCode = (lastDisconnect?.error)?.output?.statusCode;
                const shouldReconnect = statusCode !== DisconnectReason.loggedOut;
                console.log(`[Baileys] WhatsApp connection closed. Reconnecting: ${shouldReconnect}`);
                eventListeners.onConnectionChange({ isConnected: false, shouldReconnect });

                if (shouldReconnect) {
                    setTimeout(() => startWhatsAppClient(), 3000);
                } else {
                    console.log('[Baileys] Session logged out. Needs fresh QR code.');
                    logoutAndReset();
                }
            } else if (connection === 'open') {
                isConnected = true;
                currentQRDataUrl = null;
                currentRawQR = null;
                console.log('✓ [Baileys] WhatsApp Connected & Ready for 1080p status uploads!');
                eventListeners.onConnectionChange({ isConnected: true });
            }
        });
    } catch (err) {
        console.error('[Baileys] Error starting client:', err);
    }
}

export function startCompanionServer(callbacks = {}) {
    eventListeners = { ...eventListeners, ...callbacks };

    const app = express();
    app.use(cors());
    app.use(express.raw({ type: ['video/mp4', 'application/octet-stream'], limit: '100mb' }));
    app.use(express.json());

    // Status endpoint for phone
    app.get('/status', (req, res) => {
        res.json(getCompanionStatus());
    });

    // QR image endpoint for UI
    app.get('/qr', (req, res) => {
        res.json({
            isConnected,
            qrDataUrl: currentQRDataUrl
        });
    });

    // Update phone endpoint
    app.post('/set-phone', (req, res) => {
        const { phone } = req.body;
        if (!phone) {
            return res.status(400).json({ error: 'Phone number is required' });
        }
        const cleanPhone = phone.replace(/\D/g, '');
        config.phone = cleanPhone;
        fs.writeFileSync(CONFIG_FILE, JSON.stringify(config, null, 2));
        res.json({ success: true, phone: cleanPhone });
    });

    // Main 1080p status receiver endpoint
    app.post('/send-hd', async (req, res) => {
        try {
            if (!isConnected || !sock) {
                return res.status(503).json({
                    error: 'WhatsApp is not connected yet on PC. Please scan the QR code.'
                });
            }

            const rawPhone = req.headers['x-target-phone'] || req.query.phone || config.phone;
            const targetPhone = (rawPhone ? String(rawPhone) : '').replace(/\D/g, '');

            if (!targetPhone) {
                return res.status(400).json({
                    error: 'Target phone number not specified. Provide header x-target-phone or call /set-phone'
                });
            }

            const videoBuffer = req.body;
            if (!videoBuffer || !videoBuffer.length) {
                return res.status(400).json({ error: 'No video payload received' });
            }

            const sizeMb = (videoBuffer.length / (1024 * 1024)).toFixed(2);
            eventListeners.onUploadEvent({
                type: 'upload_start',
                targetPhone,
                sizeMb,
                timestamp: new Date().toLocaleTimeString()
            });

            const recipientJid = `${targetPhone}@s.whatsapp.net`;

            await sock.sendMessage(recipientJid, {
                video: videoBuffer,
                caption: '1080p Full HD Status (Forward this to My Status)',
                mimetype: 'video/mp4'
            });

            eventListeners.onUploadEvent({
                type: 'upload_success',
                targetPhone,
                sizeMb,
                timestamp: new Date().toLocaleTimeString()
            });

            res.json({
                success: true,
                message: '1080p video delivered to WhatsApp! Open WhatsApp, tap Forward (➔), and choose My Status.'
            });
        } catch (err) {
            console.error('[Companion] Error delivering video:', err);
            eventListeners.onUploadEvent({
                type: 'upload_error',
                error: err.message,
                timestamp: new Date().toLocaleTimeString()
            });
            res.status(500).json({ error: err.message || 'Failed to send video via WhatsApp' });
        }
    });

    const localIp = getLocalIpAddress();
    const serverInstance = app.listen(PORT, '0.0.0.0', () => {
        console.log(`[Companion] Server running on http://${localIp}:${PORT}`);
        startWhatsAppClient();
        startDiscoveryService({ port: PORT, getStatus: getCompanionStatus });
    });

    return {
        app,
        serverInstance,
        getStatus: getCompanionStatus,
        logoutAndReset
    };
}

// Direct execution from CLI
if (process.argv[1] === fileURLToPath(import.meta.url)) {
    startCompanionServer();
}
