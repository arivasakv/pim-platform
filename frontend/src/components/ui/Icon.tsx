import type { ReactNode } from 'react'

export type IconName =
  | 'menu'
  | 'close'
  | 'home'
  | 'components'
  | 'search'
  | 'info'
  | 'warning'
  | 'error'
  | 'success'
  | 'chevron-right'
  | 'chevron-down'
  | 'folder'
  | 'folder-open'
  | 'tag'
  | 'list'
  | 'tags'
  | 'folder-tree'
  | 'network'

interface IconProps {
  name: IconName
  size?: 16 | 20 | 24
  /** Accessible name. Leave empty for decorative icons next to visible text. */
  label?: string
}

// Simple outline drawings on a 24×24 grid (research R-05).
const ICON_PATHS: Record<IconName, ReactNode> = {
  menu: <path d="M4 6h16M4 12h16M4 18h16" />,
  close: <path d="M6 6l12 12M18 6L6 18" />,
  home: <path d="M3 11l9-7 9 7M5 10v10h14V10M10 20v-5h4v5" />,
  components: (
    <>
      <rect x="4" y="4" width="6" height="6" rx="1" />
      <rect x="14" y="4" width="6" height="6" rx="1" />
      <rect x="4" y="14" width="6" height="6" rx="1" />
      <rect x="14" y="14" width="6" height="6" rx="1" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="6" />
      <path d="M20 20l-4.5-4.5" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5M12 8h.01" />
    </>
  ),
  warning: <path d="M12 4L2.5 20h19L12 4zM12 10v4M12 17h.01" />,
  error: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M9 9l6 6M15 9l-6 6" />
    </>
  ),
  success: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M8 12.5l2.5 2.5L16 9.5" />
    </>
  ),
  // Tree icons (feature 010, "Taxonomía").
  'chevron-right': <path d="M9 6l6 6-6 6" />,
  'chevron-down': <path d="M6 9l6 6 6-6" />,
  folder: <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z" />,
  'folder-open': (
    <path d="M3 7a2 2 0 0 1 2-2h4l2 2h6a2 2 0 0 1 2 2v1M3 7v10a2 2 0 0 0 2 2h12.5a2 2 0 0 0 1.9-1.4l1.9-5.6A1 1 0 0 0 20.3 11H8.2a2 2 0 0 0-1.9 1.4L3 19" />
  ),
  tag: (
    <>
      <path d="M3 12V4a1 1 0 0 1 1-1h8l9 9-9 9-9-9z" />
      <path d="M7.5 7.5h.01" />
    </>
  ),
  // Side navigation icons for the catalog administration pages.
  list: <path d="M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01" />,
  tags: (
    <>
      <path d="M2 11V4a1 1 0 0 1 1-1h7l8.5 8.5-7.5 7.5L2 11z" />
      <path d="M14 3l8.5 8.5-7.5 7.5" />
      <path d="M6.5 7h.01" />
    </>
  ),
  'folder-tree': (
    <>
      <path d="M3 4a1 1 0 0 1 1-1h3l1.5 1.5H13a1 1 0 0 1 1 1V8a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4z" />
      <path d="M6 9v10h6M6 13h6" />
      <rect x="12" y="11" width="9" height="4" rx="1" />
      <rect x="12" y="17" width="9" height="4" rx="1" />
    </>
  ),
  network: (
    <>
      <rect x="9" y="3" width="6" height="5" rx="1" />
      <rect x="3" y="16" width="6" height="5" rx="1" />
      <rect x="15" y="16" width="6" height="5" rx="1" />
      <path d="M12 8v4M6 16v-4h12v4" />
    </>
  ),
}

export function Icon({ name, size = 20, label }: IconProps) {
  const accessibility = label
    ? { role: 'img', 'aria-label': label }
    : { 'aria-hidden': true as const, focusable: false as const }

  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      {...accessibility}
    >
      {ICON_PATHS[name]}
    </svg>
  )
}
