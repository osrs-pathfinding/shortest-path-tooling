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
import { orderSkills, totalLevel, type PlannerAccount } from "../domain/accounts";
import type { Account, Catalog, House, Option } from "../domain/contracts";

const itemFields = ["inventory", "equipment", "runePouch", "bank"] as const;
const itemTitles = { inventory: "Inventory", equipment: "Equipment", runePouch: "Rune pouch", bank: "Bank" };
const houseFixtures = [
  ["fairyRing", "Fairy ring"], ["spiritTree", "Spirit tree"], ["obelisk", "Wilderness obelisk"],
  ["mountedGlory", "Mounted glory"], ["mountedXerics", "Xeric's talisman"],
  ["mountedDigsite", "Digsite pendant"], ["mountedMythical", "Mythical cape"],
] as const;
const defaultHouse: House = {
  location: "RIMMINGTON", jewelleryBox: "NONE", fairyRing: false, spiritTree: false, obelisk: false,
  mountedGlory: false, mountedXerics: false, mountedDigsite: false, mountedMythical: false, portals: [],
};
type ItemField = typeof itemFields[number];
type Items = Record<ItemField, Record<string, number>>;
type SectionId = "levels" | "items" | "diaries" | "unlocks" | "house" | "state" | "quests";
const skillIcons: Record<string, string> = toDataUrl({
  AGILITY: agilityIcon, ATTACK: attackIcon, CONSTRUCTION: constructionIcon, COOKING: cookingIcon,
  CRAFTING: craftingIcon, DEFENCE: defenceIcon, FARMING: farmingIcon, FIREMAKING: firemakingIcon,
  FISHING: fishingIcon, FLETCHING: fletchingIcon, HERBLORE: herbloreIcon, HITPOINTS: hitpointsIcon,
  HUNTER: hunterIcon, MAGIC: magicIcon, MINING: miningIcon, PRAYER: prayerIcon, RANGED: rangedIcon,
  RUNECRAFT: runecraftIcon, SLAYER: slayerIcon, SMITHING: smithingIcon, STRENGTH: strengthIcon,
  THIEVING: thievingIcon, WOODCUTTING: woodcuttingIcon, QUEST: questListIcon, TOTAL: skillsIcon,
});
const quantityFormatter = new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 });
const numberFormatter = new Intl.NumberFormat("en");

/** The editor's form: the account's fields plus the choices that decide optional ones. */
interface AccountForm extends Omit<Account, "inventory" | "runePouch" | "equipment" | "bank" | "house" | "minigameTeleportUsedAt"> {
  hasHouse: boolean;
  house: House;
  allPortals: boolean;
  minigameTeleport: "ready" | "usedAt";
  minigameTeleportUsedAt: number;
}

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

export function AccountPanel({ accounts, selected, catalog, open, onSelect, onSave, onClose }: {
  accounts: PlannerAccount[];
  selected: PlannerAccount;
  catalog: Catalog;
  open: boolean;
  onSelect(id: string): void;
  onSave(account: Account): void;
  onClose(): void;
}) {
  const account = selected.account;
  const heading = useRef<HTMLHeadingElement>(null);
  const importInput = useRef<HTMLInputElement>(null);
  const [openSections, setOpenSections] = useState<Set<SectionId>>(() => new Set(["levels"]));
  const [questFilter, setQuestFilter] = useState("");
  const [portalFilter, setPortalFilter] = useState("");
  const [items, setItems] = useState<Items>(() => pickItems(account));
  const [message, setMessage] = useState("");
  const isCustom = selected.id === "custom";
  const defaults = useMemo(() => formDefaults(account, isCustom ? selected.name : `${selected.name} (custom)`, catalog),
    [account, isCustom, selected.name, catalog]);
  const { register, reset, setValue, getValues, watch, handleSubmit, formState: { errors, isDirty } } = useForm<AccountForm>({
    defaultValues: defaults, shouldFocusError: false,
  });
  const values = watch();
  const skills = useMemo(() => orderSkills(catalog.skills.map(skill => skill.id)), [catalog]);
  const skillNames = useMemo(() => new Map(catalog.skills.map(skill => [skill.id, skill.name])), [catalog]);
  const originalItems = useMemo(() => JSON.stringify(pickItems(account)), [account]);
  const dirty = isDirty || JSON.stringify(items) !== originalItems;
  const itemKeys = useMemo(() => Array.from(new Set(itemFields.flatMap(field => Object.keys(items[field])))).sort(), [items]);
  const itemNamesQuery = useQuery({ queryKey: ["item-names", itemKeys], queryFn: () => lookupItems(itemKeys), enabled: itemKeys.length > 0 });
  const itemNames = useMemo(() => new Map((itemNamesQuery.data || []).map(item => [item.key, item.name])), [itemNamesQuery.data]);
  const completedQuests = values.completedQuests || [];
  const selectedPortals = values.house.portals || [];
  const visibleQuests = catalog.quests.filter(quest => quest.name.toLowerCase().includes(questFilter.toLowerCase()));
  const visiblePortals = catalog.portals.filter(portal => portal.name.toLowerCase().includes(portalFilter.toLowerCase()));
  const unlockGroups = useMemo(() => groupBy(catalog.unlocks), [catalog]);
  const itemCount = itemFields.reduce((count, field) => count + Object.keys(items[field]).length, 0);
  const nameOf = (options: Option[], id: string) => options.find(option => option.id === id)?.name || id;

  useEffect(() => { reset(defaults); setItems(pickItems(account)); setMessage(""); }, [account, defaults, reset]);
  useEffect(() => { if (open) heading.current?.focus(); }, [open]);

  const toggleSection = (id: SectionId, isOpen: boolean) => setOpenSections(current => {
    if (current.has(id) === isOpen) return current;
    const next = new Set(current);
    if (isOpen) next.add(id); else next.delete(id);
    return next;
  });
  const build = (form: AccountForm): Account => {
    const { hasHouse, house, allPortals, minigameTeleport, minigameTeleportUsedAt, ...rest } = form;
    const { portals, ...houseFacts } = house;
    return validateAccount({
      ...rest, ...items, name: form.name.trim(),
      // A checkbox group with one option reports a boolean or a string, not an array.
      completedQuests: checked(rest.completedQuests), unlocks: checked(rest.unlocks),
      plantedSpiritTrees: checked(rest.plantedSpiritTrees),
      ...hasHouse ? { house: allPortals ? houseFacts : { ...houseFacts, portals: checked(portals) } } : {},
      ...minigameTeleport === "usedAt" ? { minigameTeleportUsedAt: Number(minigameTeleportUsedAt) } : {},
    });
  };
  const showInvalid = (invalid: FieldErrors<AccountForm>) => {
    if (invalid.levels || invalid.questPoints) toggleSection("levels", true);
    if (invalid.minigameTeleportUsedAt) toggleSection("state", true);
    setMessage(invalid.name ? "Enter a build name before saving." : "Some values are out of range. Fix the highlighted fields.");
  };
  const discard = () => { reset(defaults); setItems(pickItems(account)); setMessage(""); };
  const select = (id: string) => {
    if (id === selected.id) return;
    if (dirty && !window.confirm("Discard unsaved changes to this build?")) return;
    onSelect(id);
  };
  const exportAccount = () => {
    try {
      const blob = new Blob([JSON.stringify(build(getValues()), null, 2) + "\n"], { type: "application/json" });
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = "osrs-account.json";
      link.click();
      setTimeout(() => URL.revokeObjectURL(link.href), 1000);
      setMessage("");
    } catch (error) { setMessage(errorMessage(error, "Invalid account")); }
  };
  const importAccount = async (file?: File) => {
    if (!file) return;
    try {
      const imported = validateAccount(JSON.parse(await file.text()));
      // Keep the current defaults so the import shows as unsaved changes.
      reset(formDefaults(imported, imported.name, catalog), { keepDefaultValues: true });
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
      <div><p className="eyebrow">Account</p><h2 id="account-panel-title" ref={heading} tabIndex={-1}>{selected.name}</h2></div>
      <button type="button" className="close-button" aria-label="Close account panel" onClick={onClose}>×</button>
    </header>

    <div className="panel-body">
      <fieldset className="profile-picker">
        <legend>Profile</legend>
        {accounts.map(option => <label key={option.id} className={option.id === "custom" ? "custom" : undefined}>
          <input type="radio" name="account-profile" value={option.id} checked={option.id === selected.id} onChange={() => select(option.id)} />
          <span><strong>{option.name}</strong>
            <small>{option.id === "custom" ? "Saved in this browser" : `Total ${numberFormatter.format(totalLevel(option.account.levels))} · ${option.account.completedQuests.length} quests`}</small></span>
        </label>)}
      </fieldset>

      <form id="account-form" className="account-editor" noValidate onSubmit={handleSubmit(form => {
        if (!isCustom && accounts.some(option => option.id === "custom")
          && !window.confirm("Replace your saved custom build?")) return;
        try { onSave(build(form)); setMessage(""); } catch (error) { setMessage(errorMessage(error, "Invalid account")); }
      }, showInvalid)}>
        <label className="field-label">Build name
          <input aria-invalid={errors.name ? true : undefined}
            {...register("name", { validate: value => value.trim().length > 0 || "Enter a build name" })} />
        </label>
        {!isCustom && <p className="editor-note">Changes are saved as your custom build; the {selected.name} preset stays as it is.</p>}

        <Section id="levels" title="Levels" open={openSections.has("levels")} onToggle={toggleSection}
          summary={`Total ${numberFormatter.format(totalLevel(values.levels))}`}>
          <div className="skill-grid">
            {skills.map(skill => <label className="skill-card" key={skill} title={skillNames.get(skill)}>
              <img src={skillIcons[skill] || `https://oldschool.runescape.wiki/images/${skillNames.get(skill)}_icon.png`} alt="" /><span className="sr-only">{skillNames.get(skill)}</span>
              <input type="number" min="1" max="99" aria-invalid={errors.levels?.[skill] ? true : undefined}
                {...register(`levels.${skill}`, { valueAsNumber: true, required: true, min: 1, max: 99 })} />
            </label>)}
            <label className="skill-card" title="Quest points"><img src={skillIcons.QUEST} alt="" /><span className="sr-only">Quest points</span>
              <input type="number" min="0" max="32767" aria-invalid={errors.questPoints ? true : undefined}
                {...register("questPoints", { valueAsNumber: true, required: true, min: 0, max: 32767 })} />
            </label>
            <div className="skill-card total" title="Total level"><img src={skillIcons.TOTAL} alt="" />
              <output aria-label="Total level">{totalLevel(values.levels)}</output></div>
          </div>
        </Section>

        <Section id="items" title="Items" open={openSections.has("items")} onToggle={toggleSection}
          summary={plural(itemCount, "item")}>
          <ItemsEditor items={items} names={itemNames} onChange={(field, value) => setItems(current => ({ ...current, [field]: value }))} />
        </Section>

        <Section id="diaries" title="Achievement diaries" open={openSections.has("diaries")} onToggle={toggleSection}
          summary={summarizeDiaries(values.diaries, catalog)}>
          <div className="diary-row diary-all"><span>Set all regions</span>
            <div className="tier-selector">{catalog.diaryTiers.map(tier => <button type="button" key={tier.id} onClick={() => catalog.diaries
              .forEach(diary => setValue(`diaries.${diary.id}`, tier.id as Account["diaries"][string], { shouldDirty: true }))}>{tier.name}</button>)}</div>
          </div>
          {catalog.diaries.map(diary => <fieldset className="diary-row" key={diary.id}>
            <legend>{diary.name}</legend>
            <div className="tier-selector">{catalog.diaryTiers.map(tier => <label key={tier.id}>
              <input type="radio" value={tier.id} {...register(`diaries.${diary.id}`)} /><span>{tier.name}</span>
            </label>)}</div>
          </fieldset>)}
        </Section>

        <Section id="unlocks" title="Unlocks" open={openSections.has("unlocks")} onToggle={toggleSection}
          summary={[plural(values.unlocks.length, "unlock"), plural(values.plantedSpiritTrees.length, "spirit tree")].join(" · ")}>
          {[...unlockGroups].map(([group, unlocks]) => <fieldset className="check-group" key={group}>
            <legend>{group}</legend>
            <div className="check-grid">{unlocks.map(unlock => <label className="check-pill" key={unlock.id}>
              <input type="checkbox" value={unlock.id} {...register("unlocks")} /><span>{unlock.name}</span>
            </label>)}</div>
          </fieldset>)}
          <fieldset className="check-group"><legend>Planted spirit trees</legend>
            <div className="check-grid">{catalog.plantedSpiritTrees.map(tree => <label className="check-pill" key={tree.id}>
              <input type="checkbox" value={tree.id} {...register("plantedSpiritTrees")} /><span>{tree.name}</span>
            </label>)}</div>
          </fieldset>
        </Section>

        <Section id="house" title="Player-owned house" open={openSections.has("house")} onToggle={toggleSection}
          summary={values.hasHouse ? nameOf(catalog.houseLocations, values.house.location) : "None"}>
          <label className="check-pill"><input type="checkbox" {...register("hasHouse")} /><span>Has a player-owned house</span></label>
          {values.hasHouse && <>
            <label className="field-label">House location<select {...register("house.location")}>
              {catalog.houseLocations.map(location => <option key={location.id} value={location.id}>{location.name}</option>)}</select></label>
            <fieldset className="choice-group"><legend>Jewellery box</legend><div className="segmented-control">
              {catalog.jewelleryBoxes.map(box => <label key={box.id}>
                <input type="radio" value={box.id} {...register("house.jewelleryBox")} /><span>{box.name}</span>
              </label>)}
            </div></fieldset>
            <h4>Fixtures</h4>
            <div className="check-grid">{houseFixtures.map(([name, label]) => <label className="check-pill" key={name}>
              <input type="checkbox" {...register(`house.${name}`)} /><span>{label}</span>
            </label>)}</div>
            <label className="check-pill"><input type="checkbox" {...register("allPortals")} /><span>Every nexus portal</span></label>
            {!values.allPortals && <>
              <div className="list-toolbar"><input type="search" value={portalFilter} onChange={event => setPortalFilter(event.target.value)}
                placeholder="Filter portal destinations" aria-label="Filter portal destinations" /><span>{selectedPortals.length} selected</span></div>
              <fieldset className="check-list"><legend className="sr-only">Portal destinations</legend>{visiblePortals.map(portal => <label className="check-pill" key={portal.id}>
                <input type="checkbox" value={portal.id} {...register("house.portals")} /><span>{portal.name}</span>
              </label>)}</fieldset>
            </>}
          </>}
        </Section>

        <Section id="state" title="Current state" open={openSections.has("state")} onToggle={toggleSection}
          summary={`${nameOf(catalog.spellbooks, values.spellbook)} spellbook`}>
          <fieldset className="choice-group"><legend>Active spellbook</legend><div className="segmented-control four">
            {catalog.spellbooks.map(book => <label key={book.id}><input type="radio" value={book.id} {...register("spellbook")} /><span>{book.name}</span></label>)}
          </div></fieldset>
          <fieldset className="choice-group"><legend>Minigame teleport</legend><div className="segmented-control two">
            <label><input type="radio" value="ready" {...register("minigameTeleport")} /><span>Ready</span></label>
            <label><input type="radio" value="usedAt" {...register("minigameTeleport")} /><span>On cooldown</span></label>
          </div></fieldset>
          {values.minigameTeleport === "usedAt" && <label className="field-label">Used at game minute
            <input type="number" min="0" aria-invalid={errors.minigameTeleportUsedAt ? true : undefined}
              {...register("minigameTeleportUsedAt", { valueAsNumber: true, required: true, min: 0 })} /></label>}
        </Section>

        <Section id="quests" title="Completed quests" open={openSections.has("quests")} onToggle={toggleSection}
          summary={`${completedQuests.length} of ${catalog.quests.length}`}>
          <div className="list-toolbar">
            <input type="search" value={questFilter} onChange={event => setQuestFilter(event.target.value)} placeholder="Filter quests" aria-label="Filter quests" />
            <button type="button" className="text-button" onClick={() => setValue("completedQuests",
              Array.from(new Set([...completedQuests, ...visibleQuests.map(quest => quest.id)])), { shouldDirty: true })}>Select shown</button>
            <button type="button" className="text-button" onClick={() => setValue("completedQuests",
              completedQuests.filter(quest => !visibleQuests.some(shown => shown.id === quest)), { shouldDirty: true })}>Clear shown</button>
          </div>
          <fieldset className="check-list"><legend className="sr-only">Completed quests</legend>{visibleQuests.map(quest => <label className="check-pill" key={quest.id}>
            <input type="checkbox" value={quest.id} {...register("completedQuests")} /><span>{quest.name}</span>
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

/** The form for `account`; skills it does not list start at level 1, and diaries it does not list at none. */
function formDefaults(account: Account, name: string, catalog: Catalog): AccountForm {
  const { inventory: _inventory, runePouch: _runePouch, equipment: _equipment, bank: _bank, house, minigameTeleportUsedAt, ...rest } = account;
  return {
    ...rest,
    name,
    levels: { ...Object.fromEntries(catalog.skills.map(skill => [skill.id, 1])), ...account.levels },
    diaries: { ...Object.fromEntries(catalog.diaries.map(diary => [diary.id, "NONE"])), ...account.diaries },
    hasHouse: house !== undefined,
    house: { ...defaultHouse, ...house, portals: house?.portals || [] },
    allPortals: house !== undefined && house.portals === undefined,
    minigameTeleport: minigameTeleportUsedAt === undefined ? "ready" : "usedAt",
    minigameTeleportUsedAt: minigameTeleportUsedAt ?? 0,
  };
}

/** The values a react-hook-form checkbox group reports, as an array. */
function checked(value: unknown): string[] {
  if (Array.isArray(value)) return value;
  return typeof value === "string" ? [value] : [];
}

function pickItems(account: Account): Items {
  return { inventory: { ...account.inventory }, equipment: { ...account.equipment }, runePouch: { ...account.runePouch }, bank: { ...account.bank } };
}

function groupBy(options: Option[]): Map<string, Option[]> {
  const groups = new Map<string, Option[]>();
  options.forEach(option => groups.set(option.group || "", [...groups.get(option.group || "") || [], option]));
  return groups;
}

function summarizeDiaries(diaries: Account["diaries"], catalog: Catalog): string {
  const counts = Object.values(diaries).reduce<Record<string, number>>((result, tier) => ({ ...result, [tier]: (result[tier] || 0) + 1 }), {});
  return catalog.diaryTiers.filter(tier => counts[tier.id]).map(tier => `${counts[tier.id]} ${tier.name.toLowerCase()}`).join(" · ");
}

function plural(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? "" : "s"}`;
}

function clampQuantity(value: number): number {
  return Number.isFinite(value) ? Math.min(2147483647, Math.max(1, Math.floor(value))) : 1;
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback;
}

function itemSprite(key: string): string {
  return `https://chisel.weirdgloop.org/static/img/osrs-sprite/${key}.png`;
}
