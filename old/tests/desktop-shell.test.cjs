const test = require('node:test')
const assert = require('node:assert/strict')
const { EventEmitter } = require('node:events')
const Module = require('node:module')

// Exercise the shell's window/IPC wiring without requiring a graphical desktop.
// A real Windows run is still needed to verify Electron and OS integration.
test('desktop shell positions windows and restricts cross-window messages', async () => {
  const windows = []
  const handles = new Map()
  const schemes = []
  const protocols = new Map()
  const fetched = []
  let permissionHandler
  let nextId = 1

  class WebContents extends EventEmitter {
    constructor() { super(); this.id = nextId++; this.sent = [] }
    send(channel, value) { this.sent.push([channel, value]) }
    setWindowOpenHandler(handler) { this.openHandler = handler }
  }
  class BrowserWindow extends EventEmitter {
    constructor(options) {
      super()
      this.options = options
      this.webContents = new WebContents()
      this.bounds = { x: 0, y: 0, width: options.width, height: options.height }
      this.visible = false
      windows.push(this)
    }
    isDestroyed() { return false }
    isVisible() { return this.visible }
    show() { this.visible = true }
    hide() { this.visible = false }
    setPosition(x, y) { Object.assign(this.bounds, { x, y }) }
    setSize(width, height) { Object.assign(this.bounds, { width, height }) }
    getBounds() { return this.bounds }
    loadURL(url) { this.url = url }
  }
  class Tray extends EventEmitter {
    setContextMenu(menu) { this.menu = menu }
    setToolTip(text) { this.tooltip = text }
  }
  const app = new EventEmitter()
  app.commandLine = { appendSwitch() {} }
  app.requestSingleInstanceLock = () => true
  app.whenReady = () => Promise.resolve()
  app.quit = () => {}
  const ipcMain = new EventEmitter()
  ipcMain.handle = (name, callback) => handles.set(name, callback)
  const screen = new EventEmitter()
  screen.getPrimaryDisplay = () => ({ workArea: { x: 0, y: 0, width: 1440, height: 900 } })
  screen.getDisplayMatching = () => ({ workArea: { x: 0, y: 0, width: 1440, height: 900 } })
  const fakeElectron = {
    app, BrowserWindow, ipcMain, screen, Tray,
    Menu: { setApplicationMenu() {}, buildFromTemplate: (items) => items },
    nativeImage: { createFromPath: (path) => path },
    protocol: { registerSchemesAsPrivileged: (values) => schemes.push(...values), handle: (name, handler) => protocols.set(name, handler) },
    net: { fetch: async (url) => { fetched.push(url); return new Response('ok') } },
    session: { defaultSession: { setPermissionRequestHandler: (handler) => { permissionHandler = handler } } },
  }
  const originalLoad = Module._load
  Module._load = function (request, parent, isMain) {
    if (request === 'electron') return fakeElectron
    return originalLoad.call(this, request, parent, isMain)
  }
  try { require('../desktop/main.cjs') } finally { Module._load = originalLoad }
  await new Promise((resolve) => setImmediate(resolve))

  assert.equal(windows.length, 2)
  const [island, assistant] = windows
  assert.equal(island.bounds.x, 515) // centered within a 1440px work area
  assert.equal(island.bounds.y, 0)
  assert.equal(assistant.bounds.y, 90)
  assert.equal(island.options.transparent, true)
  assert.equal(island.options.alwaysOnTop, true)
  assert.equal(island.options.webPreferences.contextIsolation, true)
  assert.equal(island.options.webPreferences.nodeIntegration, false)
  assert.equal(island.url, 'island://app/index.html?overlay=island')
  assert.equal(assistant.url, 'island://app/index.html?overlay=assistant')
  assert.equal(schemes[0].privileges.secure, true)

  const serve = protocols.get('island')
  assert.equal((await serve({ url: 'island://app/index.html', headers: new Headers() })).status, 200)
  assert.match(fetched[0], /dist\/index\.html$/)
  assert.equal((await serve({ url: 'island://app/%2e%2e%2fsecret', headers: new Headers() })).status, 404)
  assert.equal((await serve({ url: 'island://other/index.html', headers: new Headers() })).status, 404)

  ipcMain.emit('island:set-expanded', { sender: assistant.webContents }, true)
  assert.equal(island.bounds.height, 84)
  ipcMain.emit('island:set-expanded', { sender: island.webContents }, true)
  assert.equal(island.bounds.height, 405)
  ipcMain.emit('island:set-expanded', { sender: island.webContents }, false)
  assert.equal(island.bounds.height, 84)

  const state = { trackIndex: 1, playing: true, volume: 0.4 }
  ipcMain.emit('music:state', { sender: island.webContents }, state)
  assert.deepEqual(assistant.webContents.sent.at(-1), ['music:state', state])
  assert.deepEqual(handles.get('music:get-state')({ sender: assistant.webContents }), state)
  assert.throws(() => handles.get('music:command')({ sender: island.webContents }, 'play'))
  handles.get('music:command')({ sender: assistant.webContents }, 'next')
  assert.deepEqual(island.webContents.sent.at(-1), ['music:command', 'next'])
  assert.throws(() => handles.get('music:command')({ sender: assistant.webContents }, 'untrusted'))

  assistant.show()
  ipcMain.emit('assistant:hide', { sender: assistant.webContents })
  assert.equal(assistant.visible, false)
  assert.deepEqual(assistant.webContents.sent.at(-1), ['assistant:hide', undefined])
  let allowed
  permissionHandler(assistant.webContents, 'media', (result) => { allowed = result }, { mediaTypes: ['audio'] })
  assert.equal(allowed, true)
  permissionHandler(assistant.webContents, 'media', (result) => { allowed = result }, { mediaTypes: ['video'] })
  assert.equal(allowed, false)
  permissionHandler(island.webContents, 'media', (result) => { allowed = result }, { mediaTypes: ['audio'] })
  assert.equal(allowed, false)
})
