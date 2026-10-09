import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import {
  faBars,
  faCheck,
  faChevronLeft,
  faCopy,
  faDesktop,
  faGauge,
  faKey,
  faMagnifyingGlass,
  faMoon,
  faPen,
  faPlus,
  faRightFromBracket,
  faServer,
  faShieldHalved,
  faSun,
  faTrashCan,
  faUpload,
  faUserCheck,
  faXmark,
} from '@fortawesome/free-solid-svg-icons'

const ICONS = {
  overview: faGauge,
  servers: faServer,
  whitelist: faUserCheck,
  admins: faShieldHalved,
  signOut: faRightFromBracket,
  sun: faSun,
  moon: faMoon,
  system: faDesktop,
  copy: faCopy,
  check: faCheck,
  plus: faPlus,
  back: faChevronLeft,
  search: faMagnifyingGlass,
  upload: faUpload,
  menu: faBars,
  close: faXmark,
  edit: faPen,
  key: faKey,
  trash: faTrashCan,
}

export function Icon({ name, size = 16 }) {
  return <FontAwesomeIcon icon={ICONS[name]} className="icon" aria-hidden="true" style={{ width: size, height: size }} />
}
