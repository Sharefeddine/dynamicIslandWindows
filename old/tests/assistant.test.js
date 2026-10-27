import test from 'node:test'
import assert from 'node:assert/strict'
import { commandIntent, formatTime, greetingFor } from '../src/assistant.js'

test('recognizes supported music commands without interpreting unrelated requests', () => {
  const examples = [
    ['Play music', 'play'], ['resume please', 'play'], ['pause the music', 'pause'],
    ['stop', 'pause'], ['play the next song', 'next'], ['skip', 'next'],
    ['previous track', 'previous'], ['back', 'previous'], ['stop listening', 'unknown'],
  ]
  for (const [input, intent] of examples) assert.equal(commandIntent(input), intent, input)
})

test('recognizes local information and assistance commands', () => {
  assert.equal(commandIntent('What time is it?'), 'time')
  assert.equal(commandIntent('What day is it today?'), 'date')
  assert.equal(commandIntent('Battery level'), 'battery')
  assert.equal(commandIntent('Hello!'), 'greeting')
  assert.equal(commandIntent('What can you do?'), 'help')
  assert.equal(commandIntent('Tell me a joke'), 'unknown')
})

test('formats playback time safely', () => {
  assert.equal(formatTime(NaN), '0:00')
  assert.equal(formatTime(-1), '0:00')
  assert.equal(formatTime(9.8), '0:09')
  assert.equal(formatTime(123), '2:03')
})

test('greeting follows the local hour', () => {
  assert.equal(greetingFor(new Date(2026, 0, 1, 8)), 'Good morning')
  assert.equal(greetingFor(new Date(2026, 0, 1, 13)), 'Good afternoon')
  assert.equal(greetingFor(new Date(2026, 0, 1, 20)), 'Good evening')
})
