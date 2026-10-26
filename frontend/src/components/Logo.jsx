// Until the real photos are added, a missing image just disappears instead of showing a broken icon.
export const hideBroken = (e) => { e.currentTarget.style.visibility = 'hidden' }

// mix-blend-multiply lets a logo with a white background blend into the cream page.
export default function Logo({ center }) {
  return center ? (
    <div className="flex flex-col items-center text-center">
      <img src="/images/logo.jpg" alt="" className="h-[62px] mix-blend-multiply" onError={hideBroken} />
      <div className="mt-3 text-[25px] font-extrabold tracking-tight">Dukaan Saathi</div>
      <div className="text-[13.5px] text-muted">Aapki dukaan ka AI saathi</div>
    </div>
  ) : (
    <div className="flex items-center gap-3">
      <img src="/images/logo.jpg" alt="" className="h-[54px] mix-blend-multiply" onError={hideBroken} />
      <div>
        <div className="text-[24px] font-extrabold leading-tight tracking-tight">Dukaan Saathi</div>
        <div className="text-[14.5px]">Aapki dukaan ka AI saathi</div>
      </div>
    </div>
  )
}
