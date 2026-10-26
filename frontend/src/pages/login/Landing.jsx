import { Box, ChartNoAxesColumnIncreasing, ChevronDown, Globe, Languages, Mic, Smartphone, Users, Zap } from 'lucide-react'
import Logo, { hideBroken } from '../../components/Logo'

function ShieldCheckFilled() {
  return (
    <svg width="26" height="26" viewBox="0 0 24 24">
      <path fill="currentColor" d="M12 2 4 5v6c0 5.2 3.4 9.4 8 11 4.6-1.6 8-5.8 8-11V5l-8-3Z" />
      <path d="m8.5 12 2.5 2.5 4.5-5" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

const features = [
  [<Mic size={22} />, 'Voice First', 'Speak naturally in Hindi, Hinglish or your language.'],
  [<Box size={22} />, 'Manage Inventory', 'Track stock, get restock suggestions.'],
  [<ChartNoAxesColumnIncreasing size={22} strokeWidth={3.2} />, 'Business Advisor', 'Understand profit & loss with easy insights.'],
  [<Users size={22} fill="currentColor" />, 'Customer Handling', 'Record udhaar, payments and reminders.'],
]
const perks = [
  [<ShieldCheckFilled />, 'Safe & Secure', 'Your data is protected'],
  [<Languages size={26} />, 'Indian Languages', 'Built for Bharat'],
  [<Zap size={26} fill="currentColor" />, 'Simple to Use', 'No typing needed'],
  [<Smartphone size={26} />, 'Works on Any Device', 'Mobile and desktop'],
]

// The photo is as tall as the panel, pinned to the right edge (hero.jpg is 557x637).
// The bubble and "Listening..." use the same box, so their % are positions on the photo itself.
const photoBox = 'absolute right-0 top-0 h-full aspect-[557/637]'

export default function Landing({ lang, setLang }) {
  return (
    <section className="relative hidden h-screen min-h-205 overflow-hidden min-[1400px]:block min-[1400px]:w-[52%]">
      <div className={photoBox}>
        <img src="/images/hero.jpg" alt="" className="h-full w-full" onError={hideBroken} />
      </div>
      {/* Fades the photo's left edge into the cream page */}
      <div className="absolute inset-0 bg-gradient-to-r from-cream from-20% via-cream/70 via-40% to-transparent to-60%" />

      <div className={photoBox}>
        <div className="absolute left-[46%] top-[8%] whitespace-nowrap rounded-[28px] bg-white px-6 py-4 text-[20px] leading-snug shadow-lg">
          Saathi, aaj ki<br />sale batao
          <span className="absolute -bottom-2 right-5 h-5 w-5 rotate-45 bg-white" />
        </div>
        <div className="absolute left-[58%] top-[56%] -translate-x-1/2 whitespace-nowrap rounded-full bg-white px-5 py-2 text-[17px] font-medium shadow-md">
          Listening...
        </div>
      </div>

      <div className="relative flex h-full flex-col px-10 pb-6 pt-6">
        <div className="flex items-start justify-between">
          <Logo />
          <label className="relative flex items-center gap-2 rounded-full bg-white py-2.5 pl-4 pr-9 text-[15px] font-medium shadow-[0_2px_8px_rgba(0,0,0,0.08)]">
            <Globe size={18} />
            <select value={lang} onChange={(e) => setLang(e.target.value)} className="appearance-none bg-transparent outline-none">
              <option>English</option>
              <option>हिन्दी</option>
              <option>Hinglish</option>
            </select>
            <ChevronDown size={16} className="pointer-events-none absolute right-4" />
          </label>
        </div>

        {/* Gaps use vh so everything fits on one screen, from short laptops to tall monitors */}
        <h1 className="relative mt-[7vh] text-[46px] font-extrabold leading-[0.95] tracking-tight text-heading">
          Bolkar<br />chalao apni<br /><span className="text-navy">dukaan</span>
          <svg width="36" height="36" viewBox="0 0 36 36" className="absolute left-[228px] top-0 h-7 w-7">
            <path d="M21 3 12 18M19 31l14-5" stroke="#f5a524" strokeWidth="4" strokeLinecap="round" />
          </svg>
        </h1>
        <p className="mt-3 max-w-56 text-[16px] leading-snug text-muted">
          Your AI voice assistant for inventory, profit &amp; loss, and customer management.
        </p>

        <ul className="mt-[4vh] space-y-[2.4vh]">
          {features.map(([icon, title, text]) => (
            <li key={title} className="flex items-center gap-4">
              <span className="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-mint text-brand">{icon}</span>
              <div className="max-w-44">
                <div className="text-[15px] font-bold">{title}</div>
                <div className="text-[13px] leading-snug text-muted">{text}</div>
              </div>
            </li>
          ))}
        </ul>

        <div className="-mx-3.5 mt-auto grid grid-cols-4 divide-x divide-[#dfe7dc] rounded-[22px] bg-perk/95 py-5 text-center">
          {perks.map(([icon, title, text]) => (
            <div key={title} className="flex flex-col items-center px-2">
              <span className="text-brand">{icon}</span>
              <div className="mt-2 text-[14.5px] font-bold">{title}</div>
              <div className="mt-1 text-[12.5px] text-muted">{text}</div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
