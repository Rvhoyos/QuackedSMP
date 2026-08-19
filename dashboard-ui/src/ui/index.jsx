import { createContext, useContext, useState, useCallback, useRef, forwardRef } from 'react'
import * as Dlg from '@radix-ui/react-dialog'
import * as Tip from '@radix-ui/react-tooltip'
import * as Dropdown from '@radix-ui/react-dropdown-menu'
import clsx from 'clsx'
import s from './ui.module.css'

export { useResource, apiFetch, apiJson, authHeaders } from './useResource'

// ── Button ──────────────────────────────────────────────────────────────────
// forwardRef so it can be a Radix `asChild` trigger (e.g. the Menu ⋯ button),
// which needs the ref to anchor its popup.
export const Btn = forwardRef(function Btn({ variant, size, block, className, children, ...p }, ref) {
  return (
    <button ref={ref} className={clsx(s.btn, variant && s[variant], size && s[size], block && s.block, className)} {...p}>
      {children}
    </button>
  )
})

// ── Icon button (optionally wrapped in a tooltip) ────────────────────────────
export function IconButton({ tip, className, children, ...p }) {
  const btn = <button className={clsx(s.iconBtn, className)} aria-label={tip} {...p}>{children}</button>
  if (!tip) return btn
  return (
    <Tip.Root>
      <Tip.Trigger asChild>{btn}</Tip.Trigger>
      <Tip.Portal>
        <Tip.Content className={s.tooltip} sideOffset={5}>
          {tip}
          <Tip.Arrow className={s.tooltipArrow} />
        </Tip.Content>
      </Tip.Portal>
    </Tip.Root>
  )
}

// Wrap the app once so tooltips work everywhere.
export function TooltipProvider({ children }) {
  return <Tip.Provider delayDuration={300} skipDelayDuration={200}>{children}</Tip.Provider>
}

// ── Badge ────────────────────────────────────────────────────────────────────
export function Badge({ variant, className, children }) {
  return <span className={clsx(s.badge, variant && s[variant], className)}>{children}</span>
}

// ── Toolbar ──────────────────────────────────────────────────────────────────
export function Toolbar({ title, count, children }) {
  return (
    <div className={s.toolbar}>
      <div className={s.toolbarTitle}>
        {title}
        {count != null && <span className={s.count}>{count}</span>}
      </div>
      <div className={s.toolbarActions}>{children}</div>
    </div>
  )
}

// ── State displays ────────────────────────────────────────────────────────────
export function Loading({ label = 'Loading…' }) {
  return <div className={s.loading}><div className={s.spinner} />{label}</div>
}

export function EmptyState({ icon, label, hint }) {
  return (
    <div className={s.empty}>
      {icon && <span className={s.emptyIcon}>{icon}</span>}
      {label}
      {hint && <span className={s.emptyHint}>{hint}</span>}
    </div>
  )
}

export function ErrorBanner({ children }) {
  if (!children) return null
  return <div className={s.errorBanner}>⚠ {children}</div>
}

// ── Form controls ─────────────────────────────────────────────────────────────
export function Field({ label, hint, children }) {
  return (
    <label className={s.field}>
      {label && <span className={s.label}>{label}</span>}
      {children}
      {hint && <span className={s.hint}>{hint}</span>}
    </label>
  )
}
export function Input(p)    { return <input className={s.input} {...p} /> }
export function Textarea(p) { return <textarea className={s.textarea} {...p} /> }
export function Select({ className, children, ...p }) {
  return <select className={clsx(s.select, className)} {...p}>{children}</select>
}
export function Toggle({ checked, onChange, disabled, 'aria-label': label }) {
  return (
    <button
      type="button" role="switch" aria-checked={checked} aria-label={label} disabled={disabled}
      className={clsx(s.toggle, checked && s.toggleOn)}
      onClick={() => onChange?.(!checked)}
    />
  )
}

// ── Dialog (Radix, focus trap, ESC, focus restore, ARIA built in) ────────────
export function Dialog({ open, onOpenChange, title, description, children }) {
  return (
    <Dlg.Root open={open} onOpenChange={onOpenChange}>
      <Dlg.Portal>
        <Dlg.Overlay className={s.dialogOverlay} />
        <Dlg.Content className={s.dialogContent}>
          {title && <Dlg.Title className={s.dialogTitle}>{title}</Dlg.Title>}
          {description && <Dlg.Description className={s.dialogDesc}>{description}</Dlg.Description>}
          {children}
        </Dlg.Content>
      </Dlg.Portal>
    </Dlg.Root>
  )
}
export function DialogButtons({ children }) { return <div className={s.dialogBtns}>{children}</div> }

// A ready-made confirm dialog. `tone` colours the confirm button.
export function ConfirmDialog({ open, onOpenChange, title, description, confirmLabel = 'Confirm', tone = 'primary', onConfirm, children }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={title} description={description}>
      {children}
      <DialogButtons>
        <Btn variant="ghost" onClick={() => onOpenChange(false)}>Cancel</Btn>
        <Btn variant={tone} onClick={onConfirm}>{confirmLabel}</Btn>
      </DialogButtons>
    </Dialog>
  )
}

// ── Overflow menu (Radix DropdownMenu) ────────────────────────────────────────
export function Menu({ trigger, children, align = 'end' }) {
  return (
    <Dropdown.Root>
      <Dropdown.Trigger asChild>{trigger}</Dropdown.Trigger>
      <Dropdown.Portal>
        <Dropdown.Content className={s.menu} align={align} sideOffset={4} collisionPadding={8}>
          {children}
        </Dropdown.Content>
      </Dropdown.Portal>
    </Dropdown.Root>
  )
}
export function MenuItem({ children, onSelect, tone, disabled }) {
  // Let Radix close the menu on select (default). The action often opens a
  // dialog rendered as a sibling, which then takes focus cleanly.
  return (
    <Dropdown.Item className={clsx(s.menuItem, tone && s[`menu_${tone}`])} disabled={disabled}
      onSelect={() => onSelect?.()}>
      {children}
    </Dropdown.Item>
  )
}
export function MenuSeparator() { return <Dropdown.Separator className={s.menuSep} /> }

// ── Meter (progress / usage bar) ──────────────────────────────────────────────
export function Meter({ value, max = 1, color, className }) {
  const pct = Math.max(0, Math.min(1, (value || 0) / (max || 1)))
  return (
    <div className={clsx(s.meter, className)}>
      <div className={s.meterFill} style={{ width: `${pct * 100}%`, background: color || 'var(--mc-wood-light)' }} />
    </div>
  )
}

// ── Stat tile ─────────────────────────────────────────────────────────────────
export function StatCard({ label, value, tone }) {
  return (
    <div className={s.statCard}>
      <span className={clsx(s.statVal, tone && s[`stat_${tone}`])}>{value}</span>
      <span className={s.statLabel}>{label}</span>
    </div>
  )
}

// ── Titled section container ──────────────────────────────────────────────────
export function SectionCard({ title, subtitle, actions, children, className }) {
  return (
    <section className={clsx(s.section, className)}>
      {(title || actions) && (
        <div className={s.sectionHead}>
          <div className={s.sectionTitles}>
            {title && <span className={s.sectionTitle}>{title}</span>}
            {subtitle && <span className={s.sectionSub}>{subtitle}</span>}
          </div>
          {actions && <div className={s.sectionActions}>{actions}</div>}
        </div>
      )}
      <div className={s.sectionBody}>{children}</div>
    </section>
  )
}

// ── Slider (range + numeric readout) ──────────────────────────────────────────
export function Slider({ value, min = 0, max = 100, step = 1, onChange, suffix }) {
  return (
    <div className={s.slider}>
      <input type="range" className={s.sliderInput} value={value ?? min} min={min} max={max} step={step}
        onChange={e => onChange?.(parseFloat(e.target.value))} />
      <span className={s.sliderVal}>{value ?? ', '}{suffix}</span>
    </div>
  )
}

// ── Color swatch dot ──────────────────────────────────────────────────────────
export function Swatch({ color, size = 12 }) {
  return <span className={s.swatch} style={{ background: color, width: size, height: size }} />
}

// ── Toast / flash ─────────────────────────────────────────────────────────────
const ToastCtx = createContext(() => {})
export function useToast() { return useContext(ToastCtx) }

export function ToastHost({ children }) {
  const [toasts, setToasts] = useState([])
  const idRef = useRef(0)
  const push = useCallback((msg, variant) => {
    const id = ++idRef.current
    setToasts(t => [...t, { id, msg, variant }])
    setTimeout(() => setToasts(t => t.filter(x => x.id !== id)), 3500)
  }, [])
  return (
    <ToastCtx.Provider value={push}>
      {children}
      <div className={s.toastWrap}>
        {toasts.map(t => <div key={t.id} className={clsx(s.toast, t.variant && s[t.variant])}>{t.msg}</div>)}
      </div>
    </ToastCtx.Provider>
  )
}
