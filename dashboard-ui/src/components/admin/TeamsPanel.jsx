import { useState, useEffect, useCallback, useRef } from 'react'
import styles from './TeamsPanel.module.css'

function authHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

const COLORS = [
  { value: 'reset',        label: 'None',          hex: '#AAAAAA' },
  { value: 'black',        label: 'Black',         hex: '#000000' },
  { value: 'dark_blue',    label: 'Dark Blue',     hex: '#0000AA' },
  { value: 'dark_green',   label: 'Dark Green',    hex: '#00AA00' },
  { value: 'dark_aqua',    label: 'Dark Aqua',     hex: '#00AAAA' },
  { value: 'dark_red',     label: 'Dark Red',      hex: '#AA0000' },
  { value: 'dark_purple',  label: 'Dark Purple',   hex: '#AA00AA' },
  { value: 'gold',         label: 'Gold',          hex: '#FFAA00' },
  { value: 'gray',         label: 'Gray',          hex: '#AAAAAA' },
  { value: 'dark_gray',    label: 'Dark Gray',     hex: '#555555' },
  { value: 'blue',         label: 'Blue',          hex: '#5555FF' },
  { value: 'green',        label: 'Green',         hex: '#55FF55' },
  { value: 'aqua',         label: 'Aqua',          hex: '#55FFFF' },
  { value: 'red',          label: 'Red',           hex: '#FF5555' },
  { value: 'light_purple', label: 'Light Purple',  hex: '#FF55FF' },
  { value: 'yellow',       label: 'Yellow',        hex: '#FFFF55' },
  { value: 'white',        label: 'White',         hex: '#FFFFFF' },
]

const VISIBILITY_OPTIONS = [
  { value: 'always',            label: 'Always' },
  { value: 'never',             label: 'Never' },
  { value: 'hideForOtherTeams', label: 'Hide for other teams' },
  { value: 'hideForOwnTeam',    label: 'Hide for own team' },
]

const COLLISION_OPTIONS = [
  { value: 'always',         label: 'Always' },
  { value: 'never',          label: 'Never' },
  { value: 'pushOtherTeams', label: 'Push other teams' },
  { value: 'pushOwnTeam',    label: 'Push own team' },
]

function colorHex(name) {
  return COLORS.find(c => c.value === name)?.hex ?? '#AAAAAA'
}

export default function TeamsPanel({ token, onExpired }) {
  const [teams,       setTeams]       = useState([])
  const [loading,     setLoading]     = useState(true)
  const [error,       setError]       = useState(null)

  // Create form
  const [createName,  setCreateName]  = useState('')
  const [creating,    setCreating]    = useState(false)

  // Expand / edit
  const [expanded,    setExpanded]    = useState({})

  // Delete confirm
  const [confirmDel,  setConfirmDel]  = useState(null)
  const [deleting,    setDeleting]    = useState(false)

  // Add player
  const [addInputs,   setAddInputs]   = useState({})
  const [addDropdown,  setAddDropdown]  = useState(null) // team name or null
  const [allPlayers,   setAllPlayers]   = useState([])
  const [playersLoaded, setPlayersLoaded] = useState(false)
  const addComboRef = useRef(null)

  // Search
  const [search,      setSearch]      = useState('')

  // Member search per team
  const [memberSearch, setMemberSearch] = useState({})

  // Close player dropdown on outside click
  useEffect(() => {
    function onMouseDown(e) {
      if (addComboRef.current && !addComboRef.current.contains(e.target))
        setAddDropdown(null)
    }
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

  const fetchTeams = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await fetch('/api/admin/teams', { headers: authHeaders(token) })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      setTeams(data)
    } catch {
      setError('Failed to load teams')
    } finally {
      setLoading(false)
    }
  }, [token]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { fetchTeams() }, [fetchTeams])

  // Lazy-load player names when any team is expanded
  useEffect(() => {
    if (Object.values(expanded).some(Boolean)) fetchPlayerNames()
  }, [expanded]) // eslint-disable-line react-hooks/exhaustive-deps

  async function handleCreate(e) {
    e.preventDefault()
    const name = createName.trim()
    if (!name) return
    setCreating(true)
    setError(null)
    try {
      const res = await fetch('/api/admin/teams/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
        body: JSON.stringify({ name }),
      })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      setCreateName('')
      await fetchTeams()
    } catch {
      setError('Create failed')
    } finally {
      setCreating(false)
    }
  }

  async function handleDelete(name) {
    setDeleting(true)
    setError(null)
    try {
      const res = await fetch('/api/admin/teams/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
        body: JSON.stringify({ name }),
      })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      setConfirmDel(null)
      setExpanded(prev => { const n = { ...prev }; delete n[name]; return n })
      await fetchTeams()
    } catch {
      setError('Delete failed')
    } finally {
      setDeleting(false)
    }
  }

  async function handleUpdate(teamName, field, value) {
    setError(null)
    try {
      const res = await fetch('/api/admin/teams/update', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
        body: JSON.stringify({ name: teamName, [field]: value }),
      })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      await fetchTeams()
    } catch {
      setError('Update failed')
    }
  }

  async function handleAddPlayer(teamName) {
    const player = (addInputs[teamName] || '').trim()
    if (!player) return
    setError(null)
    try {
      const res = await fetch('/api/admin/teams/addplayer', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
        body: JSON.stringify({ team: teamName, player }),
      })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      setAddInputs(prev => ({ ...prev, [teamName]: '' }))
      await fetchTeams()
    } catch {
      setError('Add player failed')
    }
  }

  async function handleRemovePlayer(player) {
    setError(null)
    try {
      const res = await fetch('/api/admin/teams/removeplayer', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
        body: JSON.stringify({ player }),
      })
      if (res.status === 401 || res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (data.error) { setError(data.error); return }
      await fetchTeams()
    } catch {
      setError('Remove player failed')
    }
  }

  function toggle(name) {
    setExpanded(prev => ({ ...prev, [name]: !prev[name] }))
  }

  const filtered = search
    ? teams.filter(t =>
        t.name.toLowerCase().includes(search.toLowerCase()) ||
        t.displayName.toLowerCase().includes(search.toLowerCase()) ||
        t.players.some(p => p.toLowerCase().includes(search.toLowerCase()))
      )
    : teams

  return (
    <div className={styles.wrap}>

      {/* Create form */}
      <div className={styles.createBox}>
        <div className={styles.sectionTitle}>Create Team</div>
        <form className={styles.createRow} onSubmit={handleCreate}>
          <input
            className={styles.input}
            placeholder="team_name"
            value={createName}
            onChange={e => setCreateName(e.target.value)}
            maxLength={16}
            required
            spellCheck={false}
          />
          <button type="submit" className={styles.btn} disabled={creating || !createName.trim()}>
            {creating ? '...' : 'Create'}
          </button>
        </form>

        {error && (
          <div className={styles.error}>
            {error}
            <button className={styles.dismiss} onClick={() => setError(null)}>x</button>
          </div>
        )}
      </div>

      {/* Toolbar */}
      <div className={styles.toolbar}>
        <span className={styles.count}>
          {loading ? 'Loading...' : `${teams.length} team${teams.length !== 1 ? 's' : ''}`}
        </span>
        <input
          className={styles.searchInput}
          placeholder="Search..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          spellCheck={false}
        />
        <button className={styles.refresh} onClick={fetchTeams} disabled={loading}>Refresh</button>
      </div>

      {/* Team list */}
      <div className={styles.list}>
        {!loading && filtered.length === 0 && (
          <div className={styles.empty}>
            {teams.length === 0 ? 'No teams. Create one above.' : 'No teams match your search.'}
          </div>
        )}
        {filtered.map(team => (
          <div key={team.name} className={styles.teamCard}>
            <div className={styles.teamHeader} onClick={() => toggle(team.name)}>
              <span className={styles.caret}>{expanded[team.name] ? '\u25BE' : '\u25B8'}</span>
              <span className={styles.colorDot} style={{ background: colorHex(team.color) }} />
              <span className={styles.teamName}>{team.name}</span>
              {team.displayName !== team.name && (
                <span className={styles.teamDisplay}>{team.displayName}</span>
              )}
              <span className={styles.memberCount}>{team.players.length} player{team.players.length !== 1 ? 's' : ''}</span>
              {!team.friendlyFire && <span className={styles.pvpBadge}>PvP off</span>}
            </div>

            {expanded[team.name] && (
              <div className={styles.teamBody}>
                {/* Settings */}
                <div className={styles.settingsGrid}>
                  <label className={styles.fieldLabel}>Display Name</label>
                  <input
                    className={styles.input}
                    value={team.displayName}
                    onChange={e => {
                      const v = e.target.value
                      setTeams(prev => prev.map(t => t.name === team.name ? { ...t, displayName: v } : t))
                    }}
                    onBlur={e => handleUpdate(team.name, 'displayName', e.target.value)}
                    maxLength={128}
                    spellCheck={false}
                  />

                  <label className={styles.fieldLabel}>Color</label>
                  <select
                    className={styles.select}
                    value={team.color}
                    onChange={e => handleUpdate(team.name, 'color', e.target.value)}
                  >
                    {COLORS.map(c => (
                      <option key={c.value} value={c.value}>{c.label}</option>
                    ))}
                  </select>

                  <label className={styles.fieldLabel}>Friendly Fire</label>
                  <button
                    className={team.friendlyFire ? styles.toggleOn : styles.toggleOff}
                    onClick={() => handleUpdate(team.name, 'friendlyFire', !team.friendlyFire)}
                  >
                    {team.friendlyFire ? 'ON' : 'OFF'}
                  </button>

                  <label className={styles.fieldLabel}>Invis Teammates</label>
                  <button
                    className={team.seeFriendlyInvisibles ? styles.toggleOn : styles.toggleOff}
                    onClick={() => handleUpdate(team.name, 'seeFriendlyInvisibles', !team.seeFriendlyInvisibles)}
                    title="When ON, teammates appear as translucent ghosts while invisible. When OFF, invisible teammates are fully invisible to everyone."
                  >
                    {team.seeFriendlyInvisibles ? 'VISIBLE' : 'HIDDEN'}
                  </button>

                  <label className={styles.fieldLabel}>Nametags</label>
                  <select
                    className={styles.select}
                    value={team.nameTagVisibility}
                    onChange={e => handleUpdate(team.name, 'nameTagVisibility', e.target.value)}
                  >
                    {VISIBILITY_OPTIONS.map(o => (
                      <option key={o.value} value={o.value}>{o.label}</option>
                    ))}
                  </select>

                  <label className={styles.fieldLabel}>Death Msgs</label>
                  <select
                    className={styles.select}
                    value={team.deathMessageVisibility}
                    onChange={e => handleUpdate(team.name, 'deathMessageVisibility', e.target.value)}
                  >
                    {VISIBILITY_OPTIONS.map(o => (
                      <option key={o.value} value={o.value}>{o.label}</option>
                    ))}
                  </select>

                  <label className={styles.fieldLabel}>Collision</label>
                  <select
                    className={styles.select}
                    value={team.collisionRule}
                    onChange={e => handleUpdate(team.name, 'collisionRule', e.target.value)}
                  >
                    {COLLISION_OPTIONS.map(o => (
                      <option key={o.value} value={o.value}>{o.label}</option>
                    ))}
                  </select>

                  <label className={styles.fieldLabel}>Prefix</label>
                  <div className={styles.prefixRow}>
                    <select
                      className={styles.colorSelect}
                      value={team.prefixColor || 'reset'}
                      onChange={e => handleUpdate(team.name, 'prefixColor', e.target.value)}
                      style={{ color: colorHex(team.prefixColor || 'reset') }}
                    >
                      {COLORS.map(c => (
                        <option key={c.value} value={c.value} style={{ color: c.hex }}>{c.label}</option>
                      ))}
                    </select>
                    <input
                      className={styles.input}
                      value={team.prefix}
                      onChange={e => {
                        const v = e.target.value
                        setTeams(prev => prev.map(t => t.name === team.name ? { ...t, prefix: v } : t))
                      }}
                      onBlur={e => handleUpdate(team.name, 'prefix', e.target.value)}
                      maxLength={64}
                      placeholder="none"
                      spellCheck={false}
                    />
                  </div>

                  <label className={styles.fieldLabel}>Suffix</label>
                  <div className={styles.prefixRow}>
                    <select
                      className={styles.colorSelect}
                      value={team.suffixColor || 'reset'}
                      onChange={e => handleUpdate(team.name, 'suffixColor', e.target.value)}
                      style={{ color: colorHex(team.suffixColor || 'reset') }}
                    >
                      {COLORS.map(c => (
                        <option key={c.value} value={c.value} style={{ color: c.hex }}>{c.label}</option>
                      ))}
                    </select>
                    <input
                      className={styles.input}
                      value={team.suffix}
                      onChange={e => {
                        const v = e.target.value
                        setTeams(prev => prev.map(t => t.name === team.name ? { ...t, suffix: v } : t))
                      }}
                      onBlur={e => handleUpdate(team.name, 'suffix', e.target.value)}
                      maxLength={64}
                      placeholder="none"
                      spellCheck={false}
                    />
                  </div>
                </div>

                {/* Members */}
                <div className={styles.membersSection}>
                  <div className={styles.membersHeader}>
                    <span className={styles.membersTitle}>Members ({team.players.length})</span>
                    {team.players.length > 10 && (
                      <input
                        className={styles.memberSearchInput}
                        placeholder="Filter members..."
                        value={memberSearch[team.name] || ''}
                        onChange={e => setMemberSearch(prev => ({ ...prev, [team.name]: e.target.value }))}
                        spellCheck={false}
                      />
                    )}
                  </div>

                  {(() => {
                    const inputVal = (addInputs[team.name] || '').trim()
                    const inputLower = inputVal.toLowerCase()
                    const already = new Set(team.players.map(p => p.toLowerCase()))
                    const isValid = playersLoaded && inputVal
                      && allPlayers.some(n => n.toLowerCase() === inputLower)
                      && !already.has(inputLower)
                    const isAlreadyOn = playersLoaded && inputVal && already.has(inputLower)
                    const otherTeam = isValid
                      ? teams.find(t => t.name !== team.name && t.players.some(p => p.toLowerCase() === inputLower))
                      : null
                    return (
                      <div className={styles.addPlayerRow} ref={addDropdown === team.name ? addComboRef : undefined}>
                        <div className={styles.playerCombo}>
                          <input
                            className={`${styles.input} ${inputVal && playersLoaded && !isValid && !isAlreadyOn ? styles.inputInvalid : ''}`}
                            placeholder={playersLoaded ? `Search ${allPlayers.length} players...` : 'Player name'}
                            value={addInputs[team.name] || ''}
                            onChange={e => {
                              setAddInputs(prev => ({ ...prev, [team.name]: e.target.value }))
                              setAddDropdown(team.name)
                            }}
                            onFocus={() => setAddDropdown(team.name)}
                            onKeyDown={e => {
                              if (e.key === 'Enter' && isValid) {
                                e.preventDefault()
                                handleAddPlayer(team.name)
                                setAddDropdown(null)
                              }
                            }}
                            spellCheck={false}
                          />
                          {addDropdown === team.name && inputVal && (
                            <div className={styles.playerDropdown}>
                              {(() => {
                                const matches = allPlayers
                                  .filter(n => n.toLowerCase().includes(inputLower) && !already.has(n.toLowerCase()))
                                  .slice(0, 50)
                                if (!playersLoaded) return <div className={styles.playerDropdownMsg}>Loading...</div>
                                if (!matches.length) return <div className={styles.playerDropdownMsg}>No matches</div>
                                return matches.map(n => (
                                  <button
                                    key={n}
                                    type="button"
                                    className={styles.playerDropdownItem}
                                    onMouseDown={e => {
                                      e.preventDefault()
                                      setAddInputs(prev => ({ ...prev, [team.name]: n }))
                                      setAddDropdown(null)
                                    }}
                                  >
                                    {n}
                                  </button>
                                ))
                              })()}
                            </div>
                          )}
                          {isAlreadyOn && <span className={styles.inputWarn}>Already on this team</span>}
                          {inputVal && playersLoaded && !isValid && !isAlreadyOn && (
                            <span className={styles.inputWarn}>Unknown player</span>
                          )}
                        </div>
                        <button
                          className={styles.btn}
                          onClick={() => { handleAddPlayer(team.name); setAddDropdown(null) }}
                          disabled={!isValid}
                        >
                          Add
                        </button>
                        {otherTeam && (
                          <span className={styles.inputMove}>On "{otherTeam.name}" — will be moved</span>
                        )}
                      </div>
                    )
                  })()}

                  <div className={styles.membersList}>
                    {(memberSearch[team.name]
                      ? team.players.filter(p => p.toLowerCase().includes(memberSearch[team.name].toLowerCase()))
                      : team.players
                    ).map(player => (
                      <div key={player} className={styles.memberRow}>
                        <span className={styles.memberName}>{player}</span>
                        <button
                          className={styles.memberRemove}
                          onClick={() => handleRemovePlayer(player)}
                        >
                          x
                        </button>
                      </div>
                    ))}
                    {team.players.length === 0 && (
                      <div className={styles.noMembers}>No members</div>
                    )}
                  </div>
                </div>

                {/* Delete */}
                <div className={styles.teamFooter}>
                  {confirmDel === team.name ? (
                    <span className={styles.confirmDel}>
                      <span className={styles.confirmLabel}>Delete team?</span>
                      <button
                        className={styles.btnDanger}
                        onClick={() => handleDelete(team.name)}
                        disabled={deleting}
                      >
                        {deleting ? '...' : 'Yes'}
                      </button>
                      <button className={styles.btnGhost} onClick={() => setConfirmDel(null)}>No</button>
                    </span>
                  ) : (
                    <button className={styles.btnDanger} onClick={() => setConfirmDel(team.name)}>
                      Delete Team
                    </button>
                  )}
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
