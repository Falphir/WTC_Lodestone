import { Panel } from '../ui/Panel'
import { BanRow } from './BanRow'

/** The network bans, one row each, with an Unban button per player. */
export function BanTable({ bans, busy, onUnban }) {
  return (
    <Panel>
      <table className="table table-cards">
        <thead>
          <tr>
            <th>Player</th>
            <th>Reason</th>
            <th>Banned by</th>
            <th>Banned</th>
            <th>Expires</th>
            <th>
              <span className="visually-hidden">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {bans.map((b) => (
            <BanRow key={b.uuid} ban={b} busy={busy} onUnban={onUnban} />
          ))}
        </tbody>
      </table>
    </Panel>
  )
}
