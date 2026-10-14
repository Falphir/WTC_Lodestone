/** What players are shown in Discord's /serverinfo. Whether they see it at all is the badge up top. */
export function ServerListing({ listing }) {
  const { publicAddress, modpack, modpackUrl, modpackVersion, launcher } = listing
  return (
    <dl className="setup">
      <div>
        <dt>Address</dt>
        <dd className="mono">{publicAddress || 'Not set'}</dd>
      </div>
      <div>
        <dt>Modpack</dt>
        <dd>
          {modpack || 'Not set'}
          {modpackVersion && <span className="muted mono"> {modpackVersion}</span>}
        </dd>
      </div>
      <div>
        <dt>Modpack link</dt>
        <dd>
          {modpackUrl ? (
            <a href={modpackUrl} title={modpackUrl} className="ellipsis" target="_blank" rel="noreferrer noopener">
              {modpackUrl}
            </a>
          ) : (
            'Not set'
          )}
        </dd>
      </div>
      <div>
        <dt>Launcher</dt>
        <dd>{launcher || 'Not set'}</dd>
      </div>
    </dl>
  )
}
