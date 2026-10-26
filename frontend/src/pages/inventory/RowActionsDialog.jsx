import { Archive, History, PackagePlus, Pencil, Truck } from 'lucide-react'
import Modal from '../../components/Modal'

/** The ⋮ menu of a row: a list of things to do with this item. onPick gets the name of the one tapped. */
export default function RowActionsDialog({ item, onPick, onClose }) {
  const actions = [
    ['stock', PackagePlus, 'Add stock', 'text-brand'],
    ['edit', Pencil, 'Edit item', ''],
    ['history', History, 'Stock history', ''],
    ...(item.onOrder ? [['receive', Truck, 'Mark order received', 'text-[#2563c9]']] : []),
    ['archive', Archive, 'Archive item', 'text-red-600'],
  ]

  return (
    <Modal title={item.name} onClose={onClose}>
      <ul className="divide-y divide-edge">
        {actions.map(([name, Icon, label, colour]) => (
          <li key={name}>
            <button onClick={() => onPick(name)} className={`flex w-full items-center gap-3 px-5 py-4 text-left text-[15px] font-medium hover:bg-stone-50 ${colour}`}>
              <Icon size={20} /> {label}
            </button>
          </li>
        ))}
      </ul>
    </Modal>
  )
}
