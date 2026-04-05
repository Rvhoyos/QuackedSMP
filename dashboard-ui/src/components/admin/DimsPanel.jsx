import { useState, useEffect, useCallback, useRef } from 'react'
import styles from './DimsPanel.module.css'

function authHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

const TYPE_OPTIONS = [
  { value: 'overworld',        label: 'Overworld (default biomes)' },
  { value: 'overworld-biomes', label: 'Overworld (custom biomes)' },
  { value: 'overworld-flat',   label: 'Overworld Flat' },
  { value: 'ether',            label: 'Ether - floating islands (default biomes)' },
  { value: 'ether-biomes',     label: 'Ether - floating islands (custom biomes)' },
  { value: 'nether',           label: 'Nether' },
  { value: 'end',              label: 'End' },
]

const BIOME_TYPES = new Set(['overworld-biomes', 'ether-biomes'])

function buildApiPayload(uiType, flatConfig, selectedBiomes) {
  switch (uiType) {
    case 'overworld':        return { type: 'overworld' }
    case 'overworld-biomes': {
      if (!selectedBiomes.length) return { type: 'overworld' }
      return { type: 'overworld', config: `biomes ${selectedBiomes.map(b => `${b.id}:${b.weight}`).join(' ')}` }
    }
    case 'overworld-flat': {
      const raw = flatConfig.trim()
      return {
        type: 'overworld',
        config: raw ? `flat ${raw}` : 'flat minecraft:bedrock:1 minecraft:stone:6 minecraft:dirt:2 minecraft:grass_block:1',
      }
    }
    case 'ether':            return { type: 'ether' }
    case 'ether-biomes': {
      if (!selectedBiomes.length) return { type: 'ether' }
      return { type: 'ether', config: `biomes ${selectedBiomes.map(b => `${b.id}:${b.weight}`).join(' ')}` }
    }
    case 'nether':           return { type: 'nether' }
    case 'end':              return { type: 'end' }
    default:                 return { type: uiType }
  }
}

export default function DimsPanel({ token, onExpired }) {
  const [dims,          setDims]          = useState([])
  const [loading,       setLoading]       = useState(true)
  const [error,         setError]         = useState(null)

  const [allBlocks,     setAllBlocks]     = useState([])
  const [blocksLoaded,  setBlocksLoaded]  = useState(false)
  const [allBiomes,     setAllBiomes]     = useState([])
  const [biomesLoaded,  setBiomesLoaded]  = useState(false)

  // Create form
  const [createId,       setCreateId]       = useState('')
  const [createUiType,   setCreateUiType]   = useState('overworld')
  const [flatConfig,     setFlatConfig]     = useState('')
  const [selectedBiomes, setSelectedBiomes] = useState([])
  const [biomeSearch,    setBiomeSearch]    = useState('')
  const [biomeWeight,    setBiomeWeight]    = useState('1')
  const [biomeOpen,      setBiomeOpen]      = useState(false)
  const [creating,       setCreating]       = useState(false)
  const [warning,        setWarning]        = useState(null)

  // Portal set
  const [portalEditing,  setPortalEditing]  = useState(null)
  const [portalInput,    setPortalInput]    = useState('')
  const [portalSaving,   setPortalSaving]   = useState(false)

  // Delete confirm
  const [confirmDel,     setConfirmDel]     = useState(null)
  const [deleting,       setDeleting]       = useState(false)

  const biomeComboRef = useRef(null)

  // Close biome dropdown on outside click
  useEffect(() => {
    function onMouseDown(e) {
      if (biomeComboRef.current && !biomeComboRef.current.contains(e.target)) {
        setBiomeOpen(false)
      }
    }
    document.addEventListener('mousedown', onMouseDown)
    return () => document.removeEventListener('mousedown', onMouseDown)
  }, [])

  // Data fetchers
  const fetchDims = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await fetch('/api/admin/dims', { headers: authHeaders(token) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      setDims(data)
    } catch {
      setError('Failed to load dimensions')
    } finally {
      setLoading(false)
    }
  }, [token]) // eslint-disable-line react-hooks/exhaustive-deps

  const fetchBlocks = useCallback(async () => {
    if (blocksLoaded) return
    try {
      const res = await fetch('/api/admin/blocks', { headers: authHeaders(token) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (Array.isArray(data)) { setAllBlocks(data); setBlocksLoaded(true) }
    } catch {}
  }, [blocksLoaded, token, onExpired])

  const fetchBiomes = useCallback(async () => {
    if (biomesLoaded) return
    try {
      const res = await fetch('/api/admin/biomes', { headers: authHeaders(token) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (Array.isArray(data)) { setAllBiomes(data); setBiomesLoaded(true) }
    } catch {}
  }, [biomesLoaded, token, onExpired])

  useEffect(() => { fetchDims() }, [fetchDims])

  useEffect(() => {
    if (BIOME_TYPES.has(createUiType)) fetchBiomes()
  }, [createUiType]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (portalEditing != null) fetchBlocks()
  }, [portalEditing]) // eslint-disable-line react-hooks/exhaustive-deps

  // Handlers
  async function handleCreate(e) {
    e.preventDefault()
    setCreating(true)
    setError(null)
    setWarning(null)
    try {
      const { type, config } = buildApiPayload(createUiType, flatConfig, selectedBiomes)
      const body = { id: createId.trim(), type }
      if (config) body.config = config
      const res = await fetch('/api/admin/dims/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
        body: JSON.stringify(body),
      })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      setCreateId(''); setFlatConfig(''); setSelectedBiomes([])
      setWarning('Server restart required for block breaking/placing in the new dimension. Until then it is read-only.')
      await fetchDims()
    } catch {
      setError('Create failed')
    } finally {
      setCreating(false)
    }
  }

  async function handleDelete(dimId) {
    setDeleting(true)
    setError(null)
    try {
      const res = await fetch('/api/admin/dims/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
        body: JSON.stringify({ id: dimId }),
      })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      setConfirmDel(null)
      await fetchDims()
    } catch {
      setError('Delete failed')
    } finally {
      setDeleting(false)
    }
  }

  async function handleSetPortal(dimId) {
    const blockId = portalInput.trim()
    if (!blockId) return
    setPortalSaving(true)
    setError(null)
    try {
      const res = await fetch('/api/admin/dims/setportal', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
        body: JSON.stringify({ dimId, blockId }),
      })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      setPortalEditing(null); setPortalInput('')
      await fetchDims()
    } catch {
      setError('Set portal failed')
    } finally {
      setPortalSaving(false)
    }
  }

  function selectBiome(id) {
    const w = Math.max(1, parseInt(biomeWeight, 10) || 1)
    setSelectedBiomes(prev => [...prev, { id, weight: w }])
    setBiomeSearch('')
    setBiomeOpen(false)
  }

  function removeBiome(idx) {
    setSelectedBiomes(prev => prev.filter((_, i) => i !== idx))
  }

  // Filtered lists
  const portalIsKnown = !blocksLoaded || !portalInput.trim() || allBlocks.includes(portalInput.trim())

  const blockSuggestions = (() => {
    const q = portalInput.trim()
    if (!q) return allBlocks.slice(0, 60)
    return allBlocks.filter(id => id.startsWith(q) || (q.length >= 2 && id.includes(q))).slice(0, 60)
  })()

  const filteredBiomes = (() => {
    const q = biomeSearch.trim().toLowerCase()
    if (!q) return allBiomes
    return allBiomes.filter(id => id.includes(q))
  })()

  const isBiomeType = BIOME_TYPES.has(createUiType)
  const isFlatType  = createUiType === 'overworld-flat'

  return (
    <div className={styles.wrap}>

      {/* Create form */}
      <div className={styles.createBox}>
        <div className={styles.sectionTitle}>Create Dimension</div>
        <form className={styles.createForm} onSubmit={handleCreate}>

          <div className={styles.createRow}>
            <input
              className={styles.input}
              placeholder="quacksmp:pvp"
              value={createId}
              onChange={e => setCreateId(e.target.value)}
              required
              spellCheck={false}
            />
            <select
              className={styles.select}
              value={createUiType}
              onChange={e => {
                setCreateUiType(e.target.value)
                setFlatConfig('')
                setSelectedBiomes([])
                setBiomeSearch('')
              }}
            >
              {TYPE_OPTIONS.map(o => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
            <button type="submit" className={styles.btn} disabled={creating || !createId.trim()}>
              {creating ? '...' : 'Create'}
            </button>
          </div>

          {/* Flat layers input */}
          {isFlatType && (
            <input
              className={styles.input}
              placeholder="minecraft:bedrock:1  minecraft:stone:6  minecraft:dirt:2  minecraft:grass_block:1"
              title="Layers bottom to top (block:height). Leave blank for default."
              value={flatConfig}
              onChange={e => setFlatConfig(e.target.value)}
              spellCheck={false}
            />
          )}

          {/* Biome builder */}
          {isBiomeType && (
            <div className={styles.biomeBuilder}>

              {selectedBiomes.length > 0 && (
                <div className={styles.biomeChips}>
                  {selectedBiomes.map((b, i) => (
                    <span key={i} className={styles.biomeChip}>
                      <span className={styles.biomeChipId}>{b.id}</span>
                      <span className={styles.biomeChipW}>x{b.weight}</span>
                      <button
                        type="button"
                        className={styles.biomeChipRemove}
                        onClick={() => removeBiome(i)}
                      >x</button>
                    </span>
                  ))}
                </div>
              )}

              <div className={styles.biomeAddRow}>
                {/* Custom combobox */}
                <div className={styles.biomeCombo} ref={biomeComboRef}>
                  <input
                    className={styles.input}
                    placeholder={biomesLoaded ? `Search ${allBiomes.length} biomes...` : 'minecraft:plains'}
                    value={biomeSearch}
                    onChange={e => { setBiomeSearch(e.target.value); setBiomeOpen(true) }}
                    onFocus={() => setBiomeOpen(true)}
                    spellCheck={false}
                  />
                  {biomeOpen && (
                    <div className={styles.biomeDropdown}>
                      {!biomesLoaded && (
                        <div className={styles.biomeDropdownMsg}>Loading...</div>
                      )}
                      {biomesLoaded && filteredBiomes.length === 0 && (
                        <div className={styles.biomeDropdownMsg}>No matches</div>
                      )}
                      {filteredBiomes.slice(0, 200).map(id => (
                        <button
                          key={id}
                          type="button"
                          className={styles.biomeDropdownItem}
                          onMouseDown={e => { e.preventDefault(); selectBiome(id) }}
                        >
                          {id}
                        </button>
                      ))}
                    </div>
                  )}
                </div>

                <input
                  className={styles.weightInput}
                  type="number"
                  min="1"
                  max="999"
                  value={biomeWeight}
                  onChange={e => setBiomeWeight(e.target.value)}
                  title="Relative weight"
                />
              </div>

              {selectedBiomes.length === 0 && (
                <div className={styles.biomeHint}>
                  No biomes selected - will use all default biomes for this type.
                </div>
              )}
            </div>
          )}
        </form>

        {error && (
          <div className={styles.error}>
            {error}
            <button className={styles.dismiss} onClick={() => setError(null)}>x</button>
          </div>
        )}
        {warning && (
          <div className={styles.warning}>
            {warning}
            <button className={styles.dismiss} onClick={() => setWarning(null)}>x</button>
          </div>
        )}
      </div>

      {/* Dim list toolbar */}
      <div className={styles.toolbar}>
        <span className={styles.count}>
          {loading ? 'Loading...' : `${dims.length} custom dimension${dims.length !== 1 ? 's' : ''}`}
        </span>
        <button className={styles.refresh} onClick={fetchDims} disabled={loading}>Refresh</button>
      </div>

      <div className={styles.list}>
        {!loading && dims.length === 0 && (
          <div className={styles.empty}>No custom dimensions. Create one above.</div>
        )}
        {dims.map(dim => (
          <div key={dim.id} className={styles.row}>
            <div className={styles.dimInfo}>
              <span className={styles.dimId}>{dim.id}</span>
              <span className={styles.dimType}>{dim.generatorType}</span>
              {dim.generatorConfig && (
                <span className={styles.dimConfig} title={dim.generatorConfig}>
                  {dim.generatorConfig.length > 40
                    ? dim.generatorConfig.slice(0, 38) + '...'
                    : dim.generatorConfig}
                </span>
              )}
            </div>

            <div className={styles.dimActions}>
              <span className={styles.portalTag}>
                {dim.portalBlock
                  ? <span className={styles.portalSet}>{dim.portalBlock}</span>
                  : <span className={styles.portalUnset}>no portal</span>}
              </span>

              {portalEditing === dim.id ? (
                <span className={styles.portalEdit}>
                  <span className={styles.portalInputWrap}>
                    <input
                      className={`${styles.inputSm} ${!portalIsKnown ? styles.inputInvalid : ''}`}
                      list="block-datalist"
                      placeholder="minecraft:glowstone"
                      value={portalInput}
                      onChange={e => setPortalInput(e.target.value)}
                      onKeyDown={e => { if (e.key === 'Enter') handleSetPortal(dim.id) }}
                      autoFocus
                      spellCheck={false}
                    />
                    <datalist id="block-datalist">
                      {blockSuggestions.map(id => <option key={id} value={id} />)}
                    </datalist>
                    {!portalIsKnown && (
                      <span className={styles.inputWarn}>Unknown block</span>
                    )}
                  </span>
                  <button
                    className={styles.btn}
                    onClick={() => handleSetPortal(dim.id)}
                    disabled={portalSaving || !portalInput.trim()}
                  >
                    {portalSaving ? '...' : 'Set'}
                  </button>
                  <button
                    className={styles.btnGhost}
                    onClick={() => { setPortalEditing(null); setPortalInput('') }}
                  >
                    Cancel
                  </button>
                </span>
              ) : (
                <button
                  className={styles.btnGhost}
                  onClick={() => {
                    setPortalEditing(dim.id)
                    setPortalInput(dim.portalBlock ?? '')
                  }}
                >
                  Set Portal
                </button>
              )}

              {confirmDel === dim.id ? (
                <span className={styles.confirmDel}>
                  <span className={styles.confirmLabel}>Delete?</span>
                  <button
                    className={styles.btnDanger}
                    onClick={() => handleDelete(dim.id)}
                    disabled={deleting}
                  >
                    {deleting ? '...' : 'Yes'}
                  </button>
                  <button className={styles.btnGhost} onClick={() => setConfirmDel(null)}>No</button>
                </span>
              ) : (
                <button className={styles.btnDanger} onClick={() => setConfirmDel(dim.id)}>
                  Delete
                </button>
              )}
            </div>
          </div>
        ))}
      </div>

      <div className={styles.hint}>
        Portal: build a nether-portal-shaped frame (2-21 wide, 3-21 tall) with your portal block, then right-click a frame block with a water bucket.
      </div>
    </div>
  )
}
