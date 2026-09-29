import { useQuery } from "@tanstack/react-query";
import {
  agilityIcon, attackIcon, constructionIcon, cookingIcon, craftingIcon, defenceIcon, farmingIcon,
  firemakingIcon, fishingIcon, fletchingIcon, herbloreIcon, hitpointsIcon, hunterIcon, magicIcon,
  miningIcon, prayerIcon, rangedIcon, runecraftIcon, slayerIcon, smithingIcon, strengthIcon,
  thievingIcon, toDataUrl, woodcuttingIcon, questListIcon,
} from "@dava96/osrs-icons";
import { useEffect, useMemo, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { findItems, lookupItems } from "../api/items";
import { validateAccount } from "../domain/accountValidation";
import type { AccountBuild } from "../domain/contracts";

const spiritTrees = [
  ["FARMING_GUILD", "Farming Guild"], ["PORT_SARIM", "Port Sarim"],
  ["ETCETERIA", "Etceteria"], ["BRIMHAVEN", "Brimhaven"], ["HOSIDIUS", "Hosidius"],
] as const;
const pohLocations = ["Rimmington", "Taverley", "Pollnivneach", "Rellekka", "Brimhaven", "Yanille", "Prifddinas", "Hosidius", "Aldarin"];
const portals = [
  "Annakarl Portal", "Arceuus Library Portal", "Ardougne Portal", "Barbarian Outpost Portal", "Barrows Portal",
  "Battlefront Portal", "Camelot Portal", "Carrallanger Portal", "Catherby Portal", "Cemetery Portal",
  "Civitas illa Fortis Portal", "Dareeyak Portal", "Draynor Manor Portal", "Falador Portal",
  "Fenkenstrain's Castle Portal", "Fishing Guild Portal", "Ghorrock Portal", "Harmony Island Portal",
  "Ice Plateau Portal", "Kharyrll Portal", "Kourend Portal", "Lassar Portal", "Lumbridge Portal",
  "Lunar Isle Portal", "Marim Portal", "Mind Altar Portal", "Ourania Portal", "Paddewwa Portal",
  "Port Khazard Portal", "Respawn Portal (Lumbridge)", "Respawn Portal (Falador)", "Respawn Portal (Camelot)",
  "Respawn Portal (Edgeville)", "Respawn Portal (Prifddinas)", "Respawn Portal (Ferox Enclave)",
  "Respawn Portal (Kourend Castle)", "Respawn Portal (Civitas illa Fortis)", "Salve Graveyard Portal",
  "Senntisten Portal", "Trollheim Portal", "Troll Stronghold Portal", "Varrock Portal",
  "Waterbirth Island Portal", "Watchtower Portal", "Weiss Portal", "West Ardougne Portal",
];
const diaryTiers = ["NoDiary", "Easy", "Medium", "Hard", "Elite"] as const;
const itemFields = ["inventory", "equipment", "runePouch", "bank"] as const;
const pohFixtures = [
  ["fairyRing", "Fairy ring"], ["spiritTree", "Spirit tree"], ["obelisk", "Wilderness obelisk"],
  ["mountedGlory", "Mounted glory"], ["mountedXerics", "Xeric's talisman"],
  ["mountedDigsite", "Digsite pendant"], ["mountedMythical", "Mythical cape"],
] as const;
type ItemField = typeof itemFields[number];
const skillIcons: Record<string, string> = toDataUrl({
  Agility: agilityIcon, Attack: attackIcon, Construction: constructionIcon, Cooking: cookingIcon,
  Crafting: craftingIcon, Defence: defenceIcon, Farming: farmingIcon, Firemaking: firemakingIcon,
  Fishing: fishingIcon, Fletching: fletchingIcon, Herblore: herbloreIcon, Hitpoints: hitpointsIcon,
  Hunter: hunterIcon, Magic: magicIcon, Mining: miningIcon, Prayer: prayerIcon, Ranged: rangedIcon,
  Runecraft: runecraftIcon, Slayer: slayerIcon, Smithing: smithingIcon, Strength: strengthIcon,
  Thieving: thievingIcon, Woodcutting: woodcuttingIcon,
});
const quantityFormatter = new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 });

function ItemCollectionEditor({ title, values, names, onChange, open = false }: {
  title: string;
  values: Record<string, number>;
  names: Map<string, string>;
  onChange(value: Record<string, number>): void;
  open?: boolean;
}) {
  const [expanded, setExpanded] = useState(open);
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<string>();
  const results = useQuery({
    queryKey: ["items", search], queryFn: () => findItems(search), enabled: search.trim().length >= 2,
  });
  const options = results.data || [];
  const add = (key: string) => {
    onChange({ ...values, [key]: values[key] || 1 });
    setSelected(key);
    setSearch("");
  };
  return <details className="item-collection" open={expanded} onToggle={event => setExpanded(event.currentTarget.open)}>
    <summary><span className="item-heading"><strong>{title}</strong><small>{Object.keys(values).length ? `${Object.keys(values).length} configured` : "No items"}</small></span></summary>
    <div className="item-search">
      <label><span className="sr-only">{`Find ${title.toLowerCase()} item`}</span><input type="search" value={search}
        placeholder={`Search items to add to ${title.toLowerCase()}`} onChange={event => setSearch(event.target.value)} /></label>
      {search.trim().length >= 2 && <div className="item-results" aria-label="Item search results">
        {results.isFetching && <p>Searching…</p>}
        {!results.isFetching && options.slice(0, 8).map(item => <button type="button" key={item.key} onClick={() => add(item.key)}>
          <img src={itemSprite(item.key)} alt="" /><span>{item.name}</span>{values[item.key] && <small>Added</small>}
        </button>)}
        {!results.isFetching && !options.length && <p>No matching items</p>}
      </div>}
    </div>
    {!!Object.keys(values).length && <div className="item-grid" aria-label={`${title} items`}>{Object.entries(values).map(([key, quantity]) => {
      const name = names.get(key) || `Item ${key}`;
      return <button type="button" className={selected === key ? "selected" : ""} key={key} title={name}
        aria-label={`${name}, quantity ${quantity}`} onClick={() => setSelected(key)}>
        <img src={itemSprite(key)} alt="" /><span>{quantity > 1 ? quantityFormatter.format(quantity) : ""}</span>
      </button>;
    })}</div>}
    {selected && values[selected] && <div className="item-editor">
      <strong>{names.get(selected) || `Item ${selected}`}</strong>
      <label><span className="sr-only">{names.get(selected) || selected} quantity</span><input type="number" min="1" max="2147483647" value={values[selected]}
        onChange={event => onChange({ ...values, [selected]: Math.max(1, Number(event.target.value)) })} /></label>
      <button type="button" aria-label={`Remove ${names.get(selected) || selected}`} onClick={() => {
        const next = { ...values }; delete next[selected]; onChange(next); setSelected(undefined);
      }}>Remove</button>
    </div>}
  </details>;
}

export function AccountEditor({ account, quests, open, onClose, onSave }: {
  account: AccountBuild;
  quests: string[];
  open: boolean;
  onClose(): void;
  onSave(account: AccountBuild): void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const importInput = useRef<HTMLInputElement>(null);
  const [questFilter, setQuestFilter] = useState("");
  const [portalFilter, setPortalFilter] = useState("");
  const [items, setItems] = useState<Record<ItemField, Record<string, number>>>(() => pickItems(account));
  const [fileError, setFileError] = useState("");
  const { register, reset, setValue, getValues, watch, handleSubmit, formState: { errors } } = useForm<AccountBuild>({ defaultValues: account });
  const skills = useMemo(() => Object.keys(account.levels).filter(name => name !== "Total" && name !== "Quest").sort(), [account]);
  const visibleQuests = quests.filter(quest => quest.toLowerCase().includes(questFilter.toLowerCase()));
  const itemKeys = useMemo(() => Array.from(new Set(itemFields.flatMap(field => Object.keys(items[field])))).sort(), [items]);
  const itemNamesQuery = useQuery({ queryKey: ["item-names", itemKeys], queryFn: () => lookupItems(itemKeys), enabled: itemKeys.length > 0 });
  const itemNames = useMemo(() => new Map((itemNamesQuery.data || []).map(item => [item.key, item.name])), [itemNamesQuery.data]);
  const portalMode = watch("poh.portals.mode");
  const selectedPortals = watch("poh.portals.destinations") || [];
  const completedQuests = watch("completedQuests") || [];
  const cooldown = watch("runtime.minigameTeleport.state");
  const visiblePortals = portals.filter(portal => portal.toLowerCase().includes(portalFilter.toLowerCase()));

  useEffect(() => { reset(account); setItems(pickItems(account)); setFileError(""); }, [account, reset]);
  useEffect(() => {
    if (open && !dialog.current?.open) dialog.current?.showModal();
    if (!open && dialog.current?.open) dialog.current.close();
  }, [open]);

  const build = (value: AccountBuild): AccountBuild => validateAccount({
    ...value, ...items, id: "custom", name: value.name.trim(),
    runtime: {
      ...value.runtime,
      minigameTeleport: value.runtime.minigameTeleport.state === "usedAt"
        ? { state: "usedAt", minutes: Number(value.runtime.minigameTeleport.minutes) }
        : { state: "ready" },
    },
  });
  const exportAccount = () => {
    try {
      const blob = new Blob([JSON.stringify(build(getValues()), null, 2) + "\n"], { type: "application/json" });
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob); link.download = "osrs-account-build.json"; link.click();
      URL.revokeObjectURL(link.href); setFileError("");
    } catch (error) { setFileError(error instanceof Error ? error.message : "Invalid account build"); }
  };
  const importAccount = async (file?: File) => {
    if (!file) return;
    try {
      const imported = validateAccount(JSON.parse(await file.text()));
      reset(imported); setItems(pickItems(imported)); setFileError("");
    } catch (error) { setFileError(error instanceof Error ? error.message : "Invalid account file"); }
  };

  return <dialog ref={dialog} className="account-dialog" onClose={onClose}>
    <form method="dialog" className="account-editor" onSubmit={handleSubmit(value => {
      try { onSave(build(value)); } catch (error) { setFileError(error instanceof Error ? error.message : "Invalid account build"); }
    })}>
      <header>
        <div><p className="eyebrow">Routing profile</p><h2>Configure {account.name}</h2><p className="editor-intro">Only choices that can change a route are included.</p></div>
        <button type="button" className="close-button" aria-label="Close account editor" onClick={onClose}>×</button>
      </header>

      <div className="editor-meta">
        <label className="editor-name">Build name
          <input {...register("name", { required: "Enter a build name", validate: value => value.trim().length > 0 || "Enter a build name" })} />
          {errors.name && <span role="alert">{errors.name.message}</span>}
        </label>
        <div className="editor-file-actions">
          <button type="button" className="secondary-button" onClick={() => importInput.current?.click()}>Import</button>
          <button type="button" className="secondary-button" onClick={exportAccount}>Export</button>
          <input ref={importInput} className="sr-only" type="file" accept="application/json,.json" onChange={event => void importAccount(event.target.files?.[0])} />
        </div>
      </div>
      {fileError && <p className="editor-error" role="alert">{fileError}</p>}

      <section><div className="section-heading"><div><h3>Levels</h3><p>Used for shortcuts, spells and transport requirements.</p></div></div>
        <div className="skill-grid">{skills.map(skill => <label className="skill-card" key={skill} title={skill}>
        <img src={skillIcons[skill] || `https://oldschool.runescape.wiki/images/${skill}_icon.png`} alt="" /><span className="sr-only">{skill}</span>
        <input type="number" min="1" max="99" {...register(`levels.${skill}`, { valueAsNumber: true, min: 1, max: 99 })} />
      </label>)}<label className="skill-card" title="Quest points"><img src={toDataUrl(questListIcon)} alt="" /><span className="sr-only">Quest points</span><input type="number" min="0" max="32767" defaultValue={account.levels.Quest || 0}
        {...register("levels.Quest", { valueAsNumber: true, min: 0, max: 32767 })} /></label></div></section>

      <section><div className="section-heading"><div><h3>Items</h3><p>Search by name, then set the quantity available in each container.</p></div></div>{itemFields.map(field => <ItemCollectionEditor key={field}
        title={{ inventory: "Inventory", equipment: "Equipment", runePouch: "Rune pouch", bank: "Bank" }[field]}
        values={items[field]} names={itemNames} open={field === "inventory"}
        onChange={value => setItems(current => ({ ...current, [field]: value }))} />)}</section>

      <section><div className="section-heading"><div><h3>Achievement diaries</h3><p>Choose the highest completed tier for each region.</p></div></div>
        <div className="diary-grid">{Object.keys(account.diaries).sort().map(diary => <fieldset className="diary-card" key={diary}><legend>{splitName(diary)}</legend>
          <div className="tier-selector">{diaryTiers.map(tier => <label key={tier}>
            <input type="radio" value={tier} {...register(`diaries.${diary}`)} /><span>{tier === "NoDiary" ? "None" : tier}</span>
          </label>)}</div>
        </fieldset>)}</div></section>

      <section><div className="section-heading"><div><h3>Travel unlocks</h3><p>Permanent unlocks available to this account.</p></div></div>
        <div className="selection-grid"><label className="select-card"><input type="checkbox" {...register("fairyRingsUnlocked")} /><span><strong>Fairy rings</strong><small>Use the global fairy ring network</small></span></label></div>
        <h4>Planted spirit trees</h4><div className="selection-grid">{spiritTrees.map(([value, label]) => <label className="select-card" key={value}>
          <input type="checkbox" value={value} {...register("plantedSpiritTrees")} /><span><strong>{label}</strong><small>Spirit tree patch</small></span>
        </label>)}</div>
      </section>

      <section><div className="section-heading"><div><h3>Player-owned house</h3><p>Rooms and fixtures that can be chained into a route.</p></div></div>
        <div className="two-column"><label className="field-label">House location<select {...register("poh.location")}>{pohLocations.map(location => <option key={location}>{location}</option>)}</select></label>
          <fieldset className="choice-group"><legend>Jewellery box</legend><div className="segmented-control">
            {[['NoJewelleryBox', 'None'], ['FancyJewelleryBox', 'Fancy'], ['OrnateJewelleryBox', 'Ornate']].map(([value, label]) => <label key={value}><input type="radio" value={value} {...register("poh.jewelleryBox")} /><span>{label}</span></label>)}
          </div></fieldset></div>
        <h4>Fixtures</h4><div className="selection-grid">
          {pohFixtures.map(([name, label]) => <label className="select-card compact" key={name}>
            <input type="checkbox" {...register(`poh.${name}`)} /><span><strong>{label}</strong></span>
          </label>)}
        </div>
        <div className="portal-heading"><fieldset className="choice-group"><legend>Portal nexus</legend><div className="segmented-control">
          <label><input type="radio" value="selected" {...register("poh.portals.mode")} /><span>Choose portals</span></label>
          <label><input type="radio" value="all" {...register("poh.portals.mode")} /><span>All portals</span></label>
        </div></fieldset>{portalMode === "selected" && <span>{selectedPortals.length} selected</span>}</div>
        {portalMode === "selected" && <div className="portal-picker"><input type="search" value={portalFilter} onChange={event => setPortalFilter(event.target.value)} placeholder="Filter portal destinations" aria-label="Filter portal destinations" />
          <fieldset className="portal-list"><legend className="sr-only">Portal destinations</legend>{visiblePortals.map(portal => <label className="check-pill" key={portal}>
            <input type="checkbox" value={portal} {...register("poh.portals.destinations")} /><span>{portal.replace(" Portal", "")}</span>
          </label>)}</fieldset></div>}
      </section>

      <section><div className="section-heading"><div><h3>Current state</h3><p>Temporary state that affects teleports right now.</p></div></div>
        <fieldset className="choice-group"><legend>Active spellbook</legend><div className="segmented-control four">
          {["Standard", "Ancient", "Lunar", "Arceuus"].map(value => <label key={value}><input type="radio" value={value} {...register("runtime.spellbook")} /><span>{value}</span></label>)}
        </div></fieldset>
        <fieldset className="choice-group runtime-choice"><legend>Minigame teleport</legend><div className="segmented-control">
          <label><input type="radio" value="ready" {...register("runtime.minigameTeleport.state")} /><span>Ready</span></label>
          <label><input type="radio" value="usedAt" {...register("runtime.minigameTeleport.state")} /><span>On cooldown</span></label>
        </div></fieldset>
        {cooldown === "usedAt" && <label className="field-label cooldown-field">Used at game minute<input type="number" min="0" {...register("runtime.minigameTeleport.minutes", { valueAsNumber: true, required: true, min: 0 })} /></label>}</section>

      <section><div className="section-heading"><div><h3>Completed quests</h3><p>{completedQuests.length} of {quests.length} marked complete.</p></div>
        <div className="list-actions"><button type="button" onClick={() => setValue("completedQuests", Array.from(new Set([...completedQuests, ...visibleQuests])), { shouldDirty: true })}>Select visible</button>
          <button type="button" onClick={() => setValue("completedQuests", completedQuests.filter(quest => !visibleQuests.includes(quest)), { shouldDirty: true })}>Clear visible</button></div></div>
        <input className="list-filter" type="search" value={questFilter} onChange={event => setQuestFilter(event.target.value)} placeholder="Filter quests" aria-label="Filter quests" />
        <fieldset className="quest-list"><legend className="sr-only">Completed quests</legend>{visibleQuests.map(quest => <label className="check-pill" key={quest}>
          <input type="checkbox" value={quest} {...register("completedQuests")} /><span>{quest}</span>
        </label>)}</fieldset>
      </section>

      <footer><button type="button" className="secondary-button" onClick={onClose}>Cancel</button><button type="submit" className="primary-button">Save custom build</button></footer>
    </form>
  </dialog>;
}

function pickItems(account: AccountBuild): Record<ItemField, Record<string, number>> {
  return { inventory: { ...account.inventory }, equipment: { ...account.equipment }, runePouch: { ...account.runePouch }, bank: { ...account.bank } };
}

function splitName(value: string): string {
  return value.replace(/([a-z])([A-Z])/g, "$1 $2");
}

function itemSprite(key: string): string {
  return `https://chisel.weirdgloop.org/static/img/osrs-sprite/${key}.png`;
}
