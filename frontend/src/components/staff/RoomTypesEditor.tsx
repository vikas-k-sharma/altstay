'use client';

import { useState, FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import { consoleFetch } from '@/lib/staff/clientFetch';
import { formatMinor, parseMajor, minorToMajorInput } from '@/lib/staff/money';
import type { RoomTypeDto } from '@/lib/contracts/inventory';

type FormState = {
  code: string;
  name: string;
  saleMode: 'PER_UNIT' | 'WHOLE';
  kind: 'DORM' | 'PRIVATE';
  maxOccupancy: string;
  baseRateMajor: string;
  description: string;
  isActive: boolean;
};

function emptyForm(): FormState {
  return {
    code: '',
    name: '',
    saleMode: 'PER_UNIT',
    kind: 'DORM',
    maxOccupancy: '1',
    baseRateMajor: '0',
    description: '',
    isActive: true,
  };
}

function toForm(rt: RoomTypeDto, currencyCode: string): FormState {
  return {
    code: rt.code,
    name: rt.name,
    saleMode: rt.saleMode,
    kind: rt.kind,
    maxOccupancy: String(rt.maxOccupancy),
    baseRateMajor: minorToMajorInput(rt.baseRateMinor, currencyCode),
    description: rt.description ?? '',
    isActive: rt.isActive,
  };
}

const inputClass = 'rounded-lg border border-border bg-surface px-2 py-1 text-sm';
const labelClass = 'block text-xs font-medium text-text-muted';

function RoomTypeFields({
  idPrefix,
  form,
  onChange,
}: {
  idPrefix: string;
  form: FormState;
  onChange: (form: FormState) => void;
}) {
  const id = (name: string) => `${idPrefix}-${name}`;
  return (
    <div className="flex flex-wrap items-end gap-3">
      <div>
        <label className={labelClass} htmlFor={id('code')}>
          Code
        </label>
        <input
          id={id('code')}
          className={inputClass}
          value={form.code}
          onChange={(e) => onChange({ ...form, code: e.target.value })}
        />
      </div>
      <div>
        <label className={labelClass} htmlFor={id('name')}>
          Name
        </label>
        <input
          id={id('name')}
          className={inputClass}
          value={form.name}
          onChange={(e) => onChange({ ...form, name: e.target.value })}
        />
      </div>
      <div>
        <label className={labelClass} htmlFor={id('saleMode')}>
          Sale mode
        </label>
        <select
          id={id('saleMode')}
          className={inputClass}
          value={form.saleMode}
          onChange={(e) => onChange({ ...form, saleMode: e.target.value as FormState['saleMode'] })}
        >
          <option value="PER_UNIT">Per unit (dorm beds)</option>
          <option value="WHOLE">Whole (private room)</option>
        </select>
      </div>
      <div>
        <label className={labelClass} htmlFor={id('kind')}>
          Kind
        </label>
        <select
          id={id('kind')}
          className={inputClass}
          value={form.kind}
          onChange={(e) => onChange({ ...form, kind: e.target.value as FormState['kind'] })}
        >
          <option value="DORM">Dorm</option>
          <option value="PRIVATE">Private</option>
        </select>
      </div>
      <div>
        <label className={labelClass} htmlFor={id('maxOccupancy')}>
          Max occupancy
        </label>
        <input
          id={id('maxOccupancy')}
          type="number"
          min={1}
          className={`w-20 ${inputClass}`}
          value={form.maxOccupancy}
          onChange={(e) => onChange({ ...form, maxOccupancy: e.target.value })}
        />
      </div>
      <div>
        <label className={labelClass} htmlFor={id('baseRateMajor')}>
          Base rate
        </label>
        <input
          id={id('baseRateMajor')}
          className={`w-24 ${inputClass}`}
          value={form.baseRateMajor}
          onChange={(e) => onChange({ ...form, baseRateMajor: e.target.value })}
        />
      </div>
      <div>
        <label className={labelClass} htmlFor={id('description')}>
          Description
        </label>
        <input
          id={id('description')}
          className={inputClass}
          value={form.description}
          onChange={(e) => onChange({ ...form, description: e.target.value })}
        />
      </div>
      <label className="flex items-center gap-1 text-sm">
        <input
          type="checkbox"
          checked={form.isActive}
          onChange={(e) => onChange({ ...form, isActive: e.target.checked })}
        />
        Active
      </label>
    </div>
  );
}

export function RoomTypesEditor({
  propertySlug,
  currencyCode,
  roomTypes,
}: {
  propertySlug: string;
  currencyCode: string;
  roomTypes: RoomTypeDto[];
}) {
  const router = useRouter();
  // `roomTypes` is the source of truth, refreshed by the parent page via router.refresh() after
  // every mutation below — no local copy to keep in sync, so nothing to desync either.
  const list = roomTypes;
  const [creating, setCreating] = useState<FormState>(emptyForm());
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<FormState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const response = await consoleFetch(`/api/console/properties/${propertySlug}/room-types`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          code: creating.code,
          name: creating.name,
          saleMode: creating.saleMode,
          kind: creating.kind,
          maxOccupancy: Number(creating.maxOccupancy),
          baseRateMinor: parseMajor(creating.baseRateMajor, currencyCode),
          description: creating.description.trim() || null,
          isActive: creating.isActive,
        }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => undefined);
        setError(body?.detail ?? 'Could not create the room type.');
        return;
      }
      setCreating(emptyForm());
      router.refresh();
    } finally {
      setSubmitting(false);
    }
  }

  async function handleSaveEdit(id: string) {
    if (!editForm) return;
    setSubmitting(true);
    setError(null);
    try {
      const response = await consoleFetch(`/api/console/properties/${propertySlug}/room-types/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: editForm.name,
          saleMode: editForm.saleMode,
          kind: editForm.kind,
          maxOccupancy: Number(editForm.maxOccupancy),
          baseRateMinor: parseMajor(editForm.baseRateMajor, currencyCode),
          description: editForm.description.trim() || null,
          isActive: editForm.isActive,
        }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => undefined);
        setError(body?.detail ?? 'Could not save the room type.');
        return;
      }
      setEditingId(null);
      setEditForm(null);
      router.refresh();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="space-y-4">
      {/* saleMode is how capacity is consumed, kind is what the guest thinks they're buying
          (phase-6 §4.8) — a whole-dorm buyout (WHOLE + DORM) is the case that makes the
          distinction land: sold as one unit, but the guest is still buying beds. */}
      <p className="text-sm text-text-muted">
        Sale mode is how capacity is consumed (per bed, or the whole space at once); kind is what
        the guest thinks they&apos;re buying. A whole-dorm buyout is <strong>WHOLE</strong> +{' '}
        <strong>DORM</strong>: sold as one unit, but the guest is still buying beds.
      </p>

      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-text-muted">
            <th className="p-2">Code</th>
            <th className="p-2">Name</th>
            <th className="p-2">Sale mode</th>
            <th className="p-2">Kind</th>
            <th className="p-2">Rate</th>
            <th className="p-2">Active</th>
            <th className="p-2" />
          </tr>
        </thead>
        <tbody>
          {list.map((rt) =>
            editingId === rt.id && editForm ? (
              <tr key={rt.id} className="border-t border-border">
                <td colSpan={7} className="p-2">
                  <RoomTypeFields idPrefix={`edit-${rt.id}`} form={editForm} onChange={setEditForm} />
                  <div className="mt-2 flex gap-2">
                    <button
                      type="button"
                      disabled={submitting}
                      onClick={() => handleSaveEdit(rt.id)}
                      className="rounded-lg bg-accent px-3 py-1 text-sm text-white disabled:opacity-60"
                    >
                      Save
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setEditingId(null);
                        setEditForm(null);
                      }}
                      className="text-sm text-text-muted underline"
                    >
                      Cancel
                    </button>
                  </div>
                </td>
              </tr>
            ) : (
              <tr key={rt.id} className="border-t border-border">
                <td className="p-2">{rt.code}</td>
                <td className="p-2">{rt.name}</td>
                <td className="p-2">{rt.saleMode}</td>
                <td className="p-2">{rt.kind}</td>
                <td className="p-2">{formatMinor(rt.baseRateMinor, currencyCode)}</td>
                <td className="p-2">{rt.isActive ? 'Yes' : 'No'}</td>
                <td className="p-2">
                  <button
                    type="button"
                    onClick={() => {
                      setEditingId(rt.id);
                      setEditForm(toForm(rt, currencyCode));
                    }}
                    className="text-sm text-accent hover:underline"
                  >
                    Edit
                  </button>
                </td>
              </tr>
            )
          )}
        </tbody>
      </table>

      <form onSubmit={handleCreate} className="space-y-2 rounded-lg border border-border p-3">
        <h3 className="text-sm font-semibold">Add room type</h3>
        <RoomTypeFields idPrefix="create" form={creating} onChange={setCreating} />
        <button
          type="submit"
          disabled={submitting}
          className="rounded-lg bg-accent px-3 py-1.5 text-sm font-semibold text-white disabled:opacity-60"
        >
          {submitting ? 'Saving…' : 'Add room type'}
        </button>
      </form>

      {error && (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
