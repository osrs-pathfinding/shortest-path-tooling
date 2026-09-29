import { useQuery } from "@tanstack/react-query";
import {
  agilityIcon, attackIcon, constructionIcon, cookingIcon, craftingIcon, defenceIcon, farmingIcon,
  firemakingIcon, fishingIcon, fletchingIcon, herbloreIcon, hitpointsIcon, hunterIcon, magicIcon,
  miningIcon, prayerIcon, rangedIcon, runecraftIcon, slayerIcon, smithingIcon, strengthIcon,
  thievingIcon, toDataUrl, woodcuttingIcon, questListIcon, skillsIcon,
} from "@dava96/osrs-icons";
import { useEffect, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { useForm, type FieldErrors } from "react-hook-form";
import { findItems, lookupItems } from "../api/items";
import { validateAccount } from "../domain/accountValidation";
import { accountLabel, skillNames, totalLevel } from "../domain/accounts";
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
const diaryNames: Record<string, string> = { KourendKebos: "Kourend & Kebos", LumbridgeDraynor: "Lumbridge & Draynor" };
const itemFields = ["inventory", "equipment", "runePouch", "bank"] as const;
const itemTitles = { inventory: "Inventory", equipment: "Equipment", runePouch: "Rune pouch", bank: "Bank" };
const pohFixtures = [
  ["fairyRing", "Fairy ring"], ["spiritTree", "Spirit tree"], ["obelisk", "Wilderness obelisk"],
  ["mountedGlory", "Mounted glory"], ["mountedXerics", "Xeric's talisman"],
  ["mountedDigsite", "Digsite pendant"], ["mountedMythical", "Mythical cape"],
] as const;
type ItemField = typeof itemFields[number];
type Items = Record<ItemField, Record<string, number>>;
type SectionId = "levels" | "items" | "diaries" | "unlocks" | "house" | "state" | "quests";
const skillIcons: Record<string, string> = toDataUrl({
  Agility: agilityIcon, Attack: attackIcon, Construction: constructionIcon, Cooking: cookingIcon,
  Crafting: craftingIcon, Defence: defenceIcon, Farming: farmingIcon, Firemaking: firemakingIcon,
  Fishing: fishingIcon, Fletching: fletchingIcon, Herblore: herbloreIcon, Hitpoints: hitpointsIcon,
  Hunter: hunterIcon, Magic: magicIcon, Mining: miningIcon, Prayer: prayerIcon, Ranged: rangedIcon,
  Runecraft: runecraftIcon, Slayer: slayerIcon, Smithing: smithingIcon, Strength: strengthIcon,
  Thieving: thievingIcon, Woodcutting: woodcuttingIcon, Quest: questListIcon, Total: skillsIcon,
});
const quantityFormatter = new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 });
const numberFormatter = new Intl.NumberFormat("en");

function Section({ id, title, summary, open, onToggle, children }: {
  id: SectionId;
  title: string;
  summary: ReactNode;
  open: boolean;
  onToggle(id: SectionId, open: boolean): void;
  children: ReactNode;
}) {
  return <details className="editor-section" open={open} onToggle={event => onToggle(id, event.currentTarget.open)}>
    <summary><span className="section-title">{title}</span><span className="section-summary">{summary}</span></summary>
    <div className="section-body">{children}</div>
  </details>;
}

function ItemsEditor({ items, names, onChange }: {
  items: Items;
  names: Map<string, string>;
  onChange(field: ItemField, value: Record<string, number>): void;
}) {
  const [field, setField] = useState<ItemField>("inventory");
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<string>();
  const searching = search.trim().length >= 2;
  const results = useQuery({ queryKey: ["items", search], queryFn: () => findItems(search), enabled: searching });
  const values = items[field];
  const options = results.data || [];
  const nameOf = (key: string) => names.get(key) || `Item ${key}`;
  const update = (value: Record<string, number>) => onChange(field, value);
  const add = (key: string) => {
    update({ ...values, [key]: values[key] || 1 });
    setSelected(key);
    setSearch("");
  };
  const title = itemTitles[field];

  return <>
    <div className="item-tabs" role="tablist" aria-label="Item containers">
      {itemFields.map(tab => <button type="button" role="tab" key={tab} id={`item-tab-${tab}`} aria-selected={tab === field}
        aria-controls="item-tabpanel" onClick={() => { setField(tab); setSelected(undefined); }}>
        {itemTitles[tab]}<small>{Object.keys(items[tab]).length}</small>
      </button>)}
    </div>
    <div className="item-panel" role="tabpanel" id="item-tabpanel" aria-labelledby={`item-tab-${field}`}>
      <div className="item-search">
        <input type="search" value={search} aria-label={`Find ${title.toLowerCase()} item`}
          placeholder={`Add to ${title.toLowerCase()}…`} onChange={event => setSearch(event.target.value)} />
        {searching && <div className="item-results" aria-label="Item search results">
          {results.isFetching && <p>Searching…</p>}
          {results.isError && <p>Item search is unavailable.</p>}
          {!results.isFetching && options.slice(0, 8).map(item => <button type="button" key={item.key} onClick={() => add(item.key)}>
            <img src={itemSprite(item.key)} alt="" /><span>{item.name}</span>{values[item.key] ? <small>Added</small> : null}
          </button>)}
          {!results.isFetching && !results.isError && !options.length && <p>No matching items</p>}
        </div>}
      </div>
      {Object.keys(values).length ? <div className="item-grid" aria-label={`${title} items`}>
        {Object.entries(values).map(([key, quantity]) => <button type="button" className={selected === key ? "selected" : ""} key={key}
          title={nameOf(key)} aria-label={`${nameOf(key)}, quantity ${quantity}`} aria-pressed={selected === key} onClick={() => setSelected(key)}>
          <img src={itemSprite(key)} alt="" /><span>{quantity > 1 ? quantityFormatter.format(quantity) : ""}</span>
        </button>)}
      </div> : <p className="item-empty">No items in {title.toLowerCase()}.</p>}
      {selected && values[selected] ? <div className="item-editor">
        <strong title={nameOf(selected)}>{nameOf(selected)}</strong>
        <input type="number" min="1" max="2147483647" value={values[selected]} aria-label={`${nameOf(selected)} quantity`}
          onChange={event => update({ ...values, [selected]: clampQuantity(event.target.valueAsNumber) })} />
        <button type="button" className="text-button danger" aria-label={`Remove ${nameOf(selected)}`} onClick={() => {
          const next = { ...values };
          delete next[selected];
          update(next);
          setSelected(undefined);
        }}>Remove</button>
      </div> : null}
    </div>
  </>;
}

export function AccountPanel({ accounts, account, quests, open, onSelect, onSave, onClose }: {
  accounts: AccountBuild[];
  account: AccountBuild;
  quests: string[];
  open: boolean;
  onSelect(id: string): void;
  onSave(account: AccountBuild): void;
  onClose(): void;
}) {
  const heading = useRef<HTMLHeadingElement>(null);
  const importInput = useRef<HTMLInputElement>(null);
  const [openSections, setOpenSections] = useState<Set<SectionId>>(() => new Set(["levels"]));
  const [questFilter, setQuestFilter] = useState("");
  const [portalFilter, setPortalFilter] = useState("");
  const [items, setItems] = useState<Items>(() => pickItems(account));
  const [message, setMessage] = useState("");
  const { register, reset, setValue, getValues, watch, handleSubmit, formState: { errors, isDirty } } = useForm<AccountBuild>({
    defaultValues: formDefaults(account), shouldFocusError: false,
  });
  const values = watch();
  const skills = useMemo(() => skillNames(account.levels), [account]);
  const originalItems = useMemo(() => JSON.stringify(pickItems(account)), [account]);
  const dirty = isDirty || JSON.stringify(items) !== originalItems;
  const itemKeys = useMemo(() => Array.from(new Set(itemFields.flatMap(field => Object.keys(items[field])))).sort(), [items]);
  const itemNamesQuery = useQuery({ queryKey: ["item-names", itemKeys], queryFn: () => lookupItems(itemKeys), enabled: itemKeys.length > 0 });
  const itemNames = useMemo(() => new Map((itemNamesQuery.data || []).map(item => [item.key, item.name])), [itemNamesQuery.data]);
  const completedQuests = values.completedQuests || [];
  const selectedPortals = values.poh.portals.destinations || [];
  const visibleQuests = quests.filter(quest => quest.toLowerCase().includes(questFilter.toLowerCase()));
  const visiblePortals = portals.filter(portal => portal.toLowerCase().includes(portalFilter.toLowerCase()));
  const itemCount = itemFields.reduce((count, field) => count + Object.keys(items[field]).length, 0);
  const isCustom = account.id === "custom";

  useEffect(() => { reset(formDefaults(account)); setItems(pickItems(account)); setMessage(""); }, [account, reset]);
  useEffect(() => { if (open) heading.current?.focus(); }, [open]);

  const toggleSection = (id: SectionId, isOpen: boolean) => setOpenSections(current => {
    if (current.has(id) === isOpen) return current;
    const next = new Set(current);
    if (isOpen) next.add(id); else next.delete(id);
    return next;
  });
  const build = (value: AccountBuild): AccountBuild => {
    const levels = { ...value.levels };
    if ("Total" in levels) levels.Total = totalLevel(levels);
    return validateAccount({
      ...value, ...items, levels, id: "custom", name: value.name.trim(),
      runtime: {
        ...value.runtime,
        minigameTeleport: value.runtime.minigameTeleport.state === "usedAt"
          ? { state: "usedAt", minutes: Number(value.runtime.minigameTeleport.minutes) }
          : { state: "ready" },
      },
    });
  };
  const showInvalid = (invalid: FieldErrors<AccountBuild>) => {
    if (invalid.levels) toggleSection("levels", true);
    if (invalid.runtime) toggleSection("state", true);
    setMessage(invalid.name ? "Enter a build name before saving." : "Some values are out of range. Fix the highlighted fields.");
  };
  const discard = () => { reset(formDefaults(account)); setItems(pickItems(account)); setMessage(""); };
  const select = (id: string) => {
    if (id === account.id) return;
    if (dirty && !window.confirm("Discard unsaved changes to this build?")) return;
    onSelect(id);
  };
  const exportAccount = () => {
    try {
      const blob = new Blob([JSON.stringify(build(getValues()), null, 2) + "\n"], { type: "application/json" });
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = "osrs-account-build.json";
      link.click();
      setTimeout(() => URL.revokeObjectURL(link.href), 1000);
      setMessage("");
    } catch (error) { setMessage(errorMessage(error, "Invalid account build")); }
  };
  const importAccount = async (file?: File) => {
    if (!file) return;
    try {
      const imported = validateAccount(JSON.parse(await file.text()));
      // Keep the current defaults so the import shows as unsaved changes.
      reset(formDefaults(imported), { keepDefaultValues: true });
      setItems(pickItems(imported));
      setMessage("");
    } catch (error) { setMessage(errorMessage(error, "Invalid account file")); }
    if (importInput.current) importInput.current.value = "";
  };
  const closeOnEscape = (event: KeyboardEvent) => {
    // Let search fields clear themselves before Escape closes the panel.
    const target = event.target as HTMLInputElement;
    if (event.key !== "Escape" || (target.type === "search" && target.value)) return;
    onClose();
  };

  return <aside id="account-panel" className="account-panel" hidden={!open} aria-labelledby="account-panel-title" onKeyDown={closeOnEscape}>
    <header className="panel-header">
      <div><p className="eyebrow">Account</p><h2 id="account-panel-title" ref={heading} tabIndex={-1}>{accountLabel(account)}</h2></div>
      <button type="button" className="close-button" aria-label="Close account panel" onClick={onClose}>×</button>
    </header>

    <div className="panel-body">
      <fieldset className="profile-picker">
        <legend>Profile</legend>
        {accounts.map(option => <label key={option.id} className={option.id === "custom" ? "custom" : undefined}>
          <input type="radio" name="account-profile" value={option.id} checked={option.id === account.id} onChange={() => select(option.id)} />
          <span><strong>{accountLabel(option)}</strong>
            <small>{option.id === "custom" ? "Saved in this browser" : `Total ${numberFormatter.format(totalLevel(option.levels))} · ${option.completedQuests.length} quests`}</small></span>
        </label>)}
      </fieldset>

      <form id="account-form" className="account-editor" noValidate onSubmit={handleSubmit(value => {
        if (!isCustom && accounts.some(option => option.id === "custom")
          && !window.confirm("Replace your saved custom build?")) return;
        try { onSave(build(value)); setMessage(""); } catch (error) { setMessage(errorMessage(error, "Invalid account build")); }
      }, showInvalid)}>
        <label className="field-label">Build name
          <input aria-invalid={errors.name ? true : undefined}
            {...register("name", { validate: value => value.trim().length > 0 || "Enter a build name" })} />
        </label>
        {!isCustom && <p className="editor-note">Changes are saved as your custom build; the {accountLabel(account)} preset stays as it is.</p>}

        <Section id="levels" title="Levels" open={openSections.has("levels")} onToggle={toggleSection}
          summary={`Total ${numberFormatter.format(totalLevel(values.levels))}`}>
          <div className="skill-grid">
            {skills.map(skill => <label className="skill-card" key={skill} title={skill}>
              <img src={skillIcons[skill] || `https://oldschool.runescape.wiki/images/${skill}_icon.png`} alt="" /><span className="sr-only">{skill}</span>
              <input type="number" min="1" max="99" aria-invalid={errors.levels?.[skill] ? true : undefined}
                {...register(`levels.${skill}`, { valueAsNumber: true, required: true, min: 1, max: 99 })} />
            </label>)}
            <label className="skill-card" title="Quest points"><img src={skillIcons.Quest} alt="" /><span className="sr-only">Quest points</span>
              <input type="number" min="0" max="32767" aria-invalid={errors.levels?.Quest ? true : undefined}
                {...register("levels.Quest", { valueAsNumber: true, required: true, min: 0, max: 32767 })} />
            </label>
            <div className="skill-card total" title="Total level"><img src={skillIcons.Total} alt="" />
              <output aria-label="Total level">{totalLevel(values.levels)}</output></div>
          </div>
        </Section>

        <Section id="items" title="Items" open={openSections.has("items")} onToggle={toggleSection}
          summary={plural(itemCount, "item")}>
          <ItemsEditor items={items} names={itemNames} onChange={(field, value) => setItems(current => ({ ...current, [field]: value }))} />
        </Section>

        <Section id="diaries" title="Achievement diaries" open={openSections.has("diaries")} onToggle={toggleSection}
          summary={summarizeDiaries(values.diaries)}>
          <div className="diary-row diary-all"><span>Set all regions</span>
            <div className="tier-selector">{diaryTiers.map(tier => <button type="button" key={tier} onClick={() => Object.keys(account.diaries)
              .forEach(diary => setValue(`diaries.${diary}`, tier, { shouldDirty: true }))}>{tierLabel(tier)}</button>)}</div>
          </div>
          {Object.keys(account.diaries).sort().map(diary => <fieldset className="diary-row" key={diary}>
            <legend>{diaryNames[diary] || splitName(diary)}</legend>
            <div className="tier-selector">{diaryTiers.map(tier => <label key={tier}>
              <input type="radio" value={tier} {...register(`diaries.${diary}`)} /><span>{tierLabel(tier)}</span>
            </label>)}</div>
          </fieldset>)}
        </Section>

        <Section id="unlocks" title="Travel unlocks" open={openSections.has("unlocks")} onToggle={toggleSection}
          summary={[values.fairyRingsUnlocked && "Fairy rings", plural(values.plantedSpiritTrees.length, "spirit tree")].filter(Boolean).join(" · ")}>
          <label className="check-pill"><input type="checkbox" {...register("fairyRingsUnlocked")} /><span>Fairy rings</span></label>
          <h4>Planted spirit trees</h4>
          <div className="check-grid">{spiritTrees.map(([value, label]) => <label className="check-pill" key={value}>
            <input type="checkbox" value={value} {...register("plantedSpiritTrees")} /><span>{label}</span>
          </label>)}</div>
        </Section>

        <Section id="house" title="Player-owned house" open={openSections.has("house")} onToggle={toggleSection} summary={values.poh.location}>
          <label className="field-label">House location<select {...register("poh.location")}>{pohLocations.map(location => <option key={location}>{location}</option>)}</select></label>
          <fieldset className="choice-group"><legend>Jewellery box</legend><div className="segmented-control">
            {[["NoJewelleryBox", "None"], ["FancyJewelleryBox", "Fancy"], ["OrnateJewelleryBox", "Ornate"]].map(([value, label]) => <label key={value}>
              <input type="radio" value={value} {...register("poh.jewelleryBox")} /><span>{label}</span>
            </label>)}
          </div></fieldset>
          <h4>Fixtures</h4>
          <div className="check-grid">{pohFixtures.map(([name, label]) => <label className="check-pill" key={name}>
            <input type="checkbox" {...register(`poh.${name}`)} /><span>{label}</span>
          </label>)}</div>
          <fieldset className="choice-group"><legend>Portal nexus</legend><div className="segmented-control two">
            <label><input type="radio" value="selected" {...register("poh.portals.mode")} /><span>Choose portals</span></label>
            <label><input type="radio" value="all" {...register("poh.portals.mode")} /><span>All portals</span></label>
          </div></fieldset>
          {values.poh.portals.mode === "selected" && <>
            <div className="list-toolbar"><input type="search" value={portalFilter} onChange={event => setPortalFilter(event.target.value)}
              placeholder="Filter portal destinations" aria-label="Filter portal destinations" /><span>{selectedPortals.length} selected</span></div>
            <fieldset className="check-list"><legend className="sr-only">Portal destinations</legend>{visiblePortals.map(portal => <label className="check-pill" key={portal}>
              <input type="checkbox" value={portal} {...register("poh.portals.destinations")} /><span>{portal.replace(" Portal", "")}</span>
            </label>)}</fieldset>
          </>}
        </Section>

        <Section id="state" title="Current state" open={openSections.has("state")} onToggle={toggleSection}
          summary={`${values.runtime.spellbook} spellbook`}>
          <fieldset className="choice-group"><legend>Active spellbook</legend><div className="segmented-control four">
            {["Standard", "Ancient", "Lunar", "Arceuus"].map(value => <label key={value}><input type="radio" value={value} {...register("runtime.spellbook")} /><span>{value}</span></label>)}
          </div></fieldset>
          <fieldset className="choice-group"><legend>Minigame teleport</legend><div className="segmented-control two">
            <label><input type="radio" value="ready" {...register("runtime.minigameTeleport.state")} /><span>Ready</span></label>
            <label><input type="radio" value="usedAt" {...register("runtime.minigameTeleport.state")} /><span>On cooldown</span></label>
          </div></fieldset>
          {values.runtime.minigameTeleport.state === "usedAt" && <label className="field-label">Used at game minute
            <input type="number" min="0" aria-invalid={errors.runtime ? true : undefined}
              {...register("runtime.minigameTeleport.minutes", { valueAsNumber: true, required: true, min: 0 })} /></label>}
        </Section>

        <Section id="quests" title="Completed quests" open={openSections.has("quests")} onToggle={toggleSection}
          summary={`${completedQuests.length} of ${quests.length}`}>
          <div className="list-toolbar">
            <input type="search" value={questFilter} onChange={event => setQuestFilter(event.target.value)} placeholder="Filter quests" aria-label="Filter quests" />
            <button type="button" className="text-button" onClick={() => setValue("completedQuests", Array.from(new Set([...completedQuests, ...visibleQuests])), { shouldDirty: true })}>Select shown</button>
            <button type="button" className="text-button" onClick={() => setValue("completedQuests", completedQuests.filter(quest => !visibleQuests.includes(quest)), { shouldDirty: true })}>Clear shown</button>
          </div>
          <fieldset className="check-list"><legend className="sr-only">Completed quests</legend>{visibleQuests.map(quest => <label className="check-pill" key={quest}>
            <input type="checkbox" value={quest} {...register("completedQuests")} /><span>{quest}</span>
          </label>)}</fieldset>
        </Section>
      </form>
    </div>

    <footer className="panel-footer">
      {message && <p className="editor-error" role="alert">{message}</p>}
      <div className="panel-status">
        <span className={dirty ? "dirty-state dirty" : "dirty-state"} aria-live="polite">{dirty ? "Unsaved changes" : isCustom ? "Saved" : "Preset"}</span>
        <button type="button" className="text-button" onClick={() => importInput.current?.click()}>Import</button>
        <button type="button" className="text-button" onClick={exportAccount}>Export</button>
        <input ref={importInput} className="sr-only" type="file" tabIndex={-1} accept="application/json,.json"
          onChange={event => void importAccount(event.target.files?.[0])} />
      </div>
      <div className="panel-actions">
        <button type="button" className="secondary-button" disabled={!dirty} onClick={discard}>Discard</button>
        <button type="submit" form="account-form" className="primary-button" disabled={isCustom && !dirty}>
          {isCustom ? "Save" : "Save as custom"}</button>
      </div>
    </footer>
  </aside>;
}

function formDefaults(account: AccountBuild): AccountBuild {
  return {
    ...account,
    name: account.id === "custom" ? account.name : `${accountLabel(account)} (custom)`,
    levels: { Quest: 0, ...account.levels },
  };
}

function pickItems(account: AccountBuild): Items {
  return { inventory: { ...account.inventory }, equipment: { ...account.equipment }, runePouch: { ...account.runePouch }, bank: { ...account.bank } };
}

function summarizeDiaries(diaries: AccountBuild["diaries"]): string {
  const counts = Object.values(diaries).reduce<Record<string, number>>((result, tier) => ({ ...result, [tier]: (result[tier] || 0) + 1 }), {});
  return diaryTiers.filter(tier => counts[tier]).map(tier => `${counts[tier]} ${tierLabel(tier).toLowerCase()}`).join(" · ");
}

function plural(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? "" : "s"}`;
}

function tierLabel(tier: typeof diaryTiers[number]): string {
  return tier === "NoDiary" ? "None" : tier;
}

function clampQuantity(value: number): number {
  return Number.isFinite(value) ? Math.min(2147483647, Math.max(1, Math.floor(value))) : 1;
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback;
}

function splitName(value: string): string {
  return value.replace(/([a-z])([A-Z])/g, "$1 $2");
}

function itemSprite(key: string): string {
  return `https://chisel.weirdgloop.org/static/img/osrs-sprite/${key}.png`;
}
