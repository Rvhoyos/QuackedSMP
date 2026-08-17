import { useState, useEffect, useCallback, useRef, useMemo } from 'react'
import { Toolbar, IconButton, Btn, Badge, Swatch, Toggle, SectionCard, Loading, EmptyState, ErrorBanner, ConfirmDialog, Field, Input, Select, useToast } from '../../ui'
import { IconSword } from './MinecraftIcons'
import styles from './TeamsPanel.module.css'

function authHeaders(token) { return token ? { Authorization: `Bearer ${token}` } : {} }

const COLORS = [
  { value: 'reset', label: 'None', hex: '#AAAAAA' }, { value: 'black', label: 'Black', hex: '#000000' },
  { value: 'dark_blue', label: 'Dark Blue', hex: '#0000AA' }, { value: 'dark_green', label: 'Dark Green', hex: '#00AA00' },
  { value: 'dark_aqua', label: 'Dark Aqua', hex: '#00AAAA' }, { value: 'dark_red', label: 'Dark Red', hex: '#AA0000' },
  { value: 'dark_purple', label: 'Dark Purple', hex: '#AA00AA' }, { value: 'gold', label: 'Gold', hex: '#FFAA00' },
  { value: 'gray', label: 'Gray', hex: '#AAAAAA' }, { value: 'dark_gray', label: 'Dark Gray', hex: '#555555' },
  { value: 'blue', label: 'Blue', hex: '#5555FF' }, { value: 'green', label: 'Green', hex: '#55FF55' },
  { value: 'aqua', label: 'Aqua', hex: '#55FFFF' }, { value: 'red', label: 'Red', hex: '#FF5555' },
  { value: 'light_purple', label: 'Light Purple', hex: '#FF55FF' }, { value: 'yellow', label: 'Yellow', hex: '#FFFF55' },
  { value: 'white', label: 'White', hex: '#FFFFFF' },
]
const VISIBILITY_OPTIONS = [
  { value: 'always', label: 'Always' }, { value: 'never', label: 'Never' },
  { value: 'hideForOtherTeams', label: 'Hide for other teams' }, { value: 'hideForOwnTeam', label: 'Hide for own team' },
]
const COLLISION_OPTIONS = [
  { value: 'always', label: 'Always' }, { value: 'never', label: 'Never' },
  { value: 'pushOtherTeams', label: 'Push other teams' }, { value: 'pushOwnTeam', label: 'Push own team' },
]
const colorHex = name => COLORS.find(c => c.value === name)?.hex ?? '#AAAAAA'

export default function TeamsPanel({ token, onExpired }) {
  const [teams, setTeams] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [createName, setCreateName] = useState('')
  const [creating, setCreating] = useState(false)
  const [expanded, setExpanded] = useState({})
  const [confirmDel, setConfirmDel] = useState(null)
  const [addInputs, setAddInputs] = useState({})
  const [addDropdown, setAddDropdown] = useState(null)
  const [allPlayers, setAllPlayers] = useState([])
  const [playersLoaded, setPlayersLoaded] = useState(false)
  const addComboRef = useRef(null)
  const [search, setSearch] = useState('')
  const [memberSearch, setMemberSearch] = useState({})
  const [autoAssign, setAutoAssign] = useState({})
  const [allDims, setAllDims] = useState([])
  const [dimsLoaded, setDimsLoaded] = useState(false)
  const toast = useToast()

  useEffect(() => {
    function onMouseDown(e) { if (addComboRef.current && !addComboRef.current.contains(e.target)) setAddDropdown(null) }
    document.addEventListener('mousedown', onMouseDown)
    return () => document.removeEventListener('mousedown', onMouseDown)
  }, [])

  const fetchPlayerNames = useCallback(async () => {
    if (playersLoaded) return
    try {
      const res = await fetch('/api/admin/playernames', { headers: authHeaders(token) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (Array.isArray(data)) { setAllPlayers(data); setPlayersLoaded(true) }
    } catch {}
  }, [playersLoaded, token, onExpired])

  const fetchDims = useCallback(async () => {
    if (dimsLoaded) return
    try {
      const res = await fetch('/api/admin/dims/all', { headers: authHeaders(token) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (Array.isArray(data)) { setAllDims(data); setDimsLoaded(true) }
    } catch {}
  }, [dimsLoaded, token, onExpired])

  const fetchAutoAssign = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/teams/autoassign', { headers: authHeaders(token) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (!data.error) setAutoAssign(data)
    } catch {}
  }, [token, onExpired])

  const fetchTeams = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const res = await fetch('/api/admin/teams', { headers: authHeaders(token) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      setTeams(data)
    } catch { setError('Failed to load teams') } finally { setLoading(false) }
  }, [token]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { fetchTeams() }, [fetchTeams])
  useEffect(() => {
    if (Object.values(expanded).some(Boolean)) { fetchPlayerNames(); fetchDims(); fetchAutoAssign() }
  }, [expanded]) // eslint-disable-line react-hooks/exhaustive-deps

  async function handleCreate(e) {
    e.preventDefault()
    const name = createName.trim()
    if (!name) return
    setCreating(true); setError(null)
    try {
      const res = await fetch('/api/admin/teams/create', { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders(token) }, body: JSON.stringify({ name }) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      setCreateName(''); toast(`Created ${name}`); await fetchTeams()
    } catch { setError('Create failed') } finally { setCreating(false) }
  }

  async function handleDelete() {
    const name = confirmDel
    try {
      const res = await fetch('/api/admin/teams/delete', { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders(token) }, body: JSON.stringify({ name }) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      setConfirmDel(null); setExpanded(p => { const n = { ...p }; delete n[name]; return n }); toast(`Deleted ${name}`); await fetchTeams()
    } catch { setError('Delete failed') }
  }

  async function handleUpdate(teamName, field, value) {
    setError(null)
    try {
      const res = await fetch('/api/admin/teams/update', { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders(token) }, body: JSON.stringify({ name: teamName, [field]: value }) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      await fetchTeams()
    } catch { setError('Update failed') }
  }

  async function handleAddPlayer(teamName) {
    const player = (addInputs[teamName] || '').trim()
    if (!player) return
    setError(null)
    try {
      const res = await fetch('/api/admin/teams/addplayer', { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders(token) }, body: JSON.stringify({ team: teamName, player }) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      setAddInputs(p => ({ ...p, [teamName]: '' })); await fetchTeams()
    } catch { setError('Add player failed') }
  }

  async function handleRemovePlayer(player) {
    setError(null)
    try {
      const res = await fetch('/api/admin/teams/removeplayer', { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders(token) }, body: JSON.stringify({ player }) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      await fetchTeams()
    } catch { setError('Remove player failed') }
  }

  async function handleAutoAssignUpdate(teamName, dimensions) {
    setError(null)
    try {
      const res = await fetch('/api/admin/teams/autoassign', { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders(token) }, body: JSON.stringify({ team: teamName, dimensions }) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      await fetchAutoAssign()
    } catch { setError('Auto-assign update failed') }
  }

  const dimToTeam = useMemo(() => {
    const map = {}
    for (const [team, dims] of Object.entries(autoAssign)) for (const dim of dims) map[dim] = team
    return map
  }, [autoAssign])

  function toggle(name) { setExpanded(p => ({ ...p, [name]: !p[name] })) }

  const filtered = search
    ? teams.filter(t => t.name.toLowerCase().includes(search.toLowerCase()) || t.displayName.toLowerCase().includes(search.toLowerCase()) || t.players.some(p => p.toLowerCase().includes(search.toLowerCase())))
    : teams

  if (loading && teams.length === 0) return <Loading label="Loading teams…" />

  return (
    <div className={styles.wrap}>
      <Toolbar title="Teams" count={`${teams.length}`}>
        <input className={styles.filter} placeholder="Search…" value={search} onChange={e => setSearch(e.target.value)} spellCheck={false} />
        <IconButton tip="Refresh" onClick={fetchTeams}>↻</IconButton>
      </Toolbar>

      <div className={styles.body}>
        <form className={styles.createBar} onSubmit={handleCreate}>
          <Input placeholder="new_team_name" value={createName} onChange={e => setCreateName(e.target.value)} maxLength={16} spellCheck={false} />
          <Btn variant="primary" type="submit" disabled={creating || !createName.trim()}>{creating ? '…' : 'Create Team'}</Btn>
        </form>

        <ErrorBanner>{error}</ErrorBanner>

        {!loading && filtered.length === 0 ? (
          <EmptyState icon={<IconSword size={30} />} label={teams.length === 0 ? 'No teams yet' : 'No teams match your search'} hint={teams.length === 0 ? 'Create one above to organize players with colors, prefixes, and PvP rules.' : undefined} />
        ) : (
          <div className={styles.list}>
            {filtered.map(team => (
              <div key={team.name} className={styles.teamCard}>
                <button className={styles.teamHeader} onClick={() => toggle(team.name)}>
                  <span className={styles.caret}>{expanded[team.name] ? '▾' : '▸'}</span>
                  <Swatch color={colorHex(team.color)} size={14} />
                  <span className={styles.teamName}>{team.name}</span>
                  {team.displayName !== team.name && <span className={styles.teamDisplay}>{team.displayName}</span>}
                  <span className={styles.spacer} />
                  {!team.friendlyFire && <Badge variant="danger">PvP off</Badge>}
                  <Badge>{team.players.length} {team.players.length === 1 ? 'player' : 'players'}</Badge>
                </button>

                {expanded[team.name] && (
                  <div className={styles.teamBody}>
                    <SectionCard title="Identity">
                      <div className={styles.fieldGrid}>
                        <Field label="Display Name"><Input value={team.displayName} onChange={e => { const v = e.target.value; setTeams(p => p.map(t => t.name === team.name ? { ...t, displayName: v } : t)) }} onBlur={e => handleUpdate(team.name, 'displayName', e.target.value)} maxLength={128} spellCheck={false} /></Field>
                        <Field label="Color"><Select value={team.color} onChange={e => handleUpdate(team.name, 'color', e.target.value)}>{COLORS.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}</Select></Field>
                        <Field label="Prefix">
                          <div className={styles.affixRow}>
                            <Select className={styles.affixColor} value={team.prefixColor || 'reset'} onChange={e => handleUpdate(team.name, 'prefixColor', e.target.value)} style={{ color: colorHex(team.prefixColor || 'reset') }}>{COLORS.map(c => <option key={c.value} value={c.value} style={{ color: c.hex }}>{c.label}</option>)}</Select>
                            <Input value={team.prefix} onChange={e => { const v = e.target.value; setTeams(p => p.map(t => t.name === team.name ? { ...t, prefix: v } : t)) }} onBlur={e => handleUpdate(team.name, 'prefix', e.target.value)} maxLength={64} placeholder="none" spellCheck={false} />
                          </div>
                        </Field>
                        <Field label="Suffix">
                          <div className={styles.affixRow}>
                            <Select className={styles.affixColor} value={team.suffixColor || 'reset'} onChange={e => handleUpdate(team.name, 'suffixColor', e.target.value)} style={{ color: colorHex(team.suffixColor || 'reset') }}>{COLORS.map(c => <option key={c.value} value={c.value} style={{ color: c.hex }}>{c.label}</option>)}</Select>
                            <Input value={team.suffix} onChange={e => { const v = e.target.value; setTeams(p => p.map(t => t.name === team.name ? { ...t, suffix: v } : t)) }} onBlur={e => handleUpdate(team.name, 'suffix', e.target.value)} maxLength={64} placeholder="none" spellCheck={false} />
                          </div>
                        </Field>
                      </div>
                    </SectionCard>

                    <SectionCard title="Behavior">
                      <div className={styles.behaviorGrid}>
                        <div className={styles.toggleRow}><span className={styles.bLabel}>Friendly Fire</span><Toggle checked={team.friendlyFire} onChange={v => handleUpdate(team.name, 'friendlyFire', v)} aria-label="Friendly fire" /></div>
                        <div className={styles.toggleRow}><span className={styles.bLabel}>See Invisible Teammates</span><Toggle checked={team.seeFriendlyInvisibles} onChange={v => handleUpdate(team.name, 'seeFriendlyInvisibles', v)} aria-label="See invisible teammates" /></div>
                        <Field label="Nametags"><Select value={team.nameTagVisibility} onChange={e => handleUpdate(team.name, 'nameTagVisibility', e.target.value)}>{VISIBILITY_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</Select></Field>
                        <Field label="Death Messages"><Select value={team.deathMessageVisibility} onChange={e => handleUpdate(team.name, 'deathMessageVisibility', e.target.value)}>{VISIBILITY_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</Select></Field>
                        <Field label="Collision"><Select value={team.collisionRule} onChange={e => handleUpdate(team.name, 'collisionRule', e.target.value)}>{COLLISION_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</Select></Field>
                      </div>
                    </SectionCard>

                    <SectionCard title={`Members (${team.players.length})`} actions={team.players.length > 10 ? <input className={styles.filter} placeholder="Filter…" value={memberSearch[team.name] || ''} onChange={e => setMemberSearch(p => ({ ...p, [team.name]: e.target.value }))} spellCheck={false} /> : null}>
                      {(() => {
                        const inputVal = (addInputs[team.name] || '').trim()
                        const inputLower = inputVal.toLowerCase()
                        const already = new Set(team.players.map(p => p.toLowerCase()))
                        const isValid = playersLoaded && inputVal && allPlayers.some(n => n.toLowerCase() === inputLower) && !already.has(inputLower)
                        const isAlreadyOn = playersLoaded && inputVal && already.has(inputLower)
                        const otherTeam = isValid ? teams.find(t => t.name !== team.name && t.players.some(p => p.toLowerCase() === inputLower)) : null
                        return (
                          <div className={styles.addRow} ref={addDropdown === team.name ? addComboRef : undefined}>
                            <div className={styles.combo}>
                              <input className={`${styles.comboInput} ${inputVal && playersLoaded && !isValid && !isAlreadyOn ? styles.invalid : ''}`}
                                placeholder={playersLoaded ? `Add from ${allPlayers.length} players…` : 'Player name'}
                                value={addInputs[team.name] || ''}
                                onChange={e => { setAddInputs(p => ({ ...p, [team.name]: e.target.value })); setAddDropdown(team.name) }}
                                onFocus={() => setAddDropdown(team.name)}
                                onKeyDown={e => { if (e.key === 'Enter' && isValid) { e.preventDefault(); handleAddPlayer(team.name); setAddDropdown(null) } }}
                                spellCheck={false} />
                              {addDropdown === team.name && inputVal && (
                                <div className={styles.dropdown}>
                                  {!playersLoaded ? <div className={styles.dropMsg}>Loading…</div> : (() => {
                                    const matches = allPlayers.filter(n => n.toLowerCase().includes(inputLower) && !already.has(n.toLowerCase())).slice(0, 50)
                                    if (!matches.length) return <div className={styles.dropMsg}>No matches</div>
                                    return matches.map(n => <button key={n} type="button" className={styles.dropItem} onMouseDown={e => { e.preventDefault(); setAddInputs(p => ({ ...p, [team.name]: n })); setAddDropdown(null) }}>{n}</button>)
                                  })()}
                                </div>
                              )}
                              {isAlreadyOn && <span className={styles.warn}>Already on this team</span>}
                              {inputVal && playersLoaded && !isValid && !isAlreadyOn && <span className={styles.warn}>Unknown player</span>}
                              {otherTeam && <span className={styles.move}>On "{otherTeam.name}" — will be moved</span>}
                            </div>
                            <Btn onClick={() => { handleAddPlayer(team.name); setAddDropdown(null) }} disabled={!isValid}>Add</Btn>
                          </div>
                        )
                      })()}
                      <div className={styles.members}>
                        {(memberSearch[team.name] ? team.players.filter(p => p.toLowerCase().includes(memberSearch[team.name].toLowerCase())) : team.players).map(player => (
                          <span key={player} className={styles.memberChip}>{player}<button className={styles.memberRemove} onClick={() => handleRemovePlayer(player)} aria-label={`Remove ${player}`}>×</button></span>
                        ))}
                        {team.players.length === 0 && <span className={styles.noMembers}>No members</span>}
                      </div>
                    </SectionCard>

                    <SectionCard title="Auto-Assign Dimensions" subtitle="Players entering these dims join this team automatically.">
                      <div className={styles.dimChips}>
                        {(autoAssign[team.name] || []).map(dim => (
                          <span key={dim} className={styles.dimChip}>{dim}<button className={styles.memberRemove} onClick={() => handleAutoAssignUpdate(team.name, (autoAssign[team.name] || []).filter(d => d !== dim))} aria-label={`Remove ${dim}`}>×</button></span>
                        ))}
                        {!(autoAssign[team.name] || []).length && <span className={styles.noMembers}>None assigned</span>}
                      </div>
                      {dimsLoaded && (
                        <Select value="" onChange={e => { const dim = e.target.value; if (dim) handleAutoAssignUpdate(team.name, [...(autoAssign[team.name] || []), dim]) }}>
                          <option value="">Add dimension…</option>
                          {allDims.filter(dim => !(autoAssign[team.name] || []).includes(dim)).map(dim => { const owner = dimToTeam[dim]; const taken = owner && owner !== team.name; return <option key={dim} value={dim} disabled={taken}>{dim}{taken ? ` (on ${owner})` : ''}</option> })}
                        </Select>
                      )}
                    </SectionCard>

                    <div className={styles.teamFooter}>
                      <Btn variant="danger" onClick={() => setConfirmDel(team.name)}>Delete Team</Btn>
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      <ConfirmDialog open={!!confirmDel} onOpenChange={o => !o && setConfirmDel(null)}
        title={confirmDel ? `Delete team "${confirmDel}"?` : ''}
        description="Members are removed from the team. This cannot be undone."
        confirmLabel="Delete Team" tone="danger" onConfirm={handleDelete} />
    </div>
  )
}
