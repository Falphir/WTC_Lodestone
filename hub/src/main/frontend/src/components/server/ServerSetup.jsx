import { Badge } from '../ui/Badge'
import { whitelistSync } from '../../format'

/** What the mod reported about itself on check-in. */
export function ServerSetup({ server }) {
  const sync = whitelistSync(server)
  const { version, minecraftVersion, loaderVersion } = server.mod
  return (
    <dl className="setup">
      <div>
        <dt>Mod</dt>
        <dd className="mono">{version ?? 'Not reported'}</dd>
      </div>
      <div>
        <dt>Minecraft</dt>
        <dd className="mono">{minecraftVersion ?? 'Not reported'}</dd>
      </div>
      <div>
        <dt>NeoForge</dt>
        <dd className="mono">{loaderVersion ?? 'Not reported'}</dd>
      </div>
      <div>
        <dt>Whitelist</dt>
        <dd>
          <Badge tone={sync.tone}>{sync.label}</Badge>
          <small className="muted">{sync.detail}</small>
        </dd>
      </div>
    </dl>
  )
}
