/** Icon, title and one-line description at the top of a page. */
export default function PageHeader({ icon, title, subtitle }) {
  return (
    <div className="flex items-center gap-4">
      <span className="shrink-0">{icon}</span>
      <div>
        <h1 className="text-[24px] font-extrabold leading-tight tracking-tight sm:text-[30px]">{title}</h1>
        <p className="text-[15px] text-muted sm:text-[17px]">{subtitle}</p>
      </div>
    </div>
  )
}
