import { Box, ChartNoAxesColumnIncreasing, CircleHelp, House, Settings, Users } from 'lucide-react'

// The pages in the menu. The sidebar (laptop) and the bottom bar (phone) both read this list,
// so a new page only has to be added here. `short` is the small name used on the phone bar.
export const PAGES = [
  { to: '/', icon: House, title: 'Home', subtitle: null, short: 'Home' },
  { to: '/inventory', icon: Box, title: 'Inventory', subtitle: 'Management', short: 'Stock' },
  { to: '/advisor', icon: ChartNoAxesColumnIncreasing, title: 'Business Advisor', subtitle: '(Profit & Loss)', short: 'Profit' },
  { to: '/customers', icon: Users, title: 'Customer Handling', subtitle: '(Udhaar Management)', short: 'Udhaar' },
]
export const SETTINGS = { to: '/settings', icon: Settings, title: 'Settings', short: 'Settings' }
export const HELP = { to: '/help', icon: CircleHelp, title: 'Help', short: 'Help' }
