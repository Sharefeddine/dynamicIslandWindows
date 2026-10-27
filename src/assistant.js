// The companion intentionally understands a small, explicit set of local
// commands. It does not send arbitrary text to a language-model service.
export function commandIntent(raw) {
  const text = raw.trim().toLowerCase()
  const mentionsSound = /\b(music|song|track|sound|audio|something|one|playing)\b/.test(text)
  const standalone = (verbs) => new RegExp(`^(?:${verbs})(?:\\s+please)?[.!?]*$`).test(text)

  if (/\b(pause|stop)\b/.test(text) && (mentionsSound || standalone('pause|stop'))) return 'pause'
  if (/\b(next|skip)\b/.test(text) && (mentionsSound || standalone('next|skip'))) return 'next'
  if (/\b(previous|last|back)\b/.test(text) && (mentionsSound || standalone('previous|back'))) return 'previous'
  if (/\b(play|start|resume)\b/.test(text) && (mentionsSound || standalone('play|resume'))) return 'play'
  if (/\b(time|clock)\b/.test(text)) return 'time'
  if (/\b(date|day|today)\b/.test(text)) return 'date'
  if (/\b(battery|charge|power)\b/.test(text)) return 'battery'
  if (/\b(hello|hey|hi)\b/.test(text)) return 'greeting'
  if (/\b(help|what can you do|commands)\b/.test(text)) return 'help'
  return 'unknown'
}

export function formatTime(seconds) {
  if (!Number.isFinite(seconds)) return '0:00'
  const whole = Math.max(0, Math.floor(seconds))
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, '0')}`
}

export function greetingFor(date) {
  const hour = date.getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}
