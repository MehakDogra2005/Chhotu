import { useEffect, useRef, useState } from 'react'
import { rupees } from '../../components/ui'

// Chart colours, always used in this order so a series keeps its colour. Checked for colour-blind readers:
// do not reorder or add hues. More slices than this fold into "Other".
export const COLORS = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#6250d6', '#e34948']
const OTHER = '#a3a19a'
const SURFACE = '#fffdf8'
const GRID = '#ece6d8'

export const SERIES = {
  sales: { label: 'Sales', color: COLORS[0] },
  cost: { label: 'Purchase Cost', color: COLORS[1] },
  profit: { label: 'Profit', color: COLORS[2] },
}

/** Short money for axis labels: 1,20,000 -> 1.2L, 15,000 -> 15K. */
const axisMoney = (r) => {
  const a = Math.abs(r)
  const s = a >= 100000 ? `${+(a / 100000).toFixed(1)}L` : a >= 1000 ? `${+(a / 1000).toFixed(1)}K` : `${a}`
  return (r < 0 ? '-' : '') + s
}

/** A step of 1, 2 or 5 x 10^n that splits the range into about 4 parts, and the axis ends rounded to it. */
function niceScale(min, max) {
  const raw = (max - min) / 4 || 1
  const power = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 5, 10].map((m) => m * power).find((s) => s >= raw)
  return { min: Math.floor(min / step) * step, max: Math.ceil(max / step) * step, step }
}

/** Width of an element, kept up to date, so the SVG can be drawn at real pixel size (text stays readable on a phone). */
function useWidth() {
  const ref = useRef(null)
  const [width, setWidth] = useState(0)
  useEffect(() => {
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width))
    observer.observe(ref.current)
    return () => observer.disconnect()
  }, [])
  return [ref, width]
}

/** A bar whose top corners are rounded and whose bottom sits flat on the baseline. */
function barPath(x, top, w, base) {
  const r = Math.min(4, w / 2, base - top)
  return `M${x},${base} V${top + r} Q${x},${top} ${x + r},${top} H${x + w - r} Q${x + w},${top} ${x + w},${top + r} V${base} Z`
}

export function Legend({ items }) {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[12px] text-muted">
      {items.map((s) => (
        <span key={s.label} className="flex items-center gap-1.5">
          <span className={s.line ? 'h-0.5 w-3.5 rounded' : 'h-2.5 w-2.5 rounded-sm'} style={{ background: s.color }} />{s.label}
        </span>
      ))}
    </div>
  )
}

/**
 * Sales and purchase cost as bars, profit as a line, one bucket (day / week / month) per group. All three are
 * rupees, so they share one axis. Hovering a group shows its numbers.
 */
export function TrendChart({ trend, label }) {
  const [ref, width] = useWidth()
  const [hover, setHover] = useState(null)
  const H = 230, pad = { l: 44, r: 8, t: 10, b: 26 }

  const values = trend.flatMap((b) => [b.salesPaise, b.costPaise, b.profitPaise]).map((p) => p / 100)
  const empty = values.every((v) => v === 0)
  const scale = niceScale(Math.min(0, ...values), Math.max(0, ...values))
  const n = trend.length
  const plotW = Math.max(width - pad.l - pad.r, 1)
  const groupW = plotW / n
  const barW = Math.max(1.5, Math.min(14, (groupW - 6) / 2 - 1))
  const y = (rupee) => pad.t + ((scale.max - rupee) / (scale.max - scale.min)) * (H - pad.t - pad.b)
  const cx = (i) => pad.l + groupW * (i + 0.5)
  const base = y(0)
  const ticks = []
  for (let v = scale.min; v <= scale.max + 1e-9; v += scale.step) ticks.push(v)
  const every = Math.ceil(n / 6) // about six date labels, whatever the period
  const profitPoints = trend.map((b, i) => `${cx(i)},${y(b.profitPaise / 100)}`).join(' ')

  return (
    <div ref={ref} className="relative" onMouseLeave={() => setHover(null)}>
      {empty ? (
        <p className="grid h-[230px] place-items-center text-[13px] text-muted">No sales in this period yet.</p>
      ) : width > 0 && (
        <svg width={width} height={H} role="img" aria-label="Sales, purchase cost and profit over the period">
          {ticks.map((v) => (
            <g key={v}>
              <line x1={pad.l} x2={width - pad.r} y1={y(v)} y2={y(v)} stroke={v === 0 ? '#cfc6b3' : GRID} />
              <text x={pad.l - 8} y={y(v) + 4} textAnchor="end" fontSize="11" fill="#6b6f75">{axisMoney(v)}</text>
            </g>
          ))}
          {hover !== null && <rect x={cx(hover) - groupW / 2} y={pad.t} width={groupW} height={H - pad.t - pad.b} fill="#000" opacity="0.04" />}
          {trend.map((b, i) => (
            <g key={b.from}>
              {b.salesPaise > 0 && <path d={barPath(cx(i) - barW - 1, y(b.salesPaise / 100), barW, base)} fill={SERIES.sales.color} />}
              {b.costPaise > 0 && <path d={barPath(cx(i) + 1, y(b.costPaise / 100), barW, base)} fill={SERIES.cost.color} />}
              {i % every === 0 && <text x={cx(i)} y={H - 8} textAnchor="middle" fontSize="11" fill="#6b6f75">{label(b, true)}</text>}
            </g>
          ))}
          <polyline points={profitPoints} fill="none" stroke={SERIES.profit.color} strokeWidth="2" strokeLinejoin="round" />
          {trend.map((b, i) => (n <= 14 || hover === i) && (
            <circle key={b.from} cx={cx(i)} cy={y(b.profitPaise / 100)} r="4" fill={SERIES.profit.color} stroke={SURFACE} strokeWidth="2" />
          ))}
          {/* hit areas: the whole column, bigger than the bars, so small bars are easy to hover */}
          {trend.map((b, i) => (
            <rect key={b.from} x={cx(i) - groupW / 2} y={0} width={groupW} height={H} fill="transparent" onMouseEnter={() => setHover(i)} onClick={() => setHover(i)} />
          ))}
        </svg>
      )}
      {hover !== null && !empty && (
        <div className="pointer-events-none absolute top-0 z-10 w-44 rounded-xl border border-edge bg-white px-3 py-2 text-[12px] shadow-lg"
          style={{ left: Math.min(Math.max(cx(hover) - 88, 0), width - 176) }}>
          <div className="mb-1 font-semibold">{label(trend[hover], false)}</div>
          {Object.entries(SERIES).map(([key, s]) => (
            <div key={key} className="flex items-center gap-2">
              <span className="h-2 w-2 rounded-sm" style={{ background: s.color }} />
              <span className="flex-1 text-muted">{s.label}</span>
              <span className="font-semibold">{rupees(trend[hover][`${key}Paise`])}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

/** Keeps the biggest slices and puts the rest together as "Other", so no slice needs a colour outside the set. */
function fold(slices, keep = 6) {
  const sorted = [...slices].filter((s) => s.amountPaise > 0).sort((a, b) => b.amountPaise - a.amountPaise)
  if (sorted.length <= keep + 1) return sorted.map((s, i) => ({ ...s, color: COLORS[i] }))
  const rest = sorted.slice(keep).reduce((sum, s) => sum + s.amountPaise, 0)
  return [...sorted.slice(0, keep).map((s, i) => ({ ...s, color: COLORS[i] })), { name: 'Others', amountPaise: rest, color: OTHER }]
}

/** A ring split into slices, with a legend. The middle shows the total, or the slice under the pointer. */
export function Donut({ slices, totalLabel, emptyText }) {
  const [hover, setHover] = useState(null)
  const parts = fold(slices)
  const total = parts.reduce((sum, s) => sum + s.amountPaise, 0)
  if (total === 0) return <p className="grid h-40 place-items-center text-[13px] text-muted">{emptyText}</p>

  const size = 168, stroke = 24, r = (size - stroke) / 2 - 2, C = 2 * Math.PI * r
  const gap = parts.length > 1 ? 2 : 0 // a thin cut between slices
  let offset = 0
  const pct = (s) => Math.round((s.amountPaise / total) * 100)
  const shown = hover === null ? null : parts[hover]

  return (
    <div className="flex flex-col items-center gap-4 sm:flex-row">
      <div className="relative shrink-0" style={{ width: size, height: size }} onMouseLeave={() => setHover(null)}>
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="-rotate-90">
          {parts.map((s, i) => {
            const len = (s.amountPaise / total) * C
            const arc = (
              <circle key={s.name} cx={size / 2} cy={size / 2} r={r} fill="none" stroke={s.color}
                strokeWidth={hover === i ? stroke + 4 : stroke} strokeDasharray={`${Math.max(len - gap, 0.5)} ${C}`} strokeDashoffset={-offset}
                opacity={hover === null || hover === i ? 1 : 0.45} onMouseEnter={() => setHover(i)} onClick={() => setHover(i)} className="cursor-pointer transition-opacity" />
            )
            offset += len
            return arc
          })}
        </svg>
        <div className="pointer-events-none absolute inset-0 grid place-items-center text-center">
          <div>
            <div className="text-[17px] font-extrabold leading-tight">{rupees(shown ? shown.amountPaise : total)}</div>
            <div className="max-w-24 text-[11px] leading-tight text-muted">{shown ? `${shown.name} · ${pct(shown)}%` : totalLabel}</div>
          </div>
        </div>
      </div>
      <ul className="w-full min-w-0 flex-1 space-y-1.5 text-[13px]">
        {parts.map((s, i) => (
          <li key={s.name} onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}
            className={`flex items-center gap-2 rounded-md px-1.5 py-0.5 ${hover === i ? 'bg-stone-100' : ''}`}>
            <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: s.color }} />
            <span className="min-w-0 flex-1 truncate">{s.name}</span>
            <span className="text-muted">{rupees(s.amountPaise)}</span>
            <span className="w-9 text-right font-semibold">{pct(s)}%</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
