import { useState } from 'react'

// Same two-tier render the Discord bot uses for skin heads (see discord/src/lib/embeds.js):
// Visage's 3D render first, falling back to mc-heads' flat icon if it fails to load. The bot sets
// a custom User-Agent (Visage rejects browser-spoofed ones); a browser can't do that for an <img>,
// so this leans on the image's own error event to swap sources instead.
const visageHeadUrl = (id) => `https://visage.surgeplay.com/head/${encodeURIComponent(id)}`
const mcHeadsUrl = (id) => `https://mc-heads.net/head/${encodeURIComponent(id)}`

/** A whitelisted player's skin head. `uuid` also works as `id` -- both services accept either. */
export function PlayerHead({ id, size = 60 }) {
  const [src, setSrc] = useState(visageHeadUrl(id))
  return (
    <img
      className="player-head"
      src={src}
      onError={() => setSrc((current) => (current === visageHeadUrl(id) ? mcHeadsUrl(id) : current))}
      width={size}
      height={size}
      alt=""
      loading="lazy"
    />
  )
}
