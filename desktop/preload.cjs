const { contextBridge, ipcRenderer } = require('electron')

function subscribe(channel, callback) {
  const listener = (_event, value) => callback(value)
  ipcRenderer.on(channel, listener)
  return () => ipcRenderer.removeListener(channel, listener)
}

contextBridge.exposeInMainWorld('islandDesktop', {
  setIslandExpanded: (expanded) => ipcRenderer.send('island:set-expanded', expanded),
  setAssistantHeight: (height) => ipcRenderer.send('assistant:set-height', height),
  hideAssistant: () => ipcRenderer.send('assistant:hide'),
  onAssistantHide: (callback) => subscribe('assistant:hide', callback),
  onCollapse: (callback) => subscribe('island:collapse', callback),
  publishMusicState: (state) => ipcRenderer.send('music:state', state),
  getMusicState: () => ipcRenderer.invoke('music:get-state'),
  sendMusicCommand: (action) => ipcRenderer.invoke('music:command', action),
  onMusicCommand: (callback) => subscribe('music:command', callback),
  onMusicState: (callback) => subscribe('music:state', callback),
})
