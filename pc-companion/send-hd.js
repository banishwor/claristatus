import makeWASocket, {
    useMultiFileAuthState,
    DisconnectReason,
    fetchLatestBaileysVersion
} from '@whiskeysockets/baileys';
import qrcode from 'qrcode-terminal';
import pino from 'pino';
import fs from 'fs';
import path from 'path';

// Command line arguments: node send-hd.js <phoneNumber> <videoFilePath>
const args = process.argv.slice(2);
let rawPhone = args[0];
let videoPathArg = args[1];

// Default configuration fallback
const CONFIG_FILE = path.resolve('./config.json');
let config = {};
if (fs.existsSync(CONFIG_FILE)) {
    try {
        config = JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8'));
    } catch (_) {}
}

const targetPhone = (rawPhone || config.phone || '').replace(/\D/g, '');
const videoPath = path.resolve(videoPathArg || config.video || '../1.mp4');

if (!targetPhone) {
    console.error('================================================================');
    console.error('ERROR: No target phone number specified!');
    console.error('Usage: node send-hd.js <phoneNumberWithCountryCode> [videoPath]');
    console.error('Example: node send-hd.js 919876543210 ../1.mp4');
    console.error('================================================================');
    process.exit(1);
}

if (!fs.existsSync(videoPath)) {
    console.error('================================================================');
    console.error(`ERROR: Video file not found at: ${videoPath}`);
    console.error('================================================================');
    process.exit(1);
}

async function startBot() {
    const authDir = path.resolve('./auth_info');
    const { state, saveCreds } = await useMultiFileAuthState(authDir);
    const { version, isLatest } = await fetchLatestBaileysVersion();

    console.log(`Using Baileys version: v${version.join('.')} (isLatest: ${isLatest})`);

    const sock = makeWASocket({
        version,
        auth: state,
        logger: pino({ level: 'silent' }),
        printQRInTerminal: false,
        browser: ['ClariStatus HD Client', 'Chrome', '120.0.0']
    });

    sock.ev.on('creds.update', saveCreds);

    sock.ev.on('connection.update', async (update) => {
        const { connection, lastDisconnect, qr } = update;

        if (qr) {
            console.log('\n================================================================');
            console.log(' Scan this QR code with WhatsApp:');
            console.log(' (Open WhatsApp on phone -> Settings -> Linked Devices -> Link a Device)');
            console.log('================================================================\n');
            qrcode.generate(qr, { small: true });
        }

        if (connection === 'close') {
            const statusCode = (lastDisconnect?.error)?.output?.statusCode;
            const shouldReconnect = statusCode !== DisconnectReason.loggedOut;
            console.log(`Connection closed (code ${statusCode}). Reconnecting: ${shouldReconnect}`);
            if (shouldReconnect) {
                startBot();
            } else {
                console.log('Logged out from WhatsApp. Delete ./auth_info folder to scan QR code again.');
                process.exit(1);
            }
        } else if (connection === 'open') {
            console.log('\nWhatsApp connection established successfully!');
            const recipientJid = `${targetPhone}@s.whatsapp.net`;
            const stats = fs.statSync(videoPath);
            const sizeMb = (stats.size / (1024 * 1024)).toFixed(2);

            console.log(`\nSending Full HD Video (${sizeMb} MB) to: ${targetPhone}...`);

            try {
                const videoBuffer = fs.readFileSync(videoPath);

                await sock.sendMessage(recipientJid, {
                    video: videoBuffer,
                    caption: 'HD Status Video (Forward this to My Status)',
                    mimetype: 'video/mp4'
                });

                console.log('================================================================');
                console.log(' SUCCESS: Video sent to your WhatsApp chat!');
                console.log(' Now on your phone:');
                console.log(' 1. Open the WhatsApp chat.');
                console.log(' 2. Tap the Forward icon (➔) on the received video.');
                console.log(' 3. Select "My Status".');
                console.log(' WhatsApp will serve the Full HD video directly from CDN!');
                console.log('================================================================\n');

                // Wait 3 seconds for WebSocket to flush pending packets
                setTimeout(() => {
                    process.exit(0);
                }, 3000);
            } catch (err) {
                console.error('Failed to send video:', err);
                process.exit(1);
            }
        }
    });
}

startBot();
