import { Link } from 'react-router-dom'
import { ArrowRight, Box, Mic } from 'lucide-react'
import { Card, CardHeader, Empty, StatusPill, number } from '../../components/ui'
import { useAssistant } from '../../components/AssistantContext'

export default function InventoryCard({ items }) {
  const { ask } = useAssistant()
  return (
    <Card className="flex flex-col p-5">
      <CardHeader icon={<Box size={22} />} tint="bg-[#fdebd0] text-orange" title="Inventory Management" to="/inventory"
        subtitle="Track stock, update inventory and get smart alerts." />

      {items.length === 0 ? <Empty>Add your products to see stock here.</Empty> : (
        <div className="mt-5 overflow-x-auto">
          <table className="w-full text-left text-[13px]">
            <thead>
              <tr className="border-b border-edge text-[12.5px] font-semibold">
                <th className="pb-2">Item</th><th className="pb-2">Stock</th><th className="pb-2">Reorder Level</th><th className="pb-2">Status</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.name} className="border-b border-edge/60 last:border-0">
                  <td className="py-3"><Link to={`/inventory?search=${encodeURIComponent(item.name)}`} className="hover:underline">{item.name}</Link></td>
                  <td>{number(item.stock)}</td>
                  <td>{number(item.reorderLevel)}</td>
                  <td><StatusPill status={item.status} /></td>
                </tr>
              ))}
            </tbody>
            </table>
        </div>
      )}

      <button onClick={() => ask({ listen: true, hint: `Say or type, for example: "10 ${items[0]?.name || 'Chocolate'} add karo"` })} className="mt-auto flex h-13 w-full items-center gap-3 rounded-xl bg-brand px-5 text-[15px] font-semibold text-white">
        <Mic size={20} /> <span className="flex-1 text-center">Update Stock by Voice</span> <ArrowRight size={18} />
      </button>
    </Card>
  )
}
