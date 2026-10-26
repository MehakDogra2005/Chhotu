import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Box, ChartNoAxesCombined, Truck, TriangleAlert } from 'lucide-react'
import { del, post } from '../../api'
import { useAuth } from '../../AuthContext'
import useFetch from '../../useFetch'
import PageHeader from '../../components/PageHeader'
import ActionNeeded from '../../components/ActionNeeded'
import ConfirmDialog from '../../components/ConfirmDialog'
import { useAssistant } from '../../components/AssistantContext'
import { StatCard, rupees } from '../../components/ui'
import ItemsTable from './ItemsTable'
import { QuickActions, TalkToSaathi } from './SideCards'
import RowActionsDialog from './RowActionsDialog'
import { AddItemDialog, EditItemDialog } from './ItemFormDialogs'
import { AddStockDialog, HistoryDialog } from './StockDialogs'
import RestockDialog from './RestockDialog'
import ReportDialog from './ReportDialog'

export default function Inventory() {
  const { token } = useAuth()
  const { ask } = useAssistant()

  // The tab and the search text live in the address (/inventory?filter=low&search=bulb), so a link can open it filtered.
  const [params, setParams] = useSearchParams()
  const filter = params.get('filter') || 'all' // all | low | out | order
  const search = params.get('search') || ''
  const change = (next) => {
    const now = { filter, search, ...next }
    setParams({ ...(now.filter !== 'all' && { filter: now.filter }), ...(now.search && { search: now.search }) }, { replace: true })
  }

  // Which dialog is open: { kind: 'add' | 'edit' | 'actions' | 'stock' | 'history' | 'archive' | 'receive' | 'restock' | 'report', item, ids }
  const [dialog, setDialog] = useState(null)
  const [selected, setSelected] = useState([]) // ids of the rows that are ticked
  const open = (kind, extra) => setDialog({ kind, ...extra })
  const close = () => setDialog(null)

  const { data, error } = useFetch(`/api/inventory?filter=${filter}&search=${encodeURIComponent(search)}`)

  if (error) return <p className="p-10 text-center text-red-600">{error}</p>
  if (!data) return <p className="p-10 text-center text-muted">Loading your stock...</p>

  const { counts } = data
  const needRestock = counts.low + counts.out
  const categories = [...new Set(data.items.map((i) => i.category))] // suggestions when typing a category
  const item = dialog?.item
  const ticked = selected.filter((id) => data.items.some((i) => i.productId === id)) // ignore rows that are gone

  return (
    <>
      <PageHeader icon={<Box size={48} strokeWidth={1.4} className="fill-[#fbe3b4] text-[#e8a33a]" />}
        title="Inventory Management" subtitle="Track your stock, update inventory and get smart recommendations." />

      <div className="mt-5 grid grid-cols-1 gap-4 xl:grid-cols-[1fr_330px]">
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3 2xl:grid-cols-4">
            <StatCard to="/inventory" icon={<Box size={24} />} tint="bg-[#e1f5e8] text-brand" value={counts.all} label="Total Items"
              footer={<div className="text-[13px] text-brand">In your catalogue</div>} />
            <StatCard to="/inventory?filter=low" icon={<TriangleAlert size={24} />} tint="bg-[#fde6e4] text-red-500" value={needRestock} label="Low Stock Items"
              footer={needRestock > 0 ? <div className="text-[13px] text-red-500">Need restock</div> : <div className="text-[13px] text-brand">All stocked up</div>} />
            <StatCard to="/inventory?filter=order" icon={<Truck size={24} />} tint="bg-[#e3ecfb] text-[#2563c9]" value={counts.onOrder} label="Items on Order"
              footer={<div className="text-[13px] text-[#2563c9]">Pending delivery</div>} />
            <StatCard onClick={() => open('report')} icon={<ChartNoAxesCombined size={24} />} tint="bg-[#ece6fb] text-[#6d4bc8]" value={rupees(counts.valuePaise)} label="Inventory Value"
              footer={<div className="text-[13px] text-muted">at purchase price</div>} />
          </div>

          <ItemsTable data={data} filter={filter} setFilter={(value) => change({ filter: value })} search={search} setSearch={(value) => change({ search: value })}
            selected={ticked} setSelected={setSelected} onAction={(kind, row) => open(kind, { item: row })}
            onAdd={() => open('add')} onRestockSelected={() => open('restock', { ids: ticked })} />
        </div>

        <div className="space-y-4">
          <TalkToSaathi names={data.alerts.map((a) => a.name)} />
          <ActionNeeded title="Low Stock Alert" tone="red" viewAll items={data.alerts} total={needRestock} />
          <QuickActions onVoice={() => ask({ listen: true, hint: `Say or type, for example: "10 ${data.alerts[0]?.name || 'Chocolate'} add karo"` })}
            onRestock={() => open('restock')} onAdd={() => open('add')} onReport={() => open('report')} />
        </div>
      </div>

      {/* Only one dialog is open at a time */}
      {dialog?.kind === 'add' && <AddItemDialog categories={categories} onClose={close} />}
      {dialog?.kind === 'edit' && <EditItemDialog item={item} categories={categories} onClose={close} />}
      {dialog?.kind === 'stock' && <AddStockDialog item={item} onClose={close} />}
      {dialog?.kind === 'history' && <HistoryDialog item={item} onClose={close} />}
      {dialog?.kind === 'restock' && <RestockDialog ids={dialog.ids} onClose={close} />}
      {dialog?.kind === 'report' && <ReportDialog onClose={close} />}
      {dialog?.kind === 'actions' && <RowActionsDialog item={item} onPick={(kind) => open(kind, { item })} onClose={close} />}
      {dialog?.kind === 'archive' && (
        <ConfirmDialog title="Archive item" danger confirmLabel="Archive" onClose={close}
          message={`Archive ${item.name}? It will disappear from your lists. Its history is kept.`}
          onConfirm={() => del(`/api/products/${item.productId}`, token)} />
      )}
      {dialog?.kind === 'receive' && (
        <ConfirmDialog title="Order received" confirmLabel="Yes, it arrived" onClose={close}
          message={`Has the order for ${item.name} arrived? The ordered amounts will be added to your stock.`}
          onConfirm={() => post(`/api/restock/receive/${item.productId}`, {}, token)} />
      )}
    </>
  )
}
