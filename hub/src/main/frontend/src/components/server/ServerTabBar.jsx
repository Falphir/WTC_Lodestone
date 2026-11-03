import { Icon } from '../ui/Icon'

/**
 * The server page's sections, pinned to the bottom of the screen like PufferPanel's. Plain links,
 * so each tab has its own address (#/servers/{id}/{tab}) and the browser's back button walks them.
 */
export function ServerTabBar({ serverId, tabs, active }) {
  return (
    <nav className="tab-bar" aria-label="Server sections">
      {tabs.map((tab) => (
        <a
          key={tab.id}
          href={`#/servers/${encodeURIComponent(serverId)}/${tab.id}`}
          aria-current={tab.id === active ? 'page' : undefined}
        >
          <Icon name={tab.icon} />
          {tab.label}
        </a>
      ))}
    </nav>
  )
}
