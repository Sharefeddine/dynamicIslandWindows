const path = require('node:path')
const { pathToFileURL } = require('node:url')
const { app, BrowserWindow, ipcMain, Menu, Tray, nativeImage, net, protocol, screen, session } = require('electron')

// A secure, local origin lets Chromium load ES modules, audio, and microphone
// permissions without disabling web security or running a local HTTP server.
protocol.registerSchemesAsPrivileged([{ scheme: 'island', privileges: { standard: true, secure: true, supportFetchAPI: true, stream: true } }])

const ISLAND_WIDTH = 410
const ISLAND_COLLAPSED_HEIGHT = 84
const ISLAND_EXPANDED_HEIGHT = 405
const ASSISTANT_WIDTH = 390
const ASSISTANT_INITIAL_HEIGHT = 540

let islandWindow
let assistantWindow
let tray
let quitting = false
let musicState = { trackIndex: 0, playing: false, volume: 0.55 }

// Soundscapes only start on an explicit user command, including commands sent
// from the other overlay window. This lets that cross-window gesture work.
app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required')

function senderIs(event, window) {
  return window && !window.isDestroyed() && event.sender.id === window.webContents.id
}

function updateTray() {
  if (!tray) return
  const visible = Boolean(assistantWindow && !assistantWindow.isDestroyed() && assistantWindow.isVisible())
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: 'Show JARVIS', type: 'checkbox', checked: visible, click: () => visible ? hideAssistant() : showAssistant() },
    { type: 'separator' },
    { label: 'Quit Island', click: () => app.quit() },
  ]))
}

function showAssistant() {
  if (!assistantWindow || assistantWindow.isDestroyed()) return
  assistantWindow.show()
  updateTray()
}

function hideAssistant() {
  if (!assistantWindow || assistantWindow.isDestroyed()) return
  // Hiding the visual should never leave the microphone running.
  assistantWindow.webContents.send('assistant:hide')
  assistantWindow.hide()
  updateTray()
}

function placeWindows() {
  const area = screen.getPrimaryDisplay().workArea
  const center = area.x + Math.round(area.width / 2)
  if (islandWindow && !islandWindow.isDestroyed()) {
    islandWindow.setPosition(center - Math.round(ISLAND_WIDTH / 2), area.y)
  }
  if (assistantWindow && !assistantWindow.isDestroyed()) {
    const x = Math.max(area.x, Math.min(center + Math.round(ISLAND_WIDTH / 2) + 12, area.x + area.width - ASSISTANT_WIDTH))
    assistantWindow.setPosition(x, area.y + 90)
  }
}

function makeWindow(options) {
  const window = new BrowserWindow({
    ...options,
    frame: false,
    transparent: true,
    backgroundColor: '#00000000',
    alwaysOnTop: true,
    resizable: false,
    minimizable: false,
    maximizable: false,
    fullscreenable: false,
    skipTaskbar: true,
    show: false,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })
  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }))
  window.webContents.on('will-navigate', (event) => event.preventDefault())
  return window
}

function createWindows() {
  islandWindow = makeWindow({ width: ISLAND_WIDTH, height: ISLAND_COLLAPSED_HEIGHT, title: 'Island' })
  assistantWindow = makeWindow({ width: ASSISTANT_WIDTH, height: ASSISTANT_INITIAL_HEIGHT, title: 'JARVIS' })

  islandWindow.on('blur', () => {
    if (!islandWindow.isDestroyed()) islandWindow.webContents.send('island:collapse')
  })
  assistantWindow.on('close', (event) => {
    if (!quitting) { event.preventDefault(); hideAssistant() }
  })

  placeWindows()
  islandWindow.loadURL('island://app/index.html?overlay=island')
  assistantWindow.loadURL('island://app/index.html?overlay=assistant')
  islandWindow.once('ready-to-show', () => islandWindow.show())
  assistantWindow.once('ready-to-show', () => { assistantWindow.show(); updateTray() })
}

ipcMain.on('island:set-expanded', (event, expanded) => {
  if (!senderIs(event, islandWindow) || typeof expanded !== 'boolean') return
  islandWindow.setSize(ISLAND_WIDTH, expanded ? ISLAND_EXPANDED_HEIGHT : ISLAND_COLLAPSED_HEIGHT)
})

ipcMain.on('assistant:set-height', (event, height) => {
  if (!senderIs(event, assistantWindow) || !Number.isFinite(height)) return
  const maxHeight = screen.getDisplayMatching(assistantWindow.getBounds()).workArea.height - 90
  assistantWindow.setSize(ASSISTANT_WIDTH, Math.max(360, Math.min(Math.round(height), maxHeight)))
})

ipcMain.on('assistant:hide', (event) => {
  if (senderIs(event, assistantWindow)) hideAssistant()
})

ipcMain.on('music:state', (event, state) => {
  if (!senderIs(event, islandWindow) || !state || !Number.isInteger(state.trackIndex) || state.trackIndex < 0 || state.trackIndex > 2 || typeof state.playing !== 'boolean' || !Number.isFinite(state.volume)) return
  musicState = { trackIndex: state.trackIndex, playing: state.playing, volume: Math.max(0, Math.min(1, state.volume)) }
  if (assistantWindow && !assistantWindow.isDestroyed()) assistantWindow.webContents.send('music:state', musicState)
})

ipcMain.handle('music:get-state', (event) => senderIs(event, assistantWindow) ? musicState : null)
ipcMain.handle('music:command', (event, action) => {
  if (!senderIs(event, assistantWindow) || !['play', 'pause', 'next', 'previous'].includes(action) || !islandWindow || islandWindow.isDestroyed()) {
    throw new Error('Music controller is unavailable')
  }
  islandWindow.webContents.send('music:command', action)
})

const hasLock = app.requestSingleInstanceLock()
if (!hasLock) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (islandWindow && !islandWindow.isDestroyed()) islandWindow.show()
    showAssistant()
  })
  app.whenReady().then(() => {
    const root = path.resolve(__dirname, '..', 'dist')
    protocol.handle('island', (request) => {
      try {
        const url = new URL(request.url)
        if (url.hostname !== 'app') return new Response('Not found', { status: 404 })
        const file = path.resolve(root, `.${decodeURIComponent(url.pathname)}`)
        if (file !== root && !file.startsWith(`${root}${path.sep}`)) return new Response('Not found', { status: 404 })
        return net.fetch(pathToFileURL(file).toString(), { headers: request.headers }).catch(() => new Response('Not found', { status: 404 }))
      } catch {
        return new Response('Not found', { status: 404 })
      }
    })
    Menu.setApplicationMenu(null)
    session.defaultSession.setPermissionRequestHandler((webContents, permission, callback, details) => {
      const assistantRequest = assistantWindow && !assistantWindow.isDestroyed() && webContents?.id === assistantWindow.webContents.id
      const audioOnly = !details?.mediaTypes || !details.mediaTypes.includes('video')
      callback(Boolean(assistantRequest && permission === 'media' && audioOnly))
    })
    createWindows()
    tray = new Tray(nativeImage.createFromPath(path.join(__dirname, 'tray.png')))
    tray.setToolTip('Island — your desktop companion')
    tray.on('click', () => assistantWindow?.isVisible() ? hideAssistant() : showAssistant())
    updateTray()
    screen.on('display-metrics-changed', placeWindows)
    screen.on('display-added', placeWindows)
    screen.on('display-removed', placeWindows)
  })
}

app.on('before-quit', () => { quitting = true })
app.on('window-all-closed', () => app.quit())
