import { useCallback, useEffect, useRef, useState } from 'react'
import {
  ArrowDownLeft,
  ArrowRight,
  ArrowUpRight,
  Check,
  ChevronDown,
  Clock3,
  CornerDownLeft,
  Headphones,
  Mic,
  MicOff,
  Music2,
  Pause,
  Play,
  Settings2,
  SkipBack,
  SkipForward,
  Sparkles,
  Volume2,
  VolumeX,
  X,
} from 'lucide-react'
import { commandIntent, formatTime, greetingFor } from './assistant.js'

const asset = (path) => `${import.meta.env.BASE_URL}${path}`
const overlayPart = new URLSearchParams(window.location.search).get('overlay')
const isOverlay = overlayPart === 'island' || overlayPart === 'assistant'
const desktopBridge = window.islandDesktop
const remoteMusic = overlayPart === 'assistant' && Boolean(desktopBridge)

if (isOverlay) document.documentElement.dataset.desktopOverlay = overlayPart

const TRACKS = [
  { title: 'Drift', artist: 'Island Soundscapes', src: asset('audio/drift.mp3'), tone: 'drift' },
  { title: 'Blue Hour', artist: 'Island Soundscapes', src: asset('audio/blue-hour.mp3'), tone: 'blue' },
  { title: 'Still', artist: 'Island Soundscapes', src: asset('audio/still.mp3'), tone: 'still' },
]

function readPreference(key, fallback) {
  try {
    const value = localStorage.getItem(`island:${key}`)
    return value === null ? fallback : JSON.parse(value)
  } catch {
    return fallback
  }
}

function BatteryGlyph({ level, charging = false }) {
  const width = Math.max(0, Math.min(1, level / 100)) * 15
  return (
    <svg className="battery-glyph" width="25" height="15" viewBox="0 0 25 15" fill="none" aria-hidden="true">
      <rect x="0.75" y="1.75" width="20" height="11.5" rx="2.8" stroke="currentColor" strokeOpacity=".7" strokeWidth="1.3" />
      <rect x="3" y="4" width={width} height="7" rx="1.2" fill="currentColor" />
      <path d="M22.5 5.2v4.6c.9-.25 1.5-1.15 1.5-2.3s-.6-2.05-1.5-2.3Z" fill="currentColor" fillOpacity=".65" />
      {charging && <path d="m11.7 3.8-2.4 4h2l-.5 3.4 2.5-4h-2l.4-3.4Z" fill="#102a29" />}
    </svg>
  )
}

function SoundWave({ active = false, className = '' }) {
  return (
    <span className={`sound-wave ${active ? 'is-active' : ''} ${className}`} aria-hidden="true">
      <i /><i /><i /><i /><i />
    </span>
  )
}

function BrandMark() {
  return (
    <span className="brand-mark" aria-hidden="true">
      <svg viewBox="0 0 32 32" fill="none">
        <circle cx="16" cy="16" r="14" stroke="currentColor" strokeOpacity=".35" />
        <circle cx="16" cy="16" r="9.5" stroke="currentColor" strokeOpacity=".65" />
        <path d="M16 8.5c1.1 4.4 3.1 6.4 7.5 7.5-4.4 1.1-6.4 3.1-7.5 7.5-1.1-4.4-3.1-6.4-7.5-7.5 4.4-1.1 6.4-3.1 7.5-7.5Z" fill="currentColor" />
      </svg>
    </span>
  )
}

function CoreVisual() {
  return (
    <span className="core-visual" aria-hidden="true">
      <span className="core-glow" />
      <svg className="core-orbits" viewBox="0 0 260 260" fill="none">
        <defs>
          <radialGradient id="orb-fill" cx="0" cy="0" r="1" gradientTransform="translate(100 88) rotate(49) scale(135)">
            <stop stopColor="#d5f9ff" stopOpacity=".48" />
            <stop offset=".21" stopColor="#6bc3dc" stopOpacity=".22" />
            <stop offset=".58" stopColor="#234e68" stopOpacity=".36" />
            <stop offset="1" stopColor="#071c2c" stopOpacity=".9" />
          </radialGradient>
          <linearGradient id="orbit-stroke" x1="36" y1="22" x2="218" y2="229" gradientUnits="userSpaceOnUse">
            <stop stopColor="#d5faff" stopOpacity=".62" />
            <stop offset=".48" stopColor="#8bc4d8" stopOpacity=".15" />
            <stop offset="1" stopColor="#d5faff" stopOpacity=".48" />
          </linearGradient>
          <filter id="star-glow" x="-100%" y="-100%" width="300%" height="300%">
            <feGaussianBlur stdDeviation="4" result="blur" />
            <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
          </filter>
        </defs>
        <circle cx="130" cy="130" r="112" stroke="#a2d9ec" strokeOpacity=".12" />
        <circle className="outer-dashes" cx="130" cy="130" r="102" stroke="#b8eaff" strokeOpacity=".35" strokeWidth=".8" strokeDasharray="1 7" strokeLinecap="round" />
        <circle cx="130" cy="130" r="91" stroke="url(#orbit-stroke)" strokeWidth="1" />
        <circle className="orbit-segments" cx="130" cy="130" r="91" stroke="#c5f2ff" strokeOpacity=".7" strokeWidth="1.2" strokeDasharray="24 180 52 315" strokeLinecap="round" />
        <circle cx="130" cy="130" r="77" fill="url(#orb-fill)" stroke="#b3eaff" strokeOpacity=".4" />
        <circle cx="130" cy="130" r="66" stroke="#d0f7ff" strokeOpacity=".16" />
        <circle cx="130" cy="130" r="53" stroke="#d5f7ff" strokeOpacity=".13" strokeDasharray="2 5" />
        <circle cx="130" cy="130" r="37" fill="#9dd9ec" fillOpacity=".04" stroke="#d1f6ff" strokeOpacity=".16" />
        <path d="M130 103c3.8 16 10.9 23.2 27 27-16.1 3.8-23.2 10.9-27 27-3.8-16.1-10.9-23.2-27-27 16.1-3.8 23.2-11 27-27Z" fill="#d9faff" filter="url(#star-glow)" />
        <circle cx="28" cy="130" r="4.5" fill="#0b2636" stroke="#bceeff" strokeOpacity=".8" />
        <circle cx="28" cy="130" r="1.5" fill="#d9faff" />
        <circle cx="232" cy="130" r="4.5" fill="#0b2636" stroke="#bceeff" strokeOpacity=".8" />
        <circle cx="232" cy="130" r="1.5" fill="#d9faff" />
        <circle cx="130" cy="18" r="2" fill="#b9efff" fillOpacity=".8" />
        <circle cx="130" cy="242" r="2" fill="#b9efff" fillOpacity=".55" />
        <path d="M130 6v5M130 249v5M6 130h5M249 130h5" stroke="#b9efff" strokeOpacity=".4" strokeLinecap="round" />
      </svg>
    </span>
  )
}

function Switch({ checked, onChange, label }) {
  return (
    <button type="button" className={`switch ${checked ? 'switch-on' : ''}`} role="switch" aria-checked={checked} aria-label={label} onClick={() => onChange(!checked)}>
      <span />
    </button>
  )
}

export default function App() {
  const [now, setNow] = useState(() => new Date())
  const [expanded, setExpanded] = useState(false)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [motion, setMotion] = useState(() => readPreference('motion', !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches))
  const [clock24, setClock24] = useState(() => readPreference('clock24', true))
  const [trackIndex, setTrackIndex] = useState(0)
  const [playing, setPlaying] = useState(false)
  const [currentTime, setCurrentTime] = useState(0)
  const [duration, setDuration] = useState(0)
  const [volume, setVolume] = useState(() => readPreference('volume', 0.55))
  const [lastVolume, setLastVolume] = useState(0.55)
  const [audioError, setAudioError] = useState('')
  const [battery, setBattery] = useState({ level: 72, charging: false, sample: true })
  const [voiceState, setVoiceState] = useState('idle')
  const [interim, setInterim] = useState('')
  const [command, setCommand] = useState('')
  const [response, setResponse] = useState(null)

  const islandRef = useRef(null)
  const assistantRef = useRef(null)
  const settingsRef = useRef(null)
  const inputRef = useRef(null)
  const audioRef = useRef(null)
  const recognitionRef = useRef(null)
  const manualVoiceStop = useRef(false)
  const commandHandlerRef = useRef(null)
  const musicHandlerRef = useRef(null)
  const resumeAfterSkip = useRef(false)
  const track = TRACKS[trackIndex]

  useEffect(() => {
    const interval = window.setInterval(() => setNow(new Date()), 1000)
    return () => window.clearInterval(interval)
  }, [])

  useEffect(() => {
    try {
      localStorage.setItem('island:motion', JSON.stringify(motion))
      localStorage.setItem('island:clock24', JSON.stringify(clock24))
      localStorage.setItem('island:volume', JSON.stringify(volume))
    } catch {
      // Preferences are optional, including in privacy-restricted browsers.
    }
  }, [motion, clock24, volume])

  useEffect(() => {
    if (audioRef.current) audioRef.current.volume = volume
  }, [volume])

  // The native app uses two small windows. The island owns the audio element;
  // the assistant receives its state and sends commands through the main process.
  useEffect(() => {
    if (overlayPart !== 'island' || !desktopBridge) return undefined
    desktopBridge.setIslandExpanded(expanded)
    return undefined
  }, [expanded])

  useEffect(() => {
    if (overlayPart !== 'island' || !desktopBridge) return undefined
    desktopBridge.publishMusicState({ trackIndex, playing, volume })
    return undefined
  }, [trackIndex, playing, volume])

  useEffect(() => {
    if (overlayPart !== 'island' || !desktopBridge) return undefined
    const removeCommand = desktopBridge.onMusicCommand((action) => musicHandlerRef.current?.(action))
    const removeCollapse = desktopBridge.onCollapse(() => setExpanded(false))
    return () => { removeCommand(); removeCollapse() }
  }, [])

  useEffect(() => {
    if (!remoteMusic) return undefined
    let disposed = false
    const apply = (state) => {
      if (disposed || !state) return
      if (Number.isInteger(state.trackIndex) && state.trackIndex >= 0 && state.trackIndex < TRACKS.length) setTrackIndex(state.trackIndex)
      if (typeof state.playing === 'boolean') setPlaying(state.playing)
      if (typeof state.volume === 'number') setVolume(state.volume)
    }
    const remove = desktopBridge.onMusicState(apply)
    desktopBridge.getMusicState().then(apply).catch(() => {})
    return () => { disposed = true; remove() }
  }, [])

  useEffect(() => {
    if (overlayPart !== 'assistant' || !desktopBridge || !assistantRef.current) return undefined
    const element = assistantRef.current
    const resize = () => desktopBridge.setAssistantHeight(Math.ceil(element.getBoundingClientRect().height + 20))
    const observer = new ResizeObserver(resize)
    observer.observe(element)
    resize()
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    if (overlayPart !== 'assistant' || !desktopBridge) return undefined
    return desktopBridge.onAssistantHide(() => {
      manualVoiceStop.current = true
      const recognition = recognitionRef.current
      recognitionRef.current = null
      try { recognition?.abort() } catch { /* Already stopped. */ }
      setVoiceState('idle')
      setInterim('')
    })
  }, [])

  useEffect(() => {
    let batteryManager
    let disposed = false
    const update = () => {
      if (!disposed && batteryManager) {
        setBattery({ level: Math.round(batteryManager.level * 100), charging: batteryManager.charging, sample: false })
      }
    }
    if (typeof navigator.getBattery === 'function') {
      navigator.getBattery().then((manager) => {
        if (disposed) return
        batteryManager = manager
        update()
        manager.addEventListener('levelchange', update)
        manager.addEventListener('chargingchange', update)
      }).catch(() => {})
    }
    return () => {
      disposed = true
      batteryManager?.removeEventListener('levelchange', update)
      batteryManager?.removeEventListener('chargingchange', update)
    }
  }, [])

  useEffect(() => {
    function handlePointer(event) {
      if (islandRef.current && !islandRef.current.contains(event.target)) setExpanded(false)
      if (settingsRef.current && !settingsRef.current.contains(event.target)) setSettingsOpen(false)
    }
    function handleKey(event) {
      if (event.key === 'Escape') {
        setExpanded(false)
        setSettingsOpen(false)
      }
    }
    document.addEventListener('pointerdown', handlePointer)
    document.addEventListener('keydown', handleKey)
    return () => {
      document.removeEventListener('pointerdown', handlePointer)
      document.removeEventListener('keydown', handleKey)
    }
  }, [])

  useEffect(() => () => {
    if (recognitionRef.current) {
      recognitionRef.current.onstart = null
      recognitionRef.current.onend = null
      recognitionRef.current.onerror = null
      recognitionRef.current.onresult = null
      try { recognitionRef.current.abort() } catch { /* Already stopped. */ }
      recognitionRef.current = null
    }
  }, [])

  const playAudio = useCallback(async () => {
    const audio = audioRef.current
    if (!audio) return false
    try {
      setAudioError('')
      await audio.play()
      return true
    } catch {
      setAudioError('Playback could not start. Try pressing play.')
      return false
    }
  }, [])

  useEffect(() => {
    setCurrentTime(0)
    setDuration(0)
    if (resumeAfterSkip.current) {
      resumeAfterSkip.current = false
      playAudio()
    }
  }, [trackIndex, playAudio])

  function togglePlayback() {
    const audio = audioRef.current
    if (!audio) return
    if (audio.paused) playAudio()
    else audio.pause()
  }

  async function skipTrack(direction, shouldPlay = playing) {
    if (remoteMusic) {
      return desktopBridge.sendMusicCommand(direction > 0 ? 'next' : 'previous').then(() => true).catch(() => false)
    }
    resumeAfterSkip.current = shouldPlay
    setTrackIndex((index) => (index + direction + TRACKS.length) % TRACKS.length)
    return true
  }

  musicHandlerRef.current = (action) => {
    if (action === 'play') playAudio()
    if (action === 'pause') audioRef.current?.pause()
    if (action === 'next') skipTrack(1)
    if (action === 'previous') skipTrack(-1)
  }

  function toggleMute() {
    if (volume === 0) setVolume(lastVolume || 0.55)
    else {
      setLastVolume(volume)
      setVolume(0)
    }
  }

  async function runCommand(raw) {
    const clean = raw.trim()
    if (!clean) return
    const intent = commandIntent(clean)
    let answer

    if (intent === 'pause') {
      let paused = true
      if (remoteMusic) paused = await desktopBridge.sendMusicCommand('pause').then(() => true).catch(() => false)
      else if (audioRef.current) audioRef.current.pause()
      else paused = false
      answer = paused ? 'Music paused. A little quiet can be nice, too.' : 'The music player is unavailable right now.'
    } else if (intent === 'play') {
      const started = remoteMusic
        ? await desktopBridge.sendMusicCommand('play').then(() => true).catch(() => false)
        : await playAudio()
      answer = started ? `Playing “${track.title}” for you.` : 'Press play in the island to start the soundscape.'
    } else if (intent === 'next') {
      const next = TRACKS[(trackIndex + 1) % TRACKS.length]
      const changed = await skipTrack(1, playing)
      answer = changed ? `${playing ? 'Playing' : 'Selected'} “${next.title}”.` : 'The music player is unavailable right now.'
    } else if (intent === 'previous') {
      const previous = TRACKS[(trackIndex - 1 + TRACKS.length) % TRACKS.length]
      const changed = await skipTrack(-1, playing)
      answer = changed ? `${playing ? 'Playing' : 'Selected'} “${previous.title}”.` : 'The music player is unavailable right now.'
    } else if (intent === 'time') {
      answer = `It’s ${new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit', hour12: !clock24 }).format(new Date())}.`
    } else if (intent === 'date') {
      answer = `Today is ${new Intl.DateTimeFormat(undefined, { weekday: 'long', month: 'long', day: 'numeric' }).format(new Date())}.`
    } else if (intent === 'battery') {
      answer = battery.sample
        ? 'Battery information isn’t available here, so 72% is shown as a sample.'
        : `Your battery is at ${battery.level}%${battery.charging ? ' and charging' : ''}.`
    } else if (intent === 'greeting') {
      answer = 'Hello. I’m here for music, the time, and the little things.'
    } else if (intent === 'help') {
      answer = 'I can play or pause music, change tracks, and check the time, date, or battery.'
    } else {
      answer = 'I can help with music, the time, date, or battery. Try “play music” or “what time is it?”'
    }
    setResponse({ question: clean, text: answer })
    setCommand('')
  }
  commandHandlerRef.current = runCommand

  function toggleListening() {
    if (voiceState === 'listening' || voiceState === 'starting') {
      manualVoiceStop.current = true
      const recognition = recognitionRef.current
      recognitionRef.current = null
      try { recognition?.abort() } catch { /* Already stopped. */ }
      setVoiceState('idle')
      setInterim('')
      return
    }
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
    if (!SpeechRecognition) {
      setVoiceState('error')
      setResponse({ question: 'VOICE INPUT', text: 'Voice input isn’t available in this browser. You can still type or use a quick command below.' })
      return
    }
    try {
      const recognition = new SpeechRecognition()
      recognitionRef.current = recognition
      manualVoiceStop.current = false
      recognition.lang = 'en-US'
      recognition.interimResults = true
      recognition.continuous = false
      recognition.onstart = () => { if (recognitionRef.current === recognition && !manualVoiceStop.current) { setVoiceState('listening'); setInterim('') } }
      recognition.onresult = (event) => {
        if (recognitionRef.current !== recognition) return
        let finalText = ''
        let interimText = ''
        for (let i = event.resultIndex; i < event.results.length; i += 1) {
          if (event.results[i].isFinal) finalText += event.results[i][0].transcript
          else interimText += event.results[i][0].transcript
        }
        setInterim(interimText)
        if (finalText.trim()) {
          setInterim('')
          commandHandlerRef.current?.(finalText)
        }
      }
      recognition.onerror = (event) => {
        if (recognitionRef.current !== recognition || manualVoiceStop.current) return
        setVoiceState('error')
        setInterim('')
        const text = event.error === 'not-allowed' || event.error === 'service-not-allowed'
          ? 'Microphone access was blocked. Allow it in your browser settings, or type a command below.'
          : event.error === 'no-speech'
            ? 'I didn’t catch that. Tap the core to try again, or type a command.'
            : 'Voice input isn’t available right now. Try a typed or quick command instead.'
        setResponse({ question: 'VOICE INPUT', text })
      }
      recognition.onend = () => {
        if (recognitionRef.current !== recognition) return
        recognitionRef.current = null
        setVoiceState((state) => state === 'error' ? 'error' : 'idle')
        setInterim('')
        manualVoiceStop.current = false
      }
      setVoiceState('starting')
      recognition.start()
    } catch {
      recognitionRef.current = null
      setVoiceState('error')
      setResponse({ question: 'VOICE INPUT', text: 'Voice input couldn’t start. Try a typed or quick command instead.' })
    }
  }

  const displayTime = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit', hour12: !clock24 }).format(now)
  const day = new Intl.DateTimeFormat('en', { weekday: 'long' }).format(now)
  const date = new Intl.DateTimeFormat('en', { month: 'long', day: 'numeric', year: 'numeric' }).format(now)
  const voiceActive = voiceState === 'listening' || voiceState === 'starting'
  const stateTitle = voiceState === 'listening' ? 'Listening...' : voiceState === 'starting' ? 'Starting microphone...' : voiceState === 'error' ? 'Ready for a command' : 'Ready to listen'
  const stateHint = interim || (voiceState === 'listening' ? 'Go ahead, I’m listening.' : voiceState === 'starting' ? 'Waiting for microphone access' : voiceState === 'error' ? 'Type or try a quick command below' : 'Tap the core to start a conversation')

  return (
    <div className={`app ${motion ? '' : 'reduce-motion'} ${isOverlay ? `overlay overlay-${overlayPart}` : ''}`}>
      <div className="wallpaper" aria-hidden="true" />
      <div className="screen-vignette" aria-hidden="true" />

      <header className="topbar">
        <div className="brand" aria-label="Island for Windows">
          <BrandMark />
          <span className="brand-wordmark"><strong>ISLAND</strong><small>FOR WINDOWS</small></span>
        </div>
        <div className="topbar-right" ref={settingsRef}>
          <span className="system-status"><span /> YOUR SPACE IS READY</span>
          <button className={`icon-button settings-trigger ${settingsOpen ? 'selected' : ''}`} type="button" aria-label="Preferences" aria-expanded={settingsOpen} onClick={() => { setSettingsOpen(!settingsOpen); setExpanded(false) }}>
            <Settings2 size={17} strokeWidth={1.7} />
          </button>
          {settingsOpen && (
            <div className="settings-popover" role="dialog" aria-label="Preferences">
              <div className="popover-eyebrow">PREFERENCES <span>01 / 02</span></div>
              <h2>Make it yours.</h2>
              <p className="settings-intro">A little more you, a little less noise.</p>
              <div className="setting-row">
                <div><strong>Ambient motion</strong><small>Animate the core and sound waves</small></div>
                <Switch checked={motion} onChange={setMotion} label="Ambient motion" />
              </div>
              <div className="setting-row">
                <div><strong>24-hour clock</strong><small>Use 24-hour time on your desktop</small></div>
                <Switch checked={clock24} onChange={setClock24} label="24-hour clock" />
              </div>
              <div className="settings-footer"><Check size={13} /> Saved on this device</div>
            </div>
          )}
        </div>
      </header>

      <div className={`island-wrap ${expanded ? 'is-expanded' : ''}`} ref={islandRef}>
        <button type="button" className="island-compact" aria-expanded={expanded} aria-controls="music-panel" aria-label={`${expanded ? 'Collapse' : 'Expand'} music player. ${track.title}, ${battery.level}% battery${battery.sample ? ', sample' : ''}.`} onClick={() => { setExpanded(!expanded); setSettingsOpen(false) }}>
          <span className={`island-art artwork-${track.tone}`}><img src={asset('album-art.webp')} alt="" /><span><Music2 size={10} strokeWidth={2.3} /></span></span>
          <span className="island-track"><span className="island-track-label">MUSIC</span><strong>{track.title}</strong></span>
          <SoundWave active={playing} className="island-soundwave" />
          <span className="island-divider" />
          <span className="island-battery" title={battery.sample ? 'Battery unavailable — showing a sample' : `Battery ${battery.charging ? 'charging' : 'level'}`}><BatteryGlyph level={battery.level} charging={battery.charging} /><strong>{battery.level}%</strong></span>
          <ChevronDown className="island-chevron" size={14} strokeWidth={1.8} />
        </button>

        {expanded && (
          <div className="music-panel" id="music-panel">
            <div className="panel-heading"><span>YOUR SOUNDTRACK</span><span className="panel-heading-right"><span className="tiny-dot" /> {playing ? 'PLAYING' : 'PAUSED'}</span></div>
            <div className="player-track">
              <div className={`player-art artwork-${track.tone}`}><img src={asset('album-art.webp')} alt="" /></div>
              <div className="player-track-info"><span className="player-kicker">ISLAND SOUNDSCAPES</span><strong>{track.title}</strong><small>A moment to yourself</small></div>
              <span className="track-count">0{trackIndex + 1} / 03</span>
            </div>
            <div className="progress-area">
              <input aria-label="Track position" type="range" min="0" max={duration || 0} step="0.1" value={Math.min(currentTime, duration || 0)} onChange={(event) => { const value = Number(event.target.value); if (audioRef.current) audioRef.current.currentTime = value; setCurrentTime(value) }} style={{ '--progress': `${duration ? currentTime / duration * 100 : 0}%` }} />
              <div className="progress-labels"><span>{formatTime(currentTime)}</span><span>{formatTime(duration)}</span></div>
            </div>
            <div className="player-controls">
              <button type="button" className="player-small-control" aria-label={volume === 0 ? 'Unmute' : 'Mute'} onClick={toggleMute}>{volume === 0 ? <VolumeX size={17} /> : <Volume2 size={17} />}</button>
              <div className="transport-controls">
                <button type="button" className="player-control" aria-label="Previous track" onClick={() => skipTrack(-1)}><SkipBack size={18} fill="currentColor" strokeWidth={1.5} /></button>
                <button type="button" className="play-control" aria-label={playing ? 'Pause music' : 'Play music'} onClick={togglePlayback}>{playing ? <Pause size={19} fill="currentColor" /> : <Play size={19} fill="currentColor" className="play-icon" />}</button>
                <button type="button" className="player-control" aria-label="Next track" onClick={() => skipTrack(1)}><SkipForward size={18} fill="currentColor" strokeWidth={1.5} /></button>
              </div>
              <span className="player-output" title="Local soundscape"><Headphones size={16} /></span>
            </div>
            <div className="volume-row"><Volume2 size={14} /><input aria-label="Music volume" type="range" min="0" max="1" step="0.01" value={volume} onChange={(event) => setVolume(Number(event.target.value))} style={{ '--progress': `${volume * 100}%` }} /><span>{Math.round(volume * 100)}%</span></div>
            {audioError && <div className="audio-error" role="status">{audioError}</div>}
            <div className="player-bottom"><span><BatteryGlyph level={battery.level} charging={battery.charging} /> {battery.level}% {battery.sample ? 'SAMPLE BATTERY' : battery.charging ? 'CHARGING' : 'BATTERY'}</span><span>MADE FOR THIS MOMENT <span className="small-star">✦</span></span></div>
          </div>
        )}
      </div>

      {!remoteMusic && <audio ref={audioRef} src={track.src} preload="metadata" onPlay={() => setPlaying(true)} onPause={() => setPlaying(false)} onTimeUpdate={(event) => setCurrentTime(event.currentTarget.currentTime)} onLoadedMetadata={(event) => setDuration(event.currentTarget.duration)} onEnded={() => skipTrack(1, true)} onError={() => setAudioError('This soundscape could not be loaded.')} />}

      <main className="desktop-content">
        <section className="welcome" aria-labelledby="greeting">
          <div className="welcome-kicker"><span className="kicker-sparkle"><Sparkles size={13} /></span> YOUR SPACE, IN SYNC <span className="kicker-line" /></div>
          <h1 id="greeting">{greetingFor(now)}<span>.</span></h1>
          <p className="welcome-lead">A little more room to <em>just be.</em></p>
          <p className="welcome-copy">Music, a helping hand, and the little things —<br className="desktop-break" /> all together in one place.</p>
          <div className="welcome-actions">
            <button type="button" className="explore-button" onClick={() => { setExpanded(true); setSettingsOpen(false) }}><span><Music2 size={16} /></span> Explore the island <ArrowUpRight size={15} /></button>
            <button type="button" className="meet-button" onClick={() => inputRef.current?.focus()}>Meet JARVIS <ArrowRight size={15} /></button>
          </div>
          <div className="time-display">
            <div className="time-icon"><Clock3 size={19} strokeWidth={1.45} /></div>
            <div className="time-part"><strong>{displayTime}</strong><span>LOCAL TIME</span></div>
            <span className="time-divider" />
            <div className="date-part"><strong>{day}</strong><span>{date}</span></div>
          </div>
        </section>

        <section className="assistant" aria-label="JARVIS assistant" ref={assistantRef}>
          <div className={`core-card ${voiceActive ? 'core-active' : ''}`}>
            <div className="core-card-top"><span><Sparkles size={14} /> AI CORE</span><span className="core-head-actions"><span className="core-card-status"><i /> {voiceActive ? 'ACTIVE' : 'STANDBY'}</span>{overlayPart === 'assistant' && desktopBridge && <button type="button" className="overlay-hide" aria-label="Hide JARVIS" title="Hide JARVIS (show it again from the tray)" onClick={() => desktopBridge.hideAssistant()}><X size={13} /></button>}</span></div>
            <div className="core-stage">
              <button type="button" className="core-button" aria-label={voiceActive ? 'Stop listening' : 'Start listening'} aria-pressed={voiceActive} onClick={toggleListening}>
                <CoreVisual />
              </button>
            </div>
            <div className="core-title"><strong>JARVIS</strong><span>YOUR PERSONAL ASSISTANT</span></div>
            <div className="core-card-bottom"><span>VOICE INTERFACE</span><span>CORE 01 <span className="core-footer-star">✦</span></span></div>
          </div>
          <div className="assistant-status" aria-live="polite">
            <span className={`status-orb ${voiceActive ? 'status-orb-active' : ''}`}><i /></span>
            <div><strong>{stateTitle}</strong><span>{stateHint}</span></div>
            <button type="button" className="mic-button" aria-label={voiceActive ? 'Stop listening' : 'Start listening'} title={voiceActive ? 'Stop listening' : 'Start listening'} onClick={toggleListening}>{voiceActive ? <MicOff size={17} /> : <Mic size={17} />}</button>
          </div>
          <form className="command-form" onSubmit={(event) => { event.preventDefault(); runCommand(command) }}>
            <Sparkles size={15} className="command-sparkle" />
            <input ref={inputRef} value={command} onChange={(event) => setCommand(event.target.value)} aria-label="Ask JARVIS" placeholder="Ask JARVIS something..." />
            <button type="submit" aria-label="Send command" disabled={!command.trim()}><CornerDownLeft size={16} /></button>
          </form>
          {response ? (
            <div className="assistant-response" role="status">
              <div><span>JARVIS <i> / </i> {response.question === 'VOICE INPUT' ? 'VOICE INPUT' : 'RESPONSE'}</span><button type="button" onClick={() => setResponse(null)} aria-label="Dismiss response"><X size={13} /></button></div>
              <p>{response.text}</p>
            </div>
          ) : (
            <div className="suggestions"><span>TRY ASKING</span><button type="button" onClick={() => runCommand(playing ? 'Pause music' : 'Play music')}><Music2 size={12} /> {playing ? 'Pause music' : 'Play music'}</button><button type="button" onClick={() => runCommand('What time is it?')}><Clock3 size={12} /> What’s the time?</button></div>
          )}
        </section>
      </main>

      <footer className="desktop-footer">
        <div><span className="footer-sparkle">✦</span> A LITTLE MORE IN THE MOMENT</div>
        <div className="footer-tip"><ArrowDownLeft size={13} /> CLICK THE ISLAND TO EXPLORE</div>
        <div className="footer-version"><span /> ISLAND FOR WINDOWS <i>/</i> 01</div>
      </footer>
    </div>
  )
}
