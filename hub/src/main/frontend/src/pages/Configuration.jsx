import { MotdPanel, PageHead } from '../components'

/** Settings that apply across the network rather than to one server. MOTDs are the first of them. */
export default function Configuration() {
  return (
    <div className="page">
      <PageHead>
        <h1>Configuration</h1>
        <p className="muted">Network-wide settings that every server can use.</p>
      </PageHead>
      <MotdPanel />
    </div>
  )
}
