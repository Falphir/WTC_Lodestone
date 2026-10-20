import { Panel } from '../ui/Panel'
import { WhitelistRow } from './WhitelistRow'

/** The whitelisted players, one row each, with a Remove button per player. */
export function WhitelistTable({ players, busy, onRemove }) {
  return (
    <Panel>
      <table className="table table-cards">
        <thead>
          <tr>
            <th>Player</th>
            <th>Discord</th>
            <th>Added by</th>
            <th>Added</th>
            <th>
              <span className="visually-hidden">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {players.map((p) => (
            <WhitelistRow key={p.uuid} player={p} busy={busy} onRemove={onRemove} />
          ))}
        </tbody>
      </table>
    </Panel>
  )
}
