import { useState } from 'react'
import { Check, ChevronDown, ChevronLeft, Lock, Mail } from 'lucide-react'
import Logo, { hideBroken } from '../../components/Logo'
import { post } from '../../api'
import useGoogleLogin from './useGoogleLogin'
import { CATEGORIES } from '../../categories'

const STEPS = ['Phone Verification', 'Shop Details', 'All Set!']
const STEP_INDEX = { phone: 0, email: 0, otp: 0, shop: 1, done: 2 }

const inputCls = 'h-[50px] w-full rounded-[10px] border border-line bg-white px-4 text-[15px] outline-none focus:border-brand'
const buttonCls = 'h-[48px] w-full rounded-[10px] bg-brand text-[16px] font-semibold text-white hover:bg-brand-dark disabled:opacity-60'
const outlineCls = 'flex h-[50px] w-full items-center justify-center gap-4 rounded-[10px] border border-line bg-white text-[15px] font-medium hover:bg-stone-50'

function Google() {
  return (
    <svg width="22" height="22" viewBox="0 0 48 48">
      <path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9 3.6l6.7-6.7C35.7 2.4 30.3 0 24 0 14.6 0 6.5 5.4 2.6 13.2l7.8 6.1C12.3 13.5 17.7 9.5 24 9.5z" />
      <path fill="#4285F4" d="M46.5 24.5c0-1.6-.1-3.1-.4-4.5H24v9h12.7c-.6 3-2.3 5.5-4.8 7.2l7.5 5.8c4.4-4 7.1-10 7.1-17.5z" />
      <path fill="#FBBC05" d="M10.4 28.7A14.5 14.5 0 0 1 9.5 24c0-1.6.3-3.2.9-4.7l-7.8-6.1A24 24 0 0 0 0 24c0 3.9.9 7.5 2.6 10.8l7.8-6.1z" />
      <path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.5-5.8c-2.1 1.4-4.8 2.3-8.4 2.3-6.3 0-11.7-4-13.6-9.8l-7.8 6.1C6.5 42.6 14.6 48 24 48z" />
    </svg>
  )
}

export default function AuthCard({ mode, setMode, lang, onLogin }) {
  const signup = mode === 'signup'
  const [step, setStep] = useState('phone') // phone or email -> otp -> (shop -> done)
  const [method, setMethod] = useState('phone') // how the code was sent: 'phone' or 'email'
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [otp, setOtp] = useState('')
  const [shop, setShop] = useState({ ownerName: '', shopName: '', category: CATEGORIES[0] })
  const [token, setToken] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const switchMode = (m) => { setMode(m); setStep('phone'); setOtp(''); setError('') }
  const goTo = (nextStep) => { setStep(nextStep); setError('') }

  // Runs an API call, showing its error message instead of throwing.
  const submit = (action) => async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try { await action() } catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  // After any kind of login: existing users go to the dashboard, new users set up their shop.
  const finishLogin = (res) => {
    setToken(res.token)
    if (!res.isNewUser) return onLogin(res.token)
    setMode('signup')
    setShop((s) => ({ ...s, ownerName: res.name || s.ownerName }))
    setStep('shop')
  }

  const sendOtp = submit(async () => {
    await post('/api/auth/send-otp', { phone })
    setMethod('phone')
    setStep('otp')
  })
  const sendEmailOtp = submit(async () => {
    await post('/api/auth/send-email-otp', { email })
    setMethod('email')
    setStep('otp')
  })
  const verifyOtp = submit(async () => {
    finishLogin(method === 'email'
      ? await post('/api/auth/verify-email-otp', { email, otp })
      : await post('/api/auth/verify-otp', { phone, otp }))
  })
  const saveShop = submit(async () => {
    await post('/api/shops', { ...shop, language: lang }, token)
    setStep('done')
  })

  const googleLogin = useGoogleLogin(
    async (accessToken) => {
      try { finishLogin(await post('/api/auth/google', { accessToken })) } catch (err) { setError(err.message) }
    },
    setError,
  )

  const heading = {
    phone: signup ? ['Create Your Account', "Let's set up your shop in a few simple steps."] : ['Welcome Back!', 'Sign in to continue to your shop'],
    email: ['Sign in with Email', "We'll send a 6-digit code to your email."],
    otp: ['Enter OTP', `We sent a 6-digit code to ${method === 'email' ? email : '+91 ' + phone}`],
    shop: ['Tell us about your shop', 'This helps Saathi set things up for you.'],
    done: ["You're all set!", 'Saathi is ready to help you run your dukaan.'],
  }[step]

  return (
    <section className="flex min-h-screen flex-1 items-center justify-center p-4">
      <div className="w-full max-w-[440px] overflow-hidden rounded-[26px] border border-[#f0ebe1] bg-card px-7 pb-8 pt-7 shadow-[0_2px_10px_rgba(0,0,0,0.03)]">
        <div className="flex items-center justify-between text-[13px]">
          {signup ? (
            <button onClick={() => switchMode('login')} className="flex items-center gap-2"><ChevronLeft size={20} /> Back to Login</button>
          ) : <span />}
          <span>
            {signup ? 'Already have an account?' : "Don't have an account?"}
            <button onClick={() => switchMode(signup ? 'login' : 'signup')} className="ml-2 text-[15px] font-bold text-brand">{signup ? 'Login' : 'Sign Up'}</button>
          </span>
        </div>

        <div className="mt-7"><Logo center /></div>
        <h2 className="mt-7 text-center text-[30px] font-extrabold tracking-tight">{heading[0]}</h2>
        <p className="mt-1 text-center text-[16px] text-muted">{heading[1]}</p>

        {signup && (
          <ol className="relative mt-9 grid grid-cols-3 text-center text-[12.5px] leading-tight">
            <span className="absolute left-[16.6%] right-[16.6%] top-[15px] h-px bg-[#d9dcdf]" />
            {STEPS.map((label, i) => {
              const current = STEP_INDEX[step]
              return (
                <li key={label} className="relative flex flex-col items-center gap-2">
                  <span className={`grid h-[30px] w-[30px] place-items-center rounded-full border text-[14px] ${i <= current ? 'border-brand bg-brand text-white' : 'border-[#d5d8dc] bg-white'}`}>
                    {i < current || step === 'done' ? <Check size={16} /> : i + 1}
                  </span>
                  <span className="max-w-[80px]">{label}</span>
                </li>
              )
            })}
          </ol>
        )}

        <div className={signup ? 'mt-8' : 'mt-9'}>
          {step === 'phone' && (
            <form onSubmit={sendOtp} className="space-y-[22px]">
              <div className="flex h-[50px] items-center rounded-[10px] border border-line bg-white focus-within:border-brand">
                <span className="flex h-7 items-center gap-3 border-r border-line pl-4 pr-4 text-[15px] font-medium">
                  <span className="flex h-[15px] w-[22px] flex-col overflow-hidden rounded-[2px]"><i className="flex-1 bg-[#ff9933]" /><i className="flex-1 bg-white" /><i className="flex-1 bg-[#138808]" /></span>
                  +91 <ChevronDown size={14} className="-ml-1" />
                </span>
                <input value={phone} onChange={(e) => setPhone(e.target.value.replace(/\D/g, ''))} maxLength={10} inputMode="numeric"
                  placeholder="Enter your mobile number" className="w-full bg-transparent px-3 text-[15px] outline-none placeholder:text-[#7b8087]" />
              </div>
              <button disabled={busy} className={buttonCls}>Send OTP</button>
              <div className="flex items-center gap-3 text-[12.5px] text-muted"><span className="h-px flex-1 bg-line" />or continue with<span className="h-px flex-1 bg-line" /></div>
              <div className="space-y-3">
                <button type="button" onClick={googleLogin} className={outlineCls}><Google /> Continue with Google</button>
                <button type="button" onClick={() => goTo('email')} className={outlineCls}><Mail size={22} strokeWidth={1.8} /> Continue with Email</button>
              </div>
            </form>
          )}

          {step === 'email' && (
            <form onSubmit={sendEmailOtp} className="space-y-4">
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoFocus
                placeholder="Enter your email address" className={inputCls} />
              <button disabled={busy} className={buttonCls}>Send OTP</button>
              <button type="button" onClick={() => goTo('phone')} className="w-full text-sm text-brand">Use phone number instead</button>
            </form>
          )}

          {step === 'otp' && (
            <form onSubmit={verifyOtp} className="space-y-4">
              <input value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))} maxLength={6} inputMode="numeric" autoFocus
                placeholder="6-digit OTP" className={`${inputCls} text-center text-xl tracking-widest`} />
              <button disabled={busy} className={buttonCls}>Verify &amp; Continue</button>
              <button type="button" onClick={() => goTo(method)} className="w-full text-sm text-brand">
                {method === 'email' ? 'Change email' : 'Change number'}
              </button>
            </form>
          )}

          {step === 'shop' && (
            <form onSubmit={saveShop} className="space-y-4">
              <input value={shop.ownerName} onChange={(e) => setShop({ ...shop, ownerName: e.target.value })} placeholder="Your name" className={inputCls} />
              <input value={shop.shopName} onChange={(e) => setShop({ ...shop, shopName: e.target.value })} placeholder="Shop name" className={inputCls} />
              <select value={shop.category} onChange={(e) => setShop({ ...shop, category: e.target.value })} className={inputCls}>
                {CATEGORIES.map((c) => <option key={c}>{c}</option>)}
              </select>
              <button disabled={busy} className={buttonCls}>Finish setup</button>
            </form>
          )}

          {step === 'done' && (
            <div className="space-y-6">
              <div className="mx-auto grid h-16 w-16 place-items-center rounded-full bg-mint text-brand"><Check size={32} /></div>
              <button onClick={() => onLogin(token)} className={buttonCls}>Go to my dashboard</button>
            </div>
          )}

          {error && <p className="mt-4 text-center text-sm text-red-600">{error}</p>}
        </div>

        {step === 'phone' && signup && (
          <div className="mt-7 flex items-center gap-5 rounded-[14px] bg-secure px-6 py-5">
            <Lock size={26} fill="currentColor" className="shrink-0 text-brand" />
            <div>
              <div className="text-[14.5px] font-bold">Quick and Secure</div>
              <div className="mt-1 text-[13.5px] leading-relaxed text-muted">We'll never share your data. This account is only for your shop.</div>
            </div>
          </div>
        )}
        {step === 'phone' && !signup && (
          <div className="relative -mx-7 -mb-8 mt-6 h-44 bg-gradient-to-b from-transparent to-warm">
            <img src="/images/illustration.jpg" alt="" className="absolute bottom-0 left-2 h-40 mix-blend-multiply [mask-image:linear-gradient(to_top,black_75%,transparent)]" onError={hideBroken} />
            <div className="absolute bottom-8 right-6 -rotate-6 font-hand text-[19px] italic leading-snug">
              "Chhoti dukaan,<br />badi ummeedein.<br />Ab Saathi ke saath."
              <svg width="90" height="12" className="ml-6 mt-1"><path d="M2 9C30 3 60 2 88 4" stroke="#f2a93b" strokeWidth="3" fill="none" strokeLinecap="round" /></svg>
            </div>
          </div>
        )}
      </div>
    </section>
  )
}
