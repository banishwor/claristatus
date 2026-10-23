import dgram from 'dgram';
import os from 'os';

const DISCOVERY_PORT = 3001;
let beaconTimer = null;
let udpSocket = null;

/**
 * Starts a UDP discovery service on the local network.
 * 1. Broadcasts heartbeats every 2.5 seconds to 255.255.255.255:3001.
 * 2. Listens for direct 'claristatus-discover' ping requests from mobile devices on port 3001
 *    and replies immediately with connection details.
 */
export function startDiscoveryService({ port = 3000, getStatus = () => ({}) }) {
    try {
        udpSocket = dgram.createSocket({ type: 'udp4', reuseAddr: true });

        udpSocket.on('error', (err) => {
            console.warn('[Discovery] UDP Socket error:', err.message);
        });

        udpSocket.on('message', (msg, rinfo) => {
            try {
                const text = msg.toString();
                if (text.includes('claristatus-discover')) {
                    const status = getStatus();
                    const reply = JSON.stringify({
                        service: 'claristatus-companion',
                        name: os.hostname(),
                        port: port,
                        isWhatsAppConnected: !!status.isWhatsAppConnected,
                        defaultPhone: status.defaultPhone || null
                    });
                    const replyBuffer = Buffer.from(reply);
                    udpSocket.send(replyBuffer, 0, replyBuffer.length, rinfo.port, rinfo.address);
                }
            } catch (_) {}
        });

        udpSocket.bind(DISCOVERY_PORT, () => {
            try {
                udpSocket.setBroadcast(true);
            } catch (_) {}

            // Periodic heartbeat broadcast
            beaconTimer = setInterval(() => {
                try {
                    const status = getStatus();
                    const broadcastMsg = JSON.stringify({
                        service: 'claristatus-companion',
                        name: os.hostname(),
                        port: port,
                        isWhatsAppConnected: !!status.isWhatsAppConnected
                    });
                    const buffer = Buffer.from(broadcastMsg);
                    udpSocket.send(buffer, 0, buffer.length, DISCOVERY_PORT, '255.255.255.255');
                } catch (_) {}
            }, 2500);
        });

        return () => {
            if (beaconTimer) clearInterval(beaconTimer);
            if (udpSocket) {
                try { udpSocket.close(); } catch (_) {}
            }
        };
    } catch (err) {
        console.warn('[Discovery] Failed to start UDP beacon:', err.message);
        return () => {};
    }
}
