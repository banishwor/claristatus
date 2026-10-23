// Elements
const viewPairing = document.getElementById('view-pairing');
const viewConnected = document.getElementById('view-connected');

const qrImage = document.getElementById('qr-image');
const qrPlaceholder = document.getElementById('qr-placeholder');
const qrStatusText = document.getElementById('qr-status-text');

const valHostname = document.getElementById('val-hostname');
const valIp = document.getElementById('val-ip');
const btnCopyIp = document.getElementById('btn-copy-ip');

const feedList = document.getElementById('feed-list');
const feedEmpty = document.getElementById('feed-empty');

const btnWinMinimize = document.getElementById('btn-win-minimize');
const btnWinClose = document.getElementById('btn-win-close');
const btnMinimizeTray = document.getElementById('btn-minimize-tray');
const btnUnlink = document.getElementById('btn-unlink');
const btnResetSession = document.getElementById('btn-reset-session');

// Window Controls
btnWinMinimize?.addEventListener('click', () => {
    window.electronAPI?.minimizeWindow();
});

btnWinClose?.addEventListener('click', () => {
    window.electronAPI?.closeToTray();
});

btnMinimizeTray?.addEventListener('click', () => {
    window.electronAPI?.closeToTray();
});

btnCopyIp?.addEventListener('click', () => {
    const text = valIp.textContent.trim();
    window.electronAPI?.copyToClipboard(text);
    btnCopyIp.textContent = 'Copied!';
    setTimeout(() => {
        btnCopyIp.textContent = 'Copy';
    }, 1500);
});

async function handleUnlink() {
    if (confirm('Reset WhatsApp session and generate a new QR code?')) {
        if (qrStatusText) qrStatusText.textContent = 'Resetting session...';
        setConnectedState(false);
        updateQrCode(null);
        await window.electronAPI?.unlinkDevice();
    }
}

btnUnlink?.addEventListener('click', handleUnlink);
btnResetSession?.addEventListener('click', handleUnlink);

// State Management
function setConnectedState(isConnected, statusData = {}) {
    if (isConnected) {
        viewPairing.classList.remove('active');
        viewConnected.classList.add('active');

        if (statusData.name) valHostname.textContent = statusData.name;
        if (statusData.localIp && statusData.port) {
            valIp.textContent = `${statusData.localIp}:${statusData.port}`;
        }
    } else {
        viewConnected.classList.remove('active');
        viewPairing.classList.add('active');
    }
}

function updateQrCode(dataUrl) {
    if (dataUrl) {
        qrPlaceholder.style.display = 'none';
        qrImage.src = dataUrl;
        qrImage.style.display = 'block';
    } else {
        qrImage.style.display = 'none';
        qrPlaceholder.style.display = 'flex';
    }
}

function addFeedEvent(event) {
    if (feedEmpty) {
        feedEmpty.style.display = 'none';
    }

    const item = document.createElement('div');
    item.className = 'feed-item';

    const isSuccess = event.type === 'upload_success';
    const isError = event.type === 'upload_error';

    const icon = isSuccess ? '✅' : isError ? '❌' : '📤';
    const title = isSuccess 
        ? `Delivered ${event.sizeMb} MB to ${event.targetPhone}` 
        : isError 
        ? `Upload failed: ${event.error}` 
        : `Uploading ${event.sizeMb} MB...`;

    item.innerHTML = `
        <span class="event-title">${icon} ${title}</span>
        <span class="event-time">${event.timestamp || 'Now'}</span>
    `;

    feedList.prepend(item);
}

// Polling & IPC Sync
async function syncState() {
    try {
        let state = null;
        if (window.electronAPI) {
            try {
                state = await window.electronAPI.getInitialState();
            } catch (_) {}
        }
        
        // Direct HTTP fallback
        if (!state || (!state.isWhatsAppConnected && !state.qrDataUrl)) {
            try {
                const [statusRes, qrRes] = await Promise.all([
                    fetch('http://127.0.0.1:3000/status').then(r => r.json()).catch(() => null),
                    fetch('http://127.0.0.1:3000/qr').then(r => r.json()).catch(() => null)
                ]);
                if (statusRes || qrRes) {
                    state = {
                        ...(state || {}),
                        ...(statusRes || {}),
                        qrDataUrl: qrRes?.qrDataUrl || state?.qrDataUrl
                    };
                }
            } catch (_) {}
        }

        if (state) {
            setConnectedState(state.isWhatsAppConnected, state);
            if (!state.isWhatsAppConnected) {
                if (state.qrDataUrl) {
                    updateQrCode(state.qrDataUrl);
                } else if (qrStatusText) {
                    qrStatusText.textContent = 'Connecting to WhatsApp...';
                }
            }
        }
    } catch (_) {}
}

if (window.electronAPI) {
    window.electronAPI.onQrUpdate((qrData) => {
        if (qrData?.dataUrl) {
            updateQrCode(qrData.dataUrl);
            setConnectedState(false);
        }
    });

    window.electronAPI.onStatusUpdate((status) => {
        setConnectedState(status.isWhatsAppConnected, status);
    });

    window.electronAPI.onUploadEvent((event) => {
        addFeedEvent(event);
    });
}

// Run sync immediately and poll every 1 second
syncState();
setInterval(syncState, 1000);
