import { useState } from 'react'
import { Icon } from './Icon'

/** navigator.clipboard only exists in a secure context, so plain-HTTP dashboards need the execCommand fallback. */
async function copyText(text) {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // permission denied or similar: try the old way
    }
  }
  const area = document.createElement('textarea')
  area.value = text
  area.readOnly = true
  area.style.position = 'fixed'
  area.style.opacity = '0'
  document.body.appendChild(area)
  try {
    area.select()
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    area.remove()
  }
}

export function CopyButton({ text, label = 'Copy' }) {
  const [state, setState] = useState(null) // null | 'copied' | 'failed'
  async function copy() {
    const ok = await copyText(text)
    setState(ok ? 'copied' : 'failed')
    setTimeout(() => setState(null), ok ? 2000 : 4000)
  }
  return (
    <button type="button" className="button button-quiet" onClick={copy}>
      <Icon name={state === 'copied' ? 'check' : 'copy'} />
      {state === 'copied' ? 'Copied' : state === 'failed' ? "Couldn't copy" : label}
    </button>
  )
}
