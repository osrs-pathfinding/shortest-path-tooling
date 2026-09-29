import { useEffect, useMemo, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import type { AccountBuild } from "../domain/contracts";

const spiritTrees = [
  ["FARMING_GUILD", "Farming Guild"],
  ["PORT_SARIM", "Port Sarim"],
  ["ETCETERIA", "Etceteria"],
  ["BRIMHAVEN", "Brimhaven"],
  ["HOSIDIUS", "Hosidius"],
] as const;

export function AccountEditor({ account, quests, open, onClose, onSave }: {
  account: AccountBuild;
  quests: string[];
  open: boolean;
  onClose(): void;
  onSave(account: AccountBuild): void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [questFilter, setQuestFilter] = useState("");
  const { register, reset, handleSubmit, formState: { errors } } = useForm<AccountBuild>({ defaultValues: account });
  const skills = useMemo(() => Object.keys(account.levels)
    .filter(name => name !== "Total" && name !== "Quest").sort(), [account]);
  const visibleQuests = quests.filter(quest => quest.toLowerCase().includes(questFilter.toLowerCase()));

  useEffect(() => reset(account), [account, reset]);
  useEffect(() => {
    if (open && !dialog.current?.open) dialog.current?.showModal();
    if (!open && dialog.current?.open) dialog.current.close();
  }, [open]);

  return <dialog ref={dialog} className="account-dialog" onClose={onClose}>
    <form method="dialog" className="account-editor" onSubmit={handleSubmit(value => onSave({
      ...value,
      id: "custom",
      name: value.name.trim(),
    }))}>
      <header>
        <div><p className="eyebrow">Account build</p><h2>Customize routing</h2></div>
        <button type="button" className="close-button" aria-label="Close account editor" onClick={onClose}>×</button>
      </header>

      <label className="editor-name">Build name
        <input {...register("name", {
          required: "Enter a build name",
          validate: value => value.trim().length > 0 || "Enter a build name",
        })} />
        {errors.name && <span role="alert">{errors.name.message}</span>}
      </label>

      <section>
        <h3>Skills</h3>
        <div className="skill-grid">{skills.map(skill => <label key={skill}>{skill}
          <input type="number" min="1" max="99" {...register(`levels.${skill}`, {
            valueAsNumber: true, min: 1, max: 99,
          })} />
        </label>)}</div>
      </section>

      <section>
        <h3>Unlocks</h3>
        <label className="check-row"><input type="checkbox" {...register("fairyRingsUnlocked")} /> Fairy rings</label>
        <fieldset><legend>Planted spirit trees</legend>
          {spiritTrees.map(([value, label]) => <label className="check-row" key={value}>
            <input type="checkbox" value={value} {...register("plantedSpiritTrees")} /> {label}
          </label>)}
        </fieldset>
      </section>

      <section>
        <h3>Player-owned house</h3>
        <label>Jewellery box <select {...register("poh.jewelleryBox")}>
          <option value="NoJewelleryBox">None</option>
          <option value="FancyJewelleryBox">Fancy</option>
          <option value="OrnateJewelleryBox">Ornate</option>
        </select></label>
        <div className="check-grid">
          <label className="check-row"><input type="checkbox" {...register("poh.fairyRing")} /> Fairy ring</label>
          <label className="check-row"><input type="checkbox" {...register("poh.spiritTree")} /> Spirit tree</label>
          <label className="check-row"><input type="checkbox" {...register("poh.obelisk")} /> Obelisk</label>
          <label className="check-row"><input type="checkbox" {...register("poh.mountedGlory")} /> Mounted glory</label>
          <label className="check-row"><input type="checkbox" {...register("poh.mountedXerics")} /> Xeric's talisman</label>
          <label className="check-row"><input type="checkbox" {...register("poh.mountedDigsite")} /> Digsite pendant</label>
          <label className="check-row"><input type="checkbox" {...register("poh.mountedMythical")} /> Mythical cape</label>
        </div>
        <label>Portal nexus <select {...register("poh.portals.mode")}>
          <option value="selected">Preset destinations</option><option value="all">All destinations</option>
        </select></label>
      </section>

      <section>
        <h3>Completed quests</h3>
        <input type="search" value={questFilter} onChange={event => setQuestFilter(event.target.value)}
          placeholder="Filter quests" aria-label="Filter quests" />
        <fieldset className="quest-list"><legend className="sr-only">Completed quests</legend>
          {visibleQuests.map(quest => <label className="check-row" key={quest}>
            <input type="checkbox" value={quest} {...register("completedQuests")} /> {quest}
          </label>)}
        </fieldset>
      </section>

      <footer>
        <button type="button" className="secondary-button" onClick={onClose}>Cancel</button>
        <button type="submit" className="primary-button">Save custom build</button>
      </footer>
    </form>
  </dialog>;
}
