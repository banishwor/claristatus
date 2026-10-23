import express from 'express';
import cors from 'cors';
import makeWASocket, {
    useMultiFileAuthState,
    DisconnectReason,
    fetchLatestBaileysVersion
} from '@whiskeysockets/baileys';
import qrcode from 'qrcode-terminal';
import pino from 'pino';
import fs from 'fs';
import path from 'path';
import os from 'os';

const app = express();
app.use(cors());
// Support raw binary video uploads up to 100MB
app.use(express.raw({ type: ['video/mp4', 'application/octet-stream'], limit: '100mb' }));
app.use(express.json());

const PORT = 3000;
const CONFIG_FILE = path.resolve('./config.json');

let config = { phone: '' };
if (fs.existsSync(CONFIG_FILE)) {
    try {
        config = JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8'));
    } catch (_) {}
}

let sock = null;
let isConnected = false;
let currentQR = null;

// Get local network IPv4 address
function getLocalIpAddress() {
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

async function startWhatsAppClient() {
    const authDir = path.resolve('./auth_info');
    const { state, saveCreds } = await useMultiFileAuthState(authDir);
    const { version } = await fetchLatestBaileysVersion();

    sock = makeWASocket({
        version,
        auth: state,
        logger: pino({ level: 'silent' }),
        printQRInTerminal: false,
        browser: ['ClariStatus PC Companion', 'Chrome', '120.0.0']
    });

    sock.ev.on('creds.update', saveCreds);

    sock.ev.on('connection.update', (update) => {
        const { connection, lastDisconnect, qr } = update;

        if (qr) {
            currentQR = qr;
            isConnected = false;
            console.log('\n================================================================');
            console.log(' Scan this QR code with WhatsApp:');
            console.log(' (Open WhatsApp on phone -> Settings -> Linked Devices -> Link a Device)');
            console.log('================================================================\n');
            qrcode.generate(qr, { small: true });
        }

        if (connection === 'close') {
            isConnected = false;
            const statusCode = (lastDisconnect?.error)?.output?.statusCode;
            const shouldReconnect = statusCode !== DisconnectReason.loggedOut;
            console.log(`WhatsApp connection closed. Reconnecting: ${shouldReconnect}`);
            if (shouldReconnect) {
                startWhatsAppClient();
            } else {
                console.log('Session expired. Delete ./auth_info folder and restart.');
            }
        } else if (connection === 'open') {
            isConnected = true;
            currentQR = null;
            console.log('\n✓ WhatsApp Connected & Ready to receive 1080p videos from ClariStatus!');
        }
    });
}

// Health & Status endpoint for phone app
app.get('/status', (req, res) => {
    res.json({
        status: 'online',
        service: 'ClariStatus-PC-Companion',
        isWhatsAppConnected: isConnected,
        defaultPhone: config.phone || null
    });
});

// Configure default phone number
app.post('/set-phone', (req, res) => {
    const { phone } = req.body;
    if (!phone) {
        return res.status(400).json({ error: 'Phone number is required' });
    }
    const cleanPhone = phone.replace(/\D/g, '');
    config.phone = cleanPhone;
    fs.writeFileSync(CONFIG_FILE, JSON.stringify(config, null, 2));
    console.log(`Default phone number updated: ${cleanPhone}`);
    res.json({ success: true, phone: cleanPhone });
});

// Main endpoint: receives video and sends directly to WhatsApp
app.post('/send-hd', async (req, res) => {
    try {
        if (!isConnected || !sock) {
            return res.status(503).json({
                error: 'WhatsApp is not connected yet on PC. Please scan the QR code in the PC terminal.'
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
        console.log(`\n[PC Companion] Received 1080p video (${sizeMb} MB) from phone. Sending to ${targetPhone}...`);

        const recipientJid = `${targetPhone}@s.whatsapp.net`;

        await sock.sendMessage(recipientJid, {
            video: videoBuffer,
            caption: '1080p Full HD Status (Forward this to My Status)',
            mimetype: 'video/mp4'
        });

        console.log(`✓ [PC Companion] Successfully sent to ${recipientJid}!`);
        res.json({
            success: true,
            message: 'Video delivered to WhatsApp! Open WhatsApp, tap Forward (➔), and choose My Status.'
        });
    } catch (err) {
        console.error('[PC Companion] Error sending video:', err);
        res.status(500).json({ error: err.message || 'Failed to send video via WhatsApp socket' });
    }
});

// Start Express and WhatsApp client
const localIp = getLocalIpAddress();
app.listen(PORT, '0.0.0.0', () => {
    console.log('================================================================');
    console.log(` ClariStatus PC Companion Server Running!`);
    console.log(` Local Network Address: http://${localIp}:${PORT}`);
    console.log('================================================================');
    startWhatsAppClient();
});
