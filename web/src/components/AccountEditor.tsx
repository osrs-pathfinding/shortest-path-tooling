import { useQuery } from "@tanstack/react-query";
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
type ItemField = typeof itemFields[number];

function ItemCollectionEditor({ title, values, names, onChange, open = false }: {
  title: string;
  values: Record<string, number>;
  names: Map<string, string>;
  onChange(value: Record<string, number>): void;
  open?: boolean;
}) {
  const [expanded, setExpanded] = useState(open);
  const [search, setSearch] = useState("");
  const results = useQuery({
    queryKey: ["items", search], queryFn: () => findItems(search), enabled: search.trim().length >= 2,
  });
  const options = results.data || [];
  const selected = options.find(item => item.name.toLowerCase() === search.trim().toLowerCase() || item.key === search.trim());
  const add = () => {
    if (!selected) return;
    onChange({ ...values, [selected.key]: values[selected.key] || 1 });
    setSearch("");
  };
  return <details className="item-collection" open={expanded} onToggle={event => setExpanded(event.currentTarget.open)}>
    <summary>{title} <span>{Object.keys(values).length} items</span></summary>
    <div className="item-add">
      <label>{`Find ${title.toLowerCase()} item`}<input type="search" list={`items-${title}`} value={search}
        onChange={event => setSearch(event.target.value)} onKeyDown={event => {
          if (event.key === "Enter") { event.preventDefault(); add(); }
        }} /></label>
      <datalist id={`items-${title}`}>{options.map(item => <option key={item.key} value={item.name} />)}</datalist>
      <button type="button" className="secondary-button" disabled={!selected} onClick={add}>Add</button>
    </div>
    <div className="item-list">{Object.entries(values).sort(([a], [b]) => (names.get(a) || a).localeCompare(names.get(b) || b)).map(([key, quantity]) => <div className="item-row" key={key}>
      <span>{names.get(key) || `Item ${key}`}</span>
      <label><span className="sr-only">{names.get(key) || key} quantity</span><input type="number" min="1" max="2147483647" value={quantity}
        onChange={event => onChange({ ...values, [key]: Math.max(1, Number(event.target.value)) })} /></label>
      <button type="button" aria-label={`Remove ${names.get(key) || key}`} onClick={() => {
        const next = { ...values }; delete next[key]; onChange(next);
      }}>Remove</button>
    </div>)}</div>
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
  const [items, setItems] = useState<Record<ItemField, Record<string, number>>>(() => pickItems(account));
  const [fileError, setFileError] = useState("");
  const { register, reset, getValues, watch, handleSubmit, formState: { errors } } = useForm<AccountBuild>({ defaultValues: account });
  const skills = useMemo(() => Object.keys(account.levels).filter(name => name !== "Total" && name !== "Quest").sort(), [account]);
  const visibleQuests = quests.filter(quest => quest.toLowerCase().includes(questFilter.toLowerCase()));
  const itemKeys = useMemo(() => Array.from(new Set(itemFields.flatMap(field => Object.keys(items[field])))).sort(), [items]);
  const itemNamesQuery = useQuery({ queryKey: ["item-names", itemKeys], queryFn: () => lookupItems(itemKeys), enabled: itemKeys.length > 0 });
  const itemNames = useMemo(() => new Map((itemNamesQuery.data || []).map(item => [item.key, item.name])), [itemNamesQuery.data]);
  const portalMode = watch("poh.portals.mode");
  const cooldown = watch("runtime.minigameTeleport.state");

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
        <div><p className="eyebrow">Account build</p><h2>Customize routing</h2></div>
        <button type="button" className="close-button" aria-label="Close account editor" onClick={onClose}>×</button>
      </header>

      <div className="editor-file-actions">
        <button type="button" className="secondary-button" onClick={() => importInput.current?.click()}>Import JSON</button>
        <button type="button" className="secondary-button" onClick={exportAccount}>Export JSON</button>
        <input ref={importInput} className="sr-only" type="file" accept="application/json,.json" onChange={event => void importAccount(event.target.files?.[0])} />
      </div>
      {fileError && <p className="editor-error" role="alert">{fileError}</p>}

      <label className="editor-name">Build name
        <input {...register("name", { required: "Enter a build name", validate: value => value.trim().length > 0 || "Enter a build name" })} />
        {errors.name && <span role="alert">{errors.name.message}</span>}
      </label>

      <section><h3>Skills</h3><div className="skill-grid">{skills.map(skill => <label key={skill}>{skill}
        <input type="number" min="1" max="99" {...register(`levels.${skill}`, { valueAsNumber: true, min: 1, max: 99 })} />
      </label>)}<label>Quest points<input type="number" min="0" max="32767" defaultValue={account.levels.Quest || 0}
        {...register("levels.Quest", { valueAsNumber: true, min: 0, max: 32767 })} /></label></div></section>

      <section><h3>Items</h3>{itemFields.map(field => <ItemCollectionEditor key={field}
        title={{ inventory: "Inventory", equipment: "Equipment", runePouch: "Rune pouch", bank: "Bank" }[field]}
        values={items[field]} names={itemNames} open={field !== "bank"}
        onChange={value => setItems(current => ({ ...current, [field]: value }))} />)}</section>

      <section><h3>Achievement diaries</h3><div className="diary-grid">{Object.keys(account.diaries).sort().map(diary => <label key={diary}>{splitName(diary)} diary
        <select {...register(`diaries.${diary}`)}>{diaryTiers.map(tier => <option key={tier} value={tier}>{tier === "NoDiary" ? "None" : tier}</option>)}</select>
      </label>)}</div></section>

      <section><h3>Unlocks</h3>
        <label className="check-row"><input type="checkbox" {...register("fairyRingsUnlocked")} /> Fairy rings</label>
        <fieldset><legend>Planted spirit trees</legend>{spiritTrees.map(([value, label]) => <label className="check-row" key={value}>
          <input type="checkbox" value={value} {...register("plantedSpiritTrees")} /> {label}
        </label>)}</fieldset>
      </section>

      <section><h3>Player-owned house</h3>
        <div className="two-column"><label>Location<select {...register("poh.location")}>{pohLocations.map(location => <option key={location}>{location}</option>)}</select></label>
          <label>Jewellery box<select {...register("poh.jewelleryBox")}><option value="NoJewelleryBox">None</option><option value="FancyJewelleryBox">Fancy</option><option value="OrnateJewelleryBox">Ornate</option></select></label></div>
        <div className="check-grid">
          <label className="check-row"><input type="checkbox" {...register("poh.fairyRing")} /> Fairy ring</label>
          <label className="check-row"><input type="checkbox" {...register("poh.spiritTree")} /> Spirit tree</label>
          <label className="check-row"><input type="checkbox" {...register("poh.obelisk")} /> Obelisk</label>
          <label className="check-row"><input type="checkbox" {...register("poh.mountedGlory")} /> Mounted glory</label>
          <label className="check-row"><input type="checkbox" {...register("poh.mountedXerics")} /> Xeric's talisman</label>
          <label className="check-row"><input type="checkbox" {...register("poh.mountedDigsite")} /> Digsite pendant</label>
          <label className="check-row"><input type="checkbox" {...register("poh.mountedMythical")} /> Mythical cape</label>
        </div>
        <label>Portal nexus<select {...register("poh.portals.mode")}><option value="selected">Selected destinations</option><option value="all">All destinations</option></select></label>
        {portalMode === "selected" && <fieldset className="portal-list"><legend>Portal destinations</legend>{portals.map(portal => <label className="check-row" key={portal}>
          <input type="checkbox" value={portal} {...register("poh.portals.destinations")} /> {portal.replace(" Portal", "")}
        </label>)}</fieldset>}
      </section>

      <section><h3>Runtime state</h3><div className="two-column">
        <label>Spellbook<select {...register("runtime.spellbook")}><option>Standard</option><option>Ancient</option><option>Lunar</option><option>Arceuus</option></select></label>
        <label>Minigame teleport<select {...register("runtime.minigameTeleport.state")}><option value="ready">Ready</option><option value="usedAt">On cooldown</option></select></label>
      </div>
      {cooldown === "usedAt" && <label>Used at game minute<input type="number" min="0" {...register("runtime.minigameTeleport.minutes", { valueAsNumber: true, required: true, min: 0 })} /></label>}</section>

      <section><h3>Completed quests</h3>
        <input type="search" value={questFilter} onChange={event => setQuestFilter(event.target.value)} placeholder="Filter quests" aria-label="Filter quests" />
        <fieldset className="quest-list"><legend className="sr-only">Completed quests</legend>{visibleQuests.map(quest => <label className="check-row" key={quest}>
          <input type="checkbox" value={quest} {...register("completedQuests")} /> {quest}
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
