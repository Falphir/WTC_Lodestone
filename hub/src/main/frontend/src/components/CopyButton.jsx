import { useState } from 'react'
import { Icon } from './Icon'

export function CopyButton({ text, label = 'Copy' }) {
  const [copied, setCopied] = useState(false)
  async function copy() {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // clipboard blocked (e.g. plain http on another host): the text is selectable anyway
    }
  }
  return (
    <button type="button" className="button button-quiet" onClick={copy}>
      <Icon name={copied ? 'check' : 'copy'} />
      {copied ? 'Copied' : label}
    </button>
  )
}
