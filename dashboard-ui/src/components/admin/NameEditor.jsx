import { useState, useEffect } from 'react'
import { Btn, Field, Input, Dialog, DialogButtons } from '../../ui'
import { MC_COLORS } from './bookModel'
import { readName, writeName } from './stackModel'
import styles from './NameEditor.module.css'

const BLANK = { text: '', color: '', bold: false, italic: false, underlined: false }

/**
 * Renames one stack, with colour. Writes minecraft:custom_name, which vanilla stores as a full
 * text component, so a name can carry a colour and formatting the same way book text does.
 *
 * Clearing the text removes the component and the item goes back to its normal name.
 */
export default function NameEditor({ open, onOpenChange, stack, onSave }) {
  const [name, setName] = useState(BLANK)

  useEffect(() => {
    if (open) setName(readName(stack) || BLANK)
  }, [open, stack])

  function set(key, value) { setName(prev => ({ ...prev, [key]: value })) }

  const swatch = MC_COLORS.find(c => c.id === name.color)

  return (
    <Dialog open={open} onOpenChange={onOpenChange} title="Rename item"
      description="Leave the name empty to go back to the item's normal name.">
      <Field label="Name">
        <Input autoFocus value={name.text} placeholder="Duck Slayer"
          onChange={e => set('text', e.target.value)} />
      </Field>

      <Field label="Colour" hint="No colour keeps the usual rarity colour">
        <div className={styles.swatches}>
          <button type="button" title="No colour"
            className={`${styles.swatch} ${styles.swatchNone} ${!name.color ? styles.swatchOn : ''}`}
            onClick={() => set('color', '')}>/</button>
          {MC_COLORS.map(c => (
            <button key={c.id} type="button" title={c.id}
              className={`${styles.swatch} ${name.color === c.id ? styles.swatchOn : ''}`}
              style={{ background: c.hex }}
              onClick={() => set('color', c.id)} />
          ))}
        </div>
      </Field>

      <Field label="Style">
        <div className={styles.flags}>
          {[['bold', 'B'], ['italic', 'I'], ['underlined', 'U']].map(([key, label]) => (
            <button key={key} type="button" title={key}
              className={`${styles.flag} ${styles[key]} ${name[key] ? styles.flagOn : ''}`}
              onClick={() => set(key, !name[key])}>{label}</button>
          ))}
        </div>
      </Field>

      {/* Vanilla adds italic to every custom name from the parent style, so the preview shows
          upright text only because the saved component states italic:false outright. */}
      <div className={styles.previewWrap}>
        <span className={styles.previewLabel}>In game</span>
        <span className={styles.preview} style={{
          color: swatch ? swatch.hex : 'var(--txt-primary)',
          fontWeight: name.bold ? 700 : 400,
          fontStyle: name.italic ? 'italic' : 'normal',
          textDecoration: name.underlined ? 'underline' : 'none',
        }}>{name.text || 'No custom name'}</span>
      </div>

      <DialogButtons>
        <Btn onClick={() => onOpenChange(false)}>Cancel</Btn>
        <Btn variant="primary" onClick={() => { onSave(writeName(stack, name)); onOpenChange(false) }}>
          Save name
        </Btn>
      </DialogButtons>
    </Dialog>
  )
}
