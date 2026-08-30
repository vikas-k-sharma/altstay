'use client';

import { useState, FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import { consoleFetch } from '@/lib/staff/clientFetch';
import type { SpaceDto, CreateUnitRequest } from '@/lib/contracts/inventory';

const UNIT_KINDS = ['SINGLE', 'BUNK_TOP', 'BUNK_BOTTOM', 'DOUBLE'] as const;
const inputClass = 'rounded-lg border border-border bg-surface px-2 py-1 text-sm';
const labelClass = 'block text-xs font-medium text-text-muted';

function emptyUnit(): CreateUnitRequest {
  return { label: '', unitKind: 'SINGLE', isActive: true };
}

function UnitRows({
  idPrefix,
  units,
  onChange,
}: {
  idPrefix: string;
  units: CreateUnitRequest[];
  onChange: (units: CreateUnitRequest[]) => void;
}) {
  return (
    <div className="space-y-2">
      {units.map((unit, index) => (
        <div key={index} className="flex items-end gap-2">
          <div>
            <label className={labelClass} htmlFor={`${idPrefix}-label-${index}`}>
              Label
            </label>
            <input
              id={`${idPrefix}-label-${index}`}
              className={inputClass}
              value={unit.label}
              onChange={(e) => {
                const next = [...units];
                next[index] = { ...unit, label: e.target.value };
                onChange(next);
              }}
            />
          </div>
          <div>
            <label className={labelClass} htmlFor={`${idPrefix}-kind-${index}`}>
              Kind
            </label>
            <select
              id={`${idPrefix}-kind-${index}`}
              className={inputClass}
              value={unit.unitKind}
              onChange={(e) => {
                const next = [...units];
                next[index] = { ...unit, unitKind: e.target.value as CreateUnitRequest['unitKind'] };
                onChange(next);
              }}
            >
              {UNIT_KINDS.map((kind) => (
                <option key={kind} value={kind}>
                  {kind}
                </option>
              ))}
            </select>
          </div>
          <label className="flex items-center gap-1 text-sm">
            <input
              type="checkbox"
              checked={unit.isActive ?? true}
              onChange={(e) => {
                const next = [...units];
                next[index] = { ...unit, isActive: e.target.checked };
                onChange(next);
              }}
            />
            Active
          </label>
          <button
            type="button"
            onClick={() => onChange(units.filter((_, i) => i !== index))}
            className="text-sm text-danger underline"
          >
            Remove
          </button>
        </div>
      ))}
      <button
        type="button"
        onClick={() => onChange([...units, emptyUnit()])}
        className="text-sm text-accent underline"
      >
        + Add bed
      </button>
    </div>
  );
}

function SpaceStatusForm({
  space,
  propertySlug,
  onSaved,
}: {
  space: SpaceDto;
  propertySlug: string;
  onSaved: () => void;
}) {
  const [name, setName] = useState(space.name);
  const [floor, setFloor] = useState(space.floor ?? '');
  const [isActive, setIsActive] = useState(space.isActive);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSave() {
    setSubmitting(true);
    setError(null);
    try {
      // units: null — this form never replaces beds (phase-6 §12.1).
      const response = await consoleFetch(`/api/console/properties/${propertySlug}/spaces/${space.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, floor: floor.trim() || null, isActive, units: null }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => undefined);
        setError(body?.detail ?? 'Could not save.');
        return;
      }
      onSaved();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-wrap items-end gap-3">
      <div>
        <label className={labelClass} htmlFor={`status-name-${space.id}`}>
          Name
        </label>
        <input
          id={`status-name-${space.id}`}
          className={inputClass}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
      </div>
      <div>
        <label className={labelClass} htmlFor={`status-floor-${space.id}`}>
          Floor
        </label>
        <input
          id={`status-floor-${space.id}`}
          className={inputClass}
          value={floor}
          onChange={(e) => setFloor(e.target.value)}
        />
      </div>
      <label className="flex items-center gap-1 text-sm">
        <input type="checkbox" checked={isActive} onChange={(e) => setIsActive(e.target.checked)} />
        Active
      </label>
      <button
        type="button"
        disabled={submitting}
        onClick={handleSave}
        className="rounded-lg bg-accent px-3 py-1 text-sm text-white disabled:opacity-60"
      >
        Save
      </button>
      {error && (
        <p role="alert" className="w-full text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

function ManageBeds({
  space,
  propertySlug,
  onSaved,
}: {
  space: SpaceDto;
  propertySlug: string;
  onSaved: () => void;
}) {
  const [units, setUnits] = useState<CreateUnitRequest[]>(
    space.units.map((u) => ({ label: u.label, unitKind: u.unitKind, isActive: u.isActive }))
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSave() {
    if (units.length === 0) {
      setError('A space must have at least one bed.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const response = await consoleFetch(`/api/console/properties/${propertySlug}/spaces/${space.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: space.name, floor: space.floor, isActive: space.isActive, units }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => undefined);
        setError(body?.detail ?? 'Could not save the beds.');
        return;
      }
      onSaved();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="space-y-2 rounded-lg border border-warning/40 bg-surface-muted p-2">
      <p className="text-xs text-warning">
        Saving here replaces every bed in this room with a new record. It does not preserve which
        bed a current or past guest actually held — check the booking detail for anyone staying
        here before changing bed counts.
      </p>
      <UnitRows idPrefix={`manage-beds-${space.id}`} units={units} onChange={setUnits} />
      <button
        type="button"
        disabled={submitting}
        onClick={handleSave}
        className="rounded-lg bg-accent px-3 py-1 text-sm text-white disabled:opacity-60"
      >
        Save beds
      </button>
      {error && (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

export function SpacesEditor({ propertySlug, spaces }: { propertySlug: string; spaces: SpaceDto[] }) {
  const router = useRouter();
  // `spaces` is the source of truth, refreshed by the parent page via router.refresh() after
  // every mutation below — no local copy to keep in sync, so nothing to desync either.
  const list = spaces;
  const [editingId, setEditingId] = useState<string | null>(null);
  const [managingBedsId, setManagingBedsId] = useState<string | null>(null);
  const [creatingUnits, setCreatingUnits] = useState<CreateUnitRequest[]>([emptyUnit()]);
  const [creatingName, setCreatingName] = useState('');
  const [creatingFloor, setCreatingFloor] = useState('');
  const [createError, setCreateError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function afterSave() {
    setEditingId(null);
    setManagingBedsId(null);
    router.refresh();
  }

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setCreateError(null);
    try {
      const response = await consoleFetch(`/api/console/properties/${propertySlug}/spaces`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: creatingName,
          floor: creatingFloor.trim() || null,
          isActive: true,
          units: creatingUnits,
        }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => undefined);
        setCreateError(body?.detail ?? 'Could not create the space.');
        return;
      }
      setCreatingName('');
      setCreatingFloor('');
      setCreatingUnits([emptyUnit()]);
      router.refresh();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="space-y-4">
      <ul className="space-y-3">
        {list.map((space) => (
          <li key={space.id} className="rounded-lg border border-border p-3">
            <div className="flex items-center justify-between">
              <div>
                <span className="font-semibold">{space.name}</span>
                {space.floor && <span className="text-text-muted"> · floor {space.floor}</span>}
                <span className="text-text-muted"> · {space.capacity} bed(s)</span>
                {!space.isActive && <span className="text-text-muted"> · inactive</span>}
              </div>
              <div className="flex gap-2 text-sm">
                <button
                  type="button"
                  onClick={() => setEditingId(editingId === space.id ? null : space.id)}
                  className="text-accent hover:underline"
                >
                  Edit
                </button>
                <button
                  type="button"
                  onClick={() => setManagingBedsId(managingBedsId === space.id ? null : space.id)}
                  className="text-accent hover:underline"
                >
                  Manage beds
                </button>
              </div>
            </div>

            {space.capacity === 0 && (
              <p className="mt-1 text-sm text-warning">
                This space has no active beds and can never be sold.
              </p>
            )}

            {editingId === space.id && (
              <div className="mt-2">
                <SpaceStatusForm space={space} propertySlug={propertySlug} onSaved={afterSave} />
              </div>
            )}

            {managingBedsId === space.id && (
              <div className="mt-2">
                <ManageBeds space={space} propertySlug={propertySlug} onSaved={afterSave} />
              </div>
            )}
          </li>
        ))}
      </ul>

      <form onSubmit={handleCreate} className="space-y-2 rounded-lg border border-border p-3">
        <h3 className="text-sm font-semibold">Add space</h3>
        <div className="flex gap-3">
          <div>
            <label className={labelClass} htmlFor="new-space-name">
              Name
            </label>
            <input
              id="new-space-name"
              className={inputClass}
              value={creatingName}
              onChange={(e) => setCreatingName(e.target.value)}
            />
          </div>
          <div>
            <label className={labelClass} htmlFor="new-space-floor">
              Floor
            </label>
            <input
              id="new-space-floor"
              className={inputClass}
              value={creatingFloor}
              onChange={(e) => setCreatingFloor(e.target.value)}
            />
          </div>
        </div>
        <UnitRows idPrefix="new-space" units={creatingUnits} onChange={setCreatingUnits} />
        <button
          type="submit"
          disabled={submitting}
          className="rounded-lg bg-accent px-3 py-1.5 text-sm font-semibold text-white disabled:opacity-60"
        >
          {submitting ? 'Saving…' : 'Add space'}
        </button>
        {createError && (
          <p role="alert" className="text-sm text-danger">
            {createError}
          </p>
        )}
      </form>
    </div>
  );
}
