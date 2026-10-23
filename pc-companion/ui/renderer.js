// Elements
const viewPairing = document.getElementById('view-pairing');
const viewConnected = document.getElementById('view-connected');

const qrImage = document.getElementById('qr-image');
const qrPlaceholder = document.getElementById('qr-placeholder');

const valHostname = document.getElementById('val-hostname');
const valIp = document.getElementById('val-ip');
const btnCopyIp = document.getElementById('btn-copy-ip');

const feedList = document.getElementById('feed-list');
const feedEmpty = document.getElementById('feed-empty');

const btnWinMinimize = document.getElementById('btn-win-minimize');
const btnWinClose = document.getElementById('btn-win-close');
const btnMinimizeTray = document.getElementById('btn-minimize-tray');
const btnUnlink = document.getElementById('btn-unlink');

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

btnUnlink?.addEventListener('click', async () => {
    if (confirm('Unlink this device from WhatsApp? You will need to scan a new QR code to reconnect.')) {
        btnUnlink.textContent = 'Unlinking...';
        await window.electronAPI?.unlinkDevice();
        btnUnlink.textContent = 'Unlink';
    }
});

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

// IPC Subscriptions
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

    // Initial Load
    window.electronAPI.getInitialState().then((state) => {
        if (state) {
            setConnectedState(state.isWhatsAppConnected, state);
            if (!state.isWhatsAppConnected && state.qrDataUrl) {
                updateQrCode(state.qrDataUrl);
            }
        }
    });
}
