import { Field } from './Field'

/**
 * The details players are shown for a server, for both the register form and the edit dialog.
 * `onChange(key, value)` reports each edit. A key missing from `values` reads as blank, so a
 * caller starting from scratch can pass {} and a new field here needs no change at either one.
 */
export function ListingFields({ values, onChange }) {
  const value = (key) => values[key] ?? ''
  const set = (key) => (e) => onChange(key, e.target.value)
  return (
    <>
      <div className="form-row">
        <Field label="Address" hint="What players type to connect.">
          <input value={value('publicAddress')} onChange={set('publicAddress')} placeholder="play.example.com:25565" maxLength={100} />
        </Field>
        <Field label="Launcher">
          <input value={value('launcher')} onChange={set('launcher')} placeholder="CurseForge" maxLength={32} />
        </Field>
      </div>
      <div className="form-row">
        <Field label="Modpack">
          <input value={value('modpack')} onChange={set('modpack')} placeholder="All The Mods 10" maxLength={100} />
        </Field>
        <Field label="Modpack version" hint="The pack's own version, not the mod or Minecraft one.">
          <input value={value('modpackVersion')} onChange={set('modpackVersion')} placeholder="0.7.1" maxLength={32} />
        </Field>
      </div>
      <Field label="Modpack link" hint="Where players download it.">
        <input
          type="url"
          value={value('modpackUrl')}
          onChange={set('modpackUrl')}
          placeholder="https://www.curseforge.com/minecraft/modpacks/..."
          maxLength={300}
        />
      </Field>
      <Field label="Image" hint="The modpack's icon. Square works best — it's the thumbnail in Discord.">
        <input
          type="url"
          value={value('iconUrl')}
          onChange={set('iconUrl')}
          placeholder="https://example.com/pack-icon.png"
          maxLength={300}
        />
      </Field>
    </>
  )
}
