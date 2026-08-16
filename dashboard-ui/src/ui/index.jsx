import { createContext, useContext, useState, useCallback, useRef } from 'react'
import * as Dlg from '@radix-ui/react-dialog'
import * as Tip from '@radix-ui/react-tooltip'
import clsx from 'clsx'
import s from './ui.module.css'

export { useResource, apiFetch, apiJson, authHeaders } from './useResource'

// ── Button ──────────────────────────────────────────────────────────────────
export function Btn({ variant, size, block, className, children, ...p }) {
  return (
    <button className={clsx(s.btn, variant && s[variant], size && s[size], block && s.block, className)} {...p}>
      {children}
    </button>
  )
}

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

// ── Dialog (Radix — focus trap, ESC, focus restore, ARIA built in) ────────────
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
