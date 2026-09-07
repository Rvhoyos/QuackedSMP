import { useState, useEffect, useCallback, useRef } from 'react'
import { Toolbar, IconButton, Btn, Badge, SectionCard, Loading, EmptyState, ErrorBanner, ConfirmDialog, Field, Input, Select, Slider, Toggle, SwitchField, Menu, MenuItem, useToast } from '../../ui'
import { IconPortal } from './MinecraftIcons'
import styles from './DimsPanel.module.css'

function authHeaders(token) { return token ? { Authorization: `Bearer ${token}` } : {} }

const TYPE_OPTIONS = [
  { value: 'overworld', label: 'Overworld (default biomes)' },
  { value: 'overworld-biomes', label: 'Overworld (custom biomes)' },
  { value: 'overworld-flat', label: 'Overworld Flat' },
  { value: 'ether', label: 'Ether: floating islands (default biomes)' },
  { value: 'ether-biomes', label: 'Ether: floating islands (custom biomes)' },
  { value: 'nether', label: 'Nether' },
  { value: 'end', label: 'End' },
]
const BIOME_TYPES = new Set(['overworld-biomes', 'ether-biomes'])
const ETHER_TYPES = new Set(['ether', 'ether-biomes'])

const biomeClause = list => `biomes ${list.map(b => `${b.id}:${b.weight}`).join(' ')}`

// Same keyword grammar the /dim create command uses; the server re-parses and stores it canonically.
function etherClause(p) {
  if (!p) return ''
  return [
    `threshold ${p.threshold}`,
    `radius ${p.minRadius} ${p.maxRadius}`,
    `spacing ${p.spacing}`,
    `thickness ${p.minThickness} ${p.maxThickness}`,
    `height ${p.minCenterY} ${p.maxCenterY}`,
    `structures ${p.structures}`,
  ].join(' ')
}

function buildApiPayload(uiType, flatConfig, selectedBiomes, etherParams) {
  switch (uiType) {
    case 'overworld': return { type: 'overworld' }
    case 'overworld-biomes':
      return selectedBiomes.length ? { type: 'overworld', config: biomeClause(selectedBiomes) } : { type: 'overworld' }
    case 'overworld-flat': {
      const raw = flatConfig.trim()
      return { type: 'overworld', config: raw ? `flat ${raw}` : 'flat minecraft:bedrock:1 minecraft:stone:6 minecraft:dirt:2 minecraft:grass_block:1' }
    }
    case 'ether':
    case 'ether-biomes': {
      const parts = [etherClause(etherParams)]
      if (uiType === 'ether-biomes' && selectedBiomes.length) parts.push(biomeClause(selectedBiomes))
      const config = parts.filter(Boolean).join(' ')
      return config ? { type: 'ether', config } : { type: 'ether' }
    }
    case 'nether': return { type: 'nether' }
    case 'end': return { type: 'end' }
    default: return { type: uiType }
  }
}

export default function DimsPanel({ token, onExpired }) {
  const [dims, setDims] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [allBlocks, setAllBlocks] = useState([])
  const [blocksLoaded, setBlocksLoaded] = useState(false)
  const [allBiomes, setAllBiomes] = useState([])
  const [biomesLoaded, setBiomesLoaded] = useState(false)
  const [createId, setCreateId] = useState('')
  const [createUiType, setCreateUiType] = useState('overworld')
  const [flatConfig, setFlatConfig] = useState('')
  const [selectedBiomes, setSelectedBiomes] = useState([])
  const [etherMeta, setEtherMeta] = useState(null)
  const [etherParams, setEtherParams] = useState(null)
  const [players, setPlayers] = useState([])
  const [tpEditing, setTpEditing] = useState(null)
  const [tpPlayer, setTpPlayer] = useState('')
  const [tpSending, setTpSending] = useState(false)
  const [biomeSearch, setBiomeSearch] = useState('')
  const [biomeWeight, setBiomeWeight] = useState('1')
  const [biomeOpen, setBiomeOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [portalEditing, setPortalEditing] = useState(null)
  const [portalInput, setPortalInput] = useState('')
  const [portalSaving, setPortalSaving] = useState(false)
  const [confirmDel, setConfirmDel] = useState(null)
  const [sky, setSky] = useState(null)
  const [skyYInput, setSkyYInput] = useState('')
  const [skySaving, setSkySaving] = useState(false)
  const biomeComboRef = useRef(null)
  const toast = useToast()

  useEffect(() => {
    function onMouseDown(e) { if (biomeComboRef.current && !biomeComboRef.current.contains(e.target)) setBiomeOpen(false) }
    document.addEventListener('mousedown', onMouseDown)
    return () => document.removeEventListener('mousedown', onMouseDown)
  }, [])

  const fetchDims = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const res = await fetch('/api/admin/dims', { headers: authHeaders(token) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      setDims(data)
    } catch { setError('Failed to load dimensions') } finally { setLoading(false) }
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

  // Defaults and ranges come from EtherIslandParams on the server, never from a copy kept here.
  const fetchEtherMeta = useCallback(async () => {
    if (etherMeta) return
    try {
      const res = await fetch('/api/admin/dims/etherparams', { headers: authHeaders(token) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.defaults) { setEtherMeta(data); setEtherParams(data.defaults) }
    } catch {}
  }, [etherMeta, token, onExpired])

  const fetchPlayers = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/players', { headers: authHeaders(token) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (Array.isArray(data)) setPlayers(data)
    } catch {}
  }, [token, onExpired])

  // Resolved values come from the server: it owns what "auto" and "first created" resolve to.
  const fetchSky = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/dims/sky', { headers: authHeaders(token) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) return
      setSky(data)
      setSkyYInput(String(data.thresholdY))
    } catch {}
  }, [token, onExpired])

  async function saveSky(patch) {
    setSkySaving(true); setError(null)
    try {
      const res = await fetch('/api/admin/dims/sky', { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders(token) }, body: JSON.stringify(patch) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); setSkyYInput(String(sky?.thresholdY ?? 0)); return }
      setSky(data)
      setSkyYInput(String(data.thresholdY))
      toast('Sky entry saved')
      await fetchDims()
    } catch { setError('Save failed') } finally { setSkySaving(false) }
  }

  useEffect(() => { fetchDims() }, [fetchDims])
  useEffect(() => { fetchSky() }, [fetchSky])
  useEffect(() => { if (BIOME_TYPES.has(createUiType)) fetchBiomes() }, [createUiType]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (ETHER_TYPES.has(createUiType)) fetchEtherMeta() }, [createUiType]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (tpEditing != null) fetchPlayers() }, [tpEditing]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (portalEditing != null) fetchBlocks() }, [portalEditing]) // eslint-disable-line react-hooks/exhaustive-deps

  async function handleCreate(e) {
    e.preventDefault()
    setCreating(true); setError(null)
    try {
      const { type, config } = buildApiPayload(createUiType, flatConfig, selectedBiomes, etherParams)
      const body = { id: createId.trim(), type }
      if (config) body.config = config
      const res = await fetch('/api/admin/dims/create', { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders(token) }, body: JSON.stringify(body) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      setCreateId(''); setFlatConfig(''); setSelectedBiomes([])
      toast('Dimension created. Restart required for block editing')
      await fetchDims()
    } catch { setError('Create failed') } finally { setCreating(false) }
  }

  async function handleDelete() {
    const dimId = confirmDel
    try {
      const res = await fetch('/api/admin/dims/delete', { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders(token) }, body: JSON.stringify({ id: dimId }) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      setConfirmDel(null); toast('Dimension deleted'); await fetchDims()
    } catch { setError('Delete failed') }
  }

  async function handleSetPortal(dimId) {
    const blockId = portalInput.trim()
    if (!blockId) return
    setPortalSaving(true); setError(null)
    try {
      const res = await fetch('/api/admin/dims/setportal', { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders(token) }, body: JSON.stringify({ dimId, blockId }) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      setPortalEditing(null); setPortalInput(''); toast('Portal block set'); await fetchDims()
    } catch { setError('Set portal failed') } finally { setPortalSaving(false) }
  }

  async function handleTeleport(dimId) {
    if (!tpPlayer) return
    setTpSending(true); setError(null)
    try {
      const res = await fetch('/api/admin/dims/tp', { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders(token) }, body: JSON.stringify({ dimId, player: tpPlayer }) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      setTpEditing(null); toast(`Teleported ${tpPlayer}`)
    } catch { setError('Teleport failed') } finally { setTpSending(false) }
  }

  const setParam = (key, value) => setEtherParams(p => ({ ...p, [key]: value }))

  function selectBiome(id) {
    const w = Math.max(1, parseInt(biomeWeight, 10) || 1)
    setSelectedBiomes(prev => [...prev, { id, weight: w }]); setBiomeSearch(''); setBiomeOpen(false)
  }

  const portalIsKnown = !blocksLoaded || !portalInput.trim() || allBlocks.includes(portalInput.trim())
  const blockSuggestions = (() => { const q = portalInput.trim(); if (!q) return allBlocks.slice(0, 60); return allBlocks.filter(id => id.startsWith(q) || (q.length >= 2 && id.includes(q))).slice(0, 60) })()
  const filteredBiomes = (() => { const q = biomeSearch.trim().toLowerCase(); if (!q) return allBiomes; return allBiomes.filter(id => id.includes(q)) })()
  const isBiomeType = BIOME_TYPES.has(createUiType)
  const isEtherType = ETHER_TYPES.has(createUiType)
  const isFlatType = createUiType === 'overworld-flat'

  if (loading && dims.length === 0) return <Loading label="Loading dimensions…" />

  return (
    <div className={styles.wrap}>
      <Toolbar title="Dimensions" count={`${dims.length} custom`}>
        <IconButton tip="Refresh" onClick={fetchDims}>↻</IconButton>
      </Toolbar>

      <div className={styles.body}>
        <SectionCard title="Create Dimension">
          <form className={styles.createForm} onSubmit={handleCreate}>
            <div className={styles.createRow}>
              <Field label="Dimension ID"><Input placeholder="quacksmp:pvp" value={createId} onChange={e => setCreateId(e.target.value)} required spellCheck={false} /></Field>
              <Field label="Type"><Select value={createUiType} onChange={e => { setCreateUiType(e.target.value); setFlatConfig(''); setSelectedBiomes([]); setBiomeSearch('') }}>{TYPE_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</Select></Field>
              <Btn variant="primary" type="submit" disabled={creating || !createId.trim()}>{creating ? '…' : 'Create'}</Btn>
            </div>

            {isFlatType && (
              <Field label="Flat layers (block:height, bottom→top)" hint="Blank uses the default layers">
                <Input placeholder="minecraft:bedrock:1  minecraft:stone:6  minecraft:dirt:2  minecraft:grass_block:1" value={flatConfig} onChange={e => setFlatConfig(e.target.value)} spellCheck={false} />
              </Field>
            )}

            {isEtherType && etherParams && etherMeta && (
              <div className={styles.paramGrid}>
                <Field label="Island frequency" hint="Lower spawns fewer islands, high values slow chunk generation"
                  aside={Number(etherParams.threshold).toFixed(2)}>
                  <Slider hideValue value={etherParams.threshold} min={etherMeta.ranges.threshold[0]} max={etherMeta.ranges.threshold[1]}
                    step={0.01} onChange={v => setParam('threshold', v)} />
                </Field>
                <Field label="Grid spacing" hint="Larger spreads them further apart"
                  aside={etherParams.spacing}>
                  <Slider hideValue value={etherParams.spacing} min={etherMeta.ranges.spacing[0]} max={etherMeta.ranges.spacing[1]}
                    step={1} onChange={v => setParam('spacing', v)} />
                </Field>
                <Field label="Radius (blocks)" hint="Width, rolled per island, no effect below the spacing value">
                  <div className={styles.pairRow}>
                    <Input type="number" aria-label="Minimum radius" value={etherParams.minRadius}
                      min={etherMeta.ranges.radius[0]} max={etherMeta.ranges.radius[1]}
                      onChange={e => setParam('minRadius', Number(e.target.value))} />
                    <Input type="number" aria-label="Maximum radius" value={etherParams.maxRadius}
                      min={etherMeta.ranges.radius[0]} max={etherMeta.ranges.radius[1]}
                      onChange={e => setParam('maxRadius', Number(e.target.value))} />
                  </div>
                </Field>
                <Field label="Thickness (blocks)" hint="Height, independent of width">
                  <div className={styles.pairRow}>
                    <Input type="number" aria-label="Minimum thickness" value={etherParams.minThickness}
                      min={etherMeta.ranges.thickness[0]} max={etherMeta.ranges.thickness[1]}
                      onChange={e => setParam('minThickness', Number(e.target.value))} />
                    <Input type="number" aria-label="Maximum thickness" value={etherParams.maxThickness}
                      min={etherMeta.ranges.thickness[0]} max={etherMeta.ranges.thickness[1]}
                      onChange={e => setParam('maxThickness', Number(e.target.value))} />
                  </div>
                </Field>
                <Field label="Height band" hint="Y range island centres spawn in">
                  <div className={styles.pairRow}>
                    <Input type="number" aria-label="Lowest island centre" value={etherParams.minCenterY}
                      min={etherMeta.ranges.height[0]} max={etherMeta.ranges.height[1]}
                      onChange={e => setParam('minCenterY', Number(e.target.value))} />
                    <Input type="number" aria-label="Highest island centre" value={etherParams.maxCenterY}
                      min={etherMeta.ranges.height[0]} max={etherMeta.ranges.height[1]}
                      onChange={e => setParam('maxCenterY', Number(e.target.value))} />
                  </div>
                </Field>
                <Field label="Structures" hint="On, only inside terrain">
                  <div className={styles.pairRow}>
                    <Toggle checked={etherParams.structures} onChange={v => setParam('structures', v)} aria-label="Generate structures" />
                    <span className={styles.hint}>{etherParams.structures ? 'Generated' : 'None'}</span>
                  </div>
                </Field>
              </div>
            )}

            {isBiomeType && (
              <div className={styles.biomeBuilder}>
                {selectedBiomes.length > 0 && (
                  <div className={styles.chips}>
                    {selectedBiomes.map((b, i) => (
                      <span key={i} className={styles.chip}>{b.id}<span className={styles.chipW}>×{b.weight}</span><button type="button" className={styles.chipRemove} onClick={() => setSelectedBiomes(p => p.filter((_, j) => j !== i))}>×</button></span>
                    ))}
                  </div>
                )}
                <div className={styles.biomeRow}>
                  <div className={styles.combo} ref={biomeComboRef}>
                    <input className={styles.comboInput} placeholder={biomesLoaded ? `Search ${allBiomes.length} biomes…` : 'minecraft:plains'} value={biomeSearch} onChange={e => { setBiomeSearch(e.target.value); setBiomeOpen(true) }} onFocus={() => setBiomeOpen(true)} spellCheck={false} />
                    {biomeOpen && (
                      <div className={styles.dropdown}>
                        {!biomesLoaded && <div className={styles.dropMsg}>Loading…</div>}
                        {biomesLoaded && filteredBiomes.length === 0 && <div className={styles.dropMsg}>No matches</div>}
                        {filteredBiomes.slice(0, 200).map(id => <button key={id} type="button" className={styles.dropItem} onMouseDown={e => { e.preventDefault(); selectBiome(id) }}>{id}</button>)}
                      </div>
                    )}
                  </div>
                  <input className={styles.weightInput} type="number" min="1" max="999" value={biomeWeight} onChange={e => setBiomeWeight(e.target.value)} title="Relative weight" />
                </div>
                {selectedBiomes.length === 0 && <div className={styles.hint}>No biomes selected. Uses all default biomes for this type.</div>}
              </div>
            )}
          </form>
        </SectionCard>

        {sky && (
          <SectionCard title="Sky Entry">
            <div className={styles.skyGrid}>
              <SwitchField label="Enabled" hint="glide up out of the overworld to reach the ether"
                checked={sky.enabled} onChange={v => saveSky({ enabled: v })} disabled={skySaving} />

              {sky.candidates.length === 0 ? (
                <div className={styles.hint}>Create a dimension of type ether to give the sky somewhere to lead</div>
              ) : (
                <>
                  <Field label="Linked ether" hint="the dim a climb leads into">
                    <Select value={sky.override ?? ''} disabled={!sky.enabled || skySaving}
                      onChange={e => saveSky({ dimId: e.target.value })} aria-label="Linked ether dimension">
                      <option value="">{sky.resolved ? `First created (${sky.resolved})` : 'First created'}</option>
                      {sky.candidates.map(id => <option key={id} value={id}>{id}</option>)}
                    </Select>
                  </Field>

                  <Field label="Crossing height" hint={`0 uses the fall-out height (Y ${sky.dropY}) plus 20`}
                    aside={`Y ${sky.resolvedThresholdY}`}>
                    <Input type="number" value={skyYInput} disabled={!sky.enabled || skySaving}
                      onChange={e => setSkyYInput(e.target.value)}
                      onBlur={() => { const y = Number(skyYInput); if (y !== sky.thresholdY) saveSky({ thresholdY: y }) }}
                      onKeyDown={e => { if (e.key === 'Enter') e.currentTarget.blur() }}
                      aria-label="Crossing height" />
                  </Field>
                </>
              )}
            </div>
          </SectionCard>
        )}

        <ErrorBanner>{error}</ErrorBanner>

        {dims.length === 0 ? (
          <EmptyState icon={<IconPortal size={30} />} label="No custom dimensions" hint="Create one above, then restart before building in it" />
        ) : (
          <div className={styles.grid}>
            {dims.map(dim => (
              <div key={dim.id} className={styles.card}>
                <div className={styles.cardTop}>
                  <span className={styles.dimId}>{dim.id}</span>
                  <Menu trigger={<Btn size="sm" aria-label="Actions">⋯</Btn>}>
                    <MenuItem onSelect={() => { setTpEditing(dim.id); setTpPlayer('') }}>Teleport Player</MenuItem>
                    <MenuItem onSelect={() => { setPortalEditing(dim.id); setPortalInput(dim.portalBlock ?? '') }}>Set Portal Block</MenuItem>
                    <MenuItem tone="danger" onSelect={() => setConfirmDel(dim.id)}>Delete</MenuItem>
                  </Menu>
                </div>
                <div className={styles.tags}>
                  <Badge variant="info">{dim.generatorType}</Badge>
                  {dim.portalBlock ? <Badge variant="ok">portal: {dim.portalBlock.split(':').pop()}</Badge> : <Badge>no portal</Badge>}
                  {dim.sky && <Badge variant="ok">sky</Badge>}
                </div>
                {dim.generatorConfig && <span className={styles.config} title={dim.generatorConfig}>{dim.generatorConfig.length > 60 ? dim.generatorConfig.slice(0, 58) + '…' : dim.generatorConfig}</span>}

                {tpEditing === dim.id && (
                  <div className={styles.portalEdit}>
                    <Select value={tpPlayer} onChange={e => setTpPlayer(e.target.value)} aria-label="Player to teleport">
                      <option value="">{players.length ? 'Select player…' : 'Nobody online'}</option>
                      {players.map(p => <option key={p.uuid} value={p.name}>{p.name}</option>)}
                    </Select>
                    <Btn size="sm" variant="ok" onClick={() => handleTeleport(dim.id)} disabled={tpSending || !tpPlayer}>{tpSending ? '…' : 'Go'}</Btn>
                    <Btn size="sm" variant="ghost" onClick={() => setTpEditing(null)}>Cancel</Btn>
                  </div>
                )}

                {portalEditing === dim.id && (
                  <div className={styles.portalEdit}>
                    <input className={`${styles.comboInput} ${!portalIsKnown ? styles.invalid : ''}`} list="block-datalist" placeholder="minecraft:glowstone" value={portalInput} onChange={e => setPortalInput(e.target.value)} onKeyDown={e => { if (e.key === 'Enter') handleSetPortal(dim.id) }} autoFocus spellCheck={false} />
                    <datalist id="block-datalist">{blockSuggestions.map(id => <option key={id} value={id} />)}</datalist>
                    <Btn size="sm" variant="ok" onClick={() => handleSetPortal(dim.id)} disabled={portalSaving || !portalInput.trim()}>{portalSaving ? '…' : 'Set'}</Btn>
                    <Btn size="sm" variant="ghost" onClick={() => { setPortalEditing(null); setPortalInput('') }}>Cancel</Btn>
                    {!portalIsKnown && <span className={styles.warn}>Unknown block</span>}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}

        <div className={styles.footHint}>
          Portal: build a nether-portal-shaped frame (2 to 21 wide, 3 to 21 tall) with the portal block, then right-click a frame block with a water bucket.
        </div>
      </div>

      <ConfirmDialog open={!!confirmDel} onOpenChange={o => !o && setConfirmDel(null)}
        title={confirmDel ? `Delete dimension "${confirmDel}"?` : ''}
        description="The dimension and its world data are removed. This cannot be undone."
        confirmLabel="Delete" tone="danger" onConfirm={handleDelete} />
    </div>
  )
}
