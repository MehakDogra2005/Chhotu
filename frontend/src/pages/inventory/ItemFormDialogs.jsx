import { useState } from 'react'
import { PackagePlus, Pencil } from 'lucide-react'
import { post, put } from '../../api'
import { useAuth } from '../../AuthContext'
import useFetch from '../../useFetch'
import Modal from '../../components/Modal'
import useSubmit from '../../components/useSubmit'
import { Field, inputStyle, toPaise } from '../../components/ui'
import { UNITS } from '../../categories'

const EMPTY = { name: '', category: '', unit: 'piece', sellingPrice: '', purchasePrice: '', reorderLevel: '', openingStock: '' }

/** The form shared by Add Item and Edit Item. `save` gets the form in the shape the backend wants (prices in paise). */
function ItemForm({ initial, editing, categories, save, onClose }) {
  const [form, setForm] = useState(initial)
  const { busy, error, run } = useSubmit(onClose)
  const text = (field) => ({ value: form[field], onChange: (e) => setForm({ ...form, [field]: e.target.value }), className: inputStyle })

  const submit = run(() => save({
    name: form.name,
    category: form.category,
    unit: form.unit,
    sellingPricePaise: toPaise(form.sellingPrice),
    purchasePricePaise: toPaise(form.purchasePrice),
    reorderLevel: Number(form.reorderLevel || 0),
    openingStock: Number(form.openingStock || 0),
  }))

  return (
    <form onSubmit={submit} className="space-y-4 overflow-y-auto p-5">
      <Field label="Item name"><input {...text('name')} placeholder="e.g. Parle-G Biscuit" autoFocus /></Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Category">
          <input {...text('category')} list="categories" placeholder="e.g. Snacks" />
          <datalist id="categories">{categories.map((c) => <option key={c} value={c} />)}</datalist>
        </Field>
        <Field label="Counted in">
          <select {...text('unit')} disabled={editing}>{UNITS.map((u) => <option key={u}>{u}</option>)}</select>
        </Field>
        <Field label="Selling price (₹)"><input {...text('sellingPrice')} type="number" min="0" step="any" inputMode="decimal" placeholder="what customers pay" /></Field>
        <Field label="Buying price (₹)"><input {...text('purchasePrice')} type="number" min="0" step="any" inputMode="decimal" placeholder="what you pay" /></Field>
        <Field label="Reorder level"><input {...text('reorderLevel')} type="number" min="0" step="any" inputMode="decimal" placeholder="warn me below this" /></Field>
        {/* After adding, stock changes through "Add stock" so the history stays correct */}
        {!editing && <Field label="Stock you have now"><input {...text('openingStock')} type="number" min="0" step="any" inputMode="decimal" placeholder="0" /></Field>}
      </div>
      {editing && <p className="text-[12.5px] text-muted">The unit and the stock cannot be changed here. Use "Add stock" to change the stock.</p>}
      {error && <p className="text-[13px] text-red-600">{error}</p>}
      <button disabled={busy} className="h-12 w-full rounded-xl bg-brand text-[15px] font-semibold text-white hover:bg-brand-dark disabled:opacity-60">
        {busy ? 'Saving...' : editing ? 'Save changes' : 'Add item'}
      </button>
    </form>
  )
}

export function AddItemDialog({ categories, onClose }) {
  const { token } = useAuth()
  return (
    <Modal title="Add Item" icon={<PackagePlus size={22} className="text-brand" />} onClose={onClose}>
      <ItemForm initial={EMPTY} categories={categories} onClose={onClose} save={(item) => post('/api/products', item, token)} />
    </Modal>
  )
}

export function EditItemDialog({ item, categories, onClose }) {
  const { token } = useAuth()
  const { data, error } = useFetch(`/api/products/${item.productId}`)

  return (
    <Modal title={`Edit ${item.name}`} icon={<Pencil size={22} className="text-brand" />} onClose={onClose}>
      {error && <p className="p-5 text-[13px] text-red-600">{error}</p>}
      {!data && !error && <p className="p-5 text-[13px] text-muted">Loading...</p>}
      {data && (
        <ItemForm editing categories={categories} onClose={onClose}
          initial={{ name: data.name, category: data.category, unit: data.unit, sellingPrice: String(data.sellingPricePaise / 100),
            purchasePrice: String(data.purchasePricePaise / 100), reorderLevel: String(data.reorderLevel), openingStock: '' }}
          save={(changes) => put(`/api/products/${item.productId}`, changes, token)} />
      )}
    </Modal>
  )
}
