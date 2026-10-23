const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
    onQrUpdate: (callback) => ipcRenderer.on('qr-update', (_event, data) => callback(data)),
    onStatusUpdate: (callback) => ipcRenderer.on('status-update', (_event, data) => callback(data)),
    onUploadEvent: (callback) => ipcRenderer.on('upload-event', (_event, data) => callback(data)),
    
    minimizeWindow: () => ipcRenderer.send('window-minimize'),
    closeToTray: () => ipcRenderer.send('window-close-to-tray'),
    unlinkDevice: () => ipcRenderer.invoke('unlink-device'),
    copyToClipboard: (text) => ipcRenderer.send('copy-clipboard', text),
    getInitialState: () => ipcRenderer.invoke('get-initial-state')
});
