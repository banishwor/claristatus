// Elements
const viewPairing = document.getElementById('view-pairing');
const viewConnected = document.getElementById('view-connected');

const qrImage = document.getElementById('qr-image');
const qrPlaceholder = document.getElementById('qr-placeholder');
const qrStatusText = document.getElementById('qr-status-text');

const valHostname = document.getElementById('val-hostname');
const valIp = document.getElementById('val-ip');
const btnCopyIp = document.getElementById('btn-copy-ip');
const valLinkedPhone = document.getElementById('val-linked-phone');

const cardActiveTransfer = document.getElementById('card-active-transfer');
const transferSpinner = document.getElementById('transfer-spinner');
const transferStageText = document.getElementById('transfer-stage-text');
const transferPercent = document.getElementById('transfer-percent');
const transferProgressFill = document.getElementById('transfer-progress-fill');
const transferBytesText = document.getElementById('transfer-bytes-text');
const transferTargetPhone = document.getElementById('transfer-target-phone');

const feedList = document.getElementById('feed-list');
const feedEmpty = document.getElementById('feed-empty');
const btnClearFeed = document.getElementById('btn-clear-feed');

const btnWinMinimize = document.getElementById('btn-win-minimize');
const btnWinMaximize = document.getElementById('btn-win-maximize');
const btnWinClose = document.getElementById('btn-win-close');
const btnMinimizeTray = document.getElementById('btn-minimize-tray');
const btnUnlink = document.getElementById('btn-unlink');
const btnResetSession = document.getElementById('btn-reset-session');

let activeTransferHideTimeout = null;

// Synthesized audio feedback
function playSuccessChime() {
    try {
        const AudioCtx = window.AudioContext || window.webkitAudioContext;
        if (!AudioCtx) return;
        const ctx = new AudioCtx();
        const now = ctx.currentTime;
        
        // Note 1
        const osc1 = ctx.createOscillator();
        const gain1 = ctx.createGain();
        osc1.type = 'sine';
        osc1.frequency.setValueAtTime(587.33, now); // D5
        gain1.gain.setValueAtTime(0.12, now);
        gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.3);
        osc1.connect(gain1);
        gain1.connect(ctx.destination);
        osc1.start(now);
        osc1.stop(now + 0.3);

        // Note 2
        const osc2 = ctx.createOscillator();
        const gain2 = ctx.createGain();
        osc2.type = 'sine';
        osc2.frequency.setValueAtTime(880.00, now + 0.12); // A5
        gain2.gain.setValueAtTime(0.15, now + 0.12);
        gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.5);
        osc2.connect(gain2);
        gain2.connect(ctx.destination);
        osc2.start(now + 0.12);
        osc2.stop(now + 0.5);
    } catch (_) {}
}

// Window Controls
btnWinMinimize?.addEventListener('click', () => {
    window.electronAPI?.minimizeWindow();
});

btnWinMaximize?.addEventListener('click', () => {
    window.electronAPI?.maximizeWindow();
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

btnClearFeed?.addEventListener('click', () => {
    feedList.innerHTML = '';
    feedList.appendChild(feedEmpty);
    feedEmpty.style.display = 'block';
    btnClearFeed.style.display = 'none';
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
        if (valLinkedPhone) {
            const phone = statusData.linkedPhone || statusData.defaultPhone;
            if (phone) {
                valLinkedPhone.textContent = `Linked WhatsApp: +${phone}`;
            } else {
                valLinkedPhone.textContent = 'WhatsApp Linked & Active';
            }
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

function handleUploadEvent(event) {
    if (!cardActiveTransfer) return;

    if (activeTransferHideTimeout) {
        clearTimeout(activeTransferHideTimeout);
        activeTransferHideTimeout = null;
    }

    if (event.type === 'upload_start') {
        cardActiveTransfer.style.display = 'flex';
        transferSpinner.style.display = 'inline-block';
        transferStageText.textContent = 'Receiving 1080p Video from Phone...';
        transferPercent.textContent = '0%';
        transferProgressFill.style.width = '2%';
        transferBytesText.textContent = `0.0 MB / ${event.totalMb || '?'} MB`;
        transferTargetPhone.textContent = event.targetPhone ? `To: +${event.targetPhone}` : 'To: WhatsApp';
    } else if (event.type === 'upload_progress') {
        cardActiveTransfer.style.display = 'flex';
        transferSpinner.style.display = 'inline-block';
        
        const pct = Math.max(2, Math.min(100, event.percent || 0));
        transferPercent.textContent = `${pct}%`;
        transferProgressFill.style.width = `${pct}%`;

        if (event.stage === 'transmitting') {
            transferStageText.textContent = 'Injecting 1080p stream to WhatsApp...';
            transferBytesText.textContent = `${event.sizeMb || event.receivedMb || '?'} MB (Uploaded to PC)`;
        } else {
            transferStageText.textContent = 'Receiving 1080p Video from Phone...';
            transferBytesText.textContent = `${event.receivedMb || '0.0'} MB / ${event.totalMb || '?'} MB`;
        }
        if (event.targetPhone) {
            transferTargetPhone.textContent = `To: +${event.targetPhone}`;
        }
    } else if (event.type === 'upload_success') {
        cardActiveTransfer.style.display = 'flex';
        transferSpinner.style.display = 'none';
        transferStageText.textContent = '✅ 1080p Status Delivered to WhatsApp!';
        transferPercent.textContent = '100%';
        transferProgressFill.style.width = '100%';
        transferBytesText.textContent = `${event.sizeMb} MB • Ready to Forward`;
        
        playSuccessChime();
        addFeedEvent(event);

        activeTransferHideTimeout = setTimeout(() => {
            cardActiveTransfer.style.display = 'none';
        }, 7000);
    } else if (event.type === 'upload_error') {
        cardActiveTransfer.style.display = 'flex';
        transferSpinner.style.display = 'none';
        transferStageText.textContent = `❌ Upload Failed: ${event.error || 'Unknown error'}`;
        transferProgressFill.style.width = '100%';
        transferProgressFill.style.background = '#EF4444';

        addFeedEvent(event);

        activeTransferHideTimeout = setTimeout(() => {
            cardActiveTransfer.style.display = 'none';
            transferProgressFill.style.background = '';
        }, 8000);
    }
}

function addFeedEvent(event) {
    if (feedEmpty) {
        feedEmpty.style.display = 'none';
    }
    if (btnClearFeed) {
        btnClearFeed.style.display = 'inline-block';
    }

    const item = document.createElement('div');
    item.className = 'feed-item';

    const isSuccess = event.type === 'upload_success';
    const isError = event.type === 'upload_error';

    const icon = isSuccess ? '✅' : isError ? '❌' : '📤';
    const title = isSuccess 
        ? `Delivered ${event.sizeMb} MB 1080p Video` 
        : isError 
        ? `Upload failed: ${event.error}` 
        : `Uploading ${event.sizeMb || ''} MB...`;
    
    const target = event.targetPhone ? `+${event.targetPhone}` : '';

    item.innerHTML = `
        <div style="display: flex; flex-direction: column; gap: 2px;">
            <span class="event-title">${icon} ${title}</span>
            ${target ? `<span style="font-size: 11px; color: var(--emerald-light); font-family: monospace;">${target}</span>` : ''}
        </div>
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
        handleUploadEvent(event);
    });
}

// Run sync immediately and poll every 1 second
syncState();
setInterval(syncState, 1000);
