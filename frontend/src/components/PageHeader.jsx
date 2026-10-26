/** Icon, title and one-line description at the top of a page. */
export default function PageHeader({ icon, title, subtitle }) {
  return (
    <div className="flex items-center gap-4">
      {icon}
      <div>
        <h1 className="text-[30px] font-extrabold leading-tight tracking-tight">{title}</h1>
        <p className="text-[17px] text-muted">{subtitle}</p>
      </div>
    </div>
  )
}
