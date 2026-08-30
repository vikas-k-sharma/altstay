'use client';

import { useState, FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import { consoleFetch } from '@/lib/staff/clientFetch';
import { percentToBps, bpsToPercent } from '@/lib/staff/money';
import { CURATED_CURRENCIES } from '@/lib/staff/currencies';
import type { PropertyResponse } from '@/lib/contracts/property';
import type { AmenityResponse } from '@/lib/contracts/amenity';

// Real pickers, not free text (phase-6 §4.7) — timezone and currency are the two fields phase-5
// §2 gave no default for, precisely because a wrong one looks right and a text input is how a
// wrong one gets typed. Computed once at module scope; the set doesn't change at runtime.
const TIMEZONES = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : [];

type FormState = {
  name: string;
  legalName: string;
  description: string;
  status: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  stateRegion: string;
  postalCode: string;
  countryCode: string;
  contactEmail: string;
  contactPhone: string;
  timezone: string;
  currencyCode: string;
  checkInTime: string;
  checkOutTime: string;
  taxRatePercent: string;
  amenities: string[];
};

function toFormState(property: PropertyResponse): FormState {
  return {
    name: property.name,
    legalName: property.legalName ?? '',
    description: property.description ?? '',
    status: property.status,
    addressLine1: property.addressLine1 ?? '',
    addressLine2: property.addressLine2 ?? '',
    city: property.city ?? '',
    stateRegion: property.stateRegion ?? '',
    postalCode: property.postalCode ?? '',
    countryCode: property.countryCode ?? '',
    contactEmail: property.contactEmail ?? '',
    contactPhone: property.contactPhone ?? '',
    timezone: property.timezone,
    currencyCode: property.currencyCode,
    checkInTime: property.checkInTime.slice(0, 5),
    checkOutTime: property.checkOutTime.slice(0, 5),
    taxRatePercent: bpsToPercent(property.taxRateBps),
    amenities: property.amenities,
  };
}

export function PropertySettingsForm({
  property,
  amenities,
}: {
  property: PropertyResponse;
  amenities: AmenityResponse[];
}) {
  const router = useRouter();
  const [form, setForm] = useState<FormState>(() => toFormState(property));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
    setSaved(false);
  }

  function toggleAmenity(code: string) {
    setForm((prev) => ({
      ...prev,
      amenities: prev.amenities.includes(code)
        ? prev.amenities.filter((c) => c !== code)
        : [...prev.amenities, code],
    }));
    setSaved(false);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      let taxRateBps: number;
      try {
        taxRateBps = percentToBps(form.taxRatePercent);
      } catch {
        setError('Tax rate must be a number.');
        return;
      }

      // Load-modify-save, not a patch (phase-6 §4.7) — every field is submitted, including the
      // ones the user didn't touch.
      const response = await consoleFetch(`/api/console/properties/${property.slug}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: form.name,
          legalName: form.legalName.trim() || null,
          description: form.description.trim() || null,
          status: form.status,
          timezone: form.timezone,
          currencyCode: form.currencyCode,
          countryCode: form.countryCode.trim() || null,
          addressLine1: form.addressLine1.trim() || null,
          addressLine2: form.addressLine2.trim() || null,
          city: form.city.trim() || null,
          stateRegion: form.stateRegion.trim() || null,
          postalCode: form.postalCode.trim() || null,
          contactEmail: form.contactEmail.trim() || null,
          contactPhone: form.contactPhone.trim() || null,
          checkInTime: form.checkInTime,
          checkOutTime: form.checkOutTime,
          taxRateBps,
          amenities: form.amenities,
        }),
      });

      if (!response.ok) {
        const body = await response.json().catch(() => undefined);
        setError(body?.detail ?? 'Could not save. Please try again.');
        return;
      }

      setSaved(true);
      router.refresh();
    } finally {
      setSubmitting(false);
    }
  }

  const amenitiesByCategory = new Map<string, AmenityResponse[]>();
  for (const amenity of amenities) {
    const list = amenitiesByCategory.get(amenity.category) ?? [];
    list.push(amenity);
    amenitiesByCategory.set(amenity.category, list);
  }

  const inputClass = 'w-full rounded-lg border border-border bg-surface px-2 py-1 text-sm';
  const labelClass = 'block text-xs font-medium text-text-muted';

  return (
    <form onSubmit={handleSubmit} className="max-w-2xl space-y-6" noValidate>
      <fieldset className="space-y-3">
        <legend className="font-semibold">Identity</legend>
        <div>
          <label className={labelClass} htmlFor="name">
            Name
          </label>
          <input id="name" className={inputClass} value={form.name} onChange={(e) => update('name', e.target.value)} />
        </div>
        <div>
          <label className={labelClass} htmlFor="legalName">
            Legal name
          </label>
          <input
            id="legalName"
            className={inputClass}
            value={form.legalName}
            onChange={(e) => update('legalName', e.target.value)}
          />
        </div>
        <div>
          <label className={labelClass} htmlFor="slug">
            Slug
          </label>
          {/* Read-only after creation (phase-6 §4.7) */}
          <input id="slug" className={inputClass} value={property.slug} disabled readOnly />
        </div>
        <div>
          <label className={labelClass} htmlFor="description">
            Description
          </label>
          <textarea
            id="description"
            className={inputClass}
            value={form.description}
            onChange={(e) => update('description', e.target.value)}
          />
        </div>
        <div>
          <label className={labelClass} htmlFor="status">
            Status
          </label>
          <select id="status" className={inputClass} value={form.status} onChange={(e) => update('status', e.target.value)}>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
          </select>
        </div>
      </fieldset>

      <fieldset className="space-y-3">
        <legend className="font-semibold">Location</legend>
        <div>
          <label className={labelClass} htmlFor="addressLine1">
            Address line 1
          </label>
          <input
            id="addressLine1"
            className={inputClass}
            value={form.addressLine1}
            onChange={(e) => update('addressLine1', e.target.value)}
          />
        </div>
        <div>
          <label className={labelClass} htmlFor="addressLine2">
            Address line 2
          </label>
          <input
            id="addressLine2"
            className={inputClass}
            value={form.addressLine2}
            onChange={(e) => update('addressLine2', e.target.value)}
          />
        </div>
        <div className="flex gap-3">
          <div>
            <label className={labelClass} htmlFor="city">
              City
            </label>
            <input id="city" className={inputClass} value={form.city} onChange={(e) => update('city', e.target.value)} />
          </div>
          <div>
            <label className={labelClass} htmlFor="stateRegion">
              State/region
            </label>
            <input
              id="stateRegion"
              className={inputClass}
              value={form.stateRegion}
              onChange={(e) => update('stateRegion', e.target.value)}
            />
          </div>
          <div>
            <label className={labelClass} htmlFor="postalCode">
              Postal code
            </label>
            <input
              id="postalCode"
              className={inputClass}
              value={form.postalCode}
              onChange={(e) => update('postalCode', e.target.value)}
            />
          </div>
          <div>
            <label className={labelClass} htmlFor="countryCode">
              Country code
            </label>
            <input
              id="countryCode"
              maxLength={2}
              className={`${inputClass} uppercase`}
              value={form.countryCode}
              onChange={(e) => update('countryCode', e.target.value.toUpperCase())}
            />
          </div>
        </div>
      </fieldset>

      <fieldset className="space-y-3">
        <legend className="font-semibold">Contact</legend>
        <div className="flex gap-3">
          <div>
            <label className={labelClass} htmlFor="contactEmail">
              Contact email
            </label>
            <input
              id="contactEmail"
              type="email"
              className={inputClass}
              value={form.contactEmail}
              onChange={(e) => update('contactEmail', e.target.value)}
            />
          </div>
          <div>
            <label className={labelClass} htmlFor="contactPhone">
              Contact phone
            </label>
            <input
              id="contactPhone"
              className={inputClass}
              value={form.contactPhone}
              onChange={(e) => update('contactPhone', e.target.value)}
            />
          </div>
        </div>
      </fieldset>

      <fieldset className="space-y-3">
        <legend className="font-semibold">Operations</legend>
        <div className="flex gap-3">
          <div>
            <label className={labelClass} htmlFor="timezone">
              Timezone
            </label>
            <select
              id="timezone"
              className={inputClass}
              value={form.timezone}
              onChange={(e) => update('timezone', e.target.value)}
            >
              {TIMEZONES.map((tz) => (
                <option key={tz} value={tz}>
                  {tz}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className={labelClass} htmlFor="currencyCode">
              Currency
            </label>
            <select
              id="currencyCode"
              className={inputClass}
              value={form.currencyCode}
              onChange={(e) => update('currencyCode', e.target.value)}
            >
              {CURATED_CURRENCIES.map((code) => (
                <option key={code} value={code}>
                  {code}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="flex gap-3">
          <div>
            <label className={labelClass} htmlFor="checkInTime">
              Check-in time
            </label>
            <input
              id="checkInTime"
              type="time"
              className={inputClass}
              value={form.checkInTime}
              onChange={(e) => update('checkInTime', e.target.value)}
            />
          </div>
          <div>
            <label className={labelClass} htmlFor="checkOutTime">
              Check-out time
            </label>
            <input
              id="checkOutTime"
              type="time"
              className={inputClass}
              value={form.checkOutTime}
              onChange={(e) => update('checkOutTime', e.target.value)}
            />
          </div>
          <div>
            <label className={labelClass} htmlFor="taxRatePercent">
              Tax rate (%)
            </label>
            <input
              id="taxRatePercent"
              className={inputClass}
              value={form.taxRatePercent}
              onChange={(e) => update('taxRatePercent', e.target.value)}
            />
          </div>
        </div>
      </fieldset>

      <fieldset className="space-y-3">
        <legend className="font-semibold">Amenities</legend>
        {[...amenitiesByCategory.entries()].map(([category, list]) => (
          <div key={category}>
            <h4 className="text-xs font-semibold text-text-muted">{category}</h4>
            <div className="flex flex-wrap gap-3">
              {list.map((amenity) => (
                <label key={amenity.code} className="flex items-center gap-1 text-sm">
                  <input
                    type="checkbox"
                    checked={form.amenities.includes(amenity.code)}
                    onChange={() => toggleAmenity(amenity.code)}
                  />
                  {amenity.label}
                </label>
              ))}
            </div>
          </div>
        ))}
      </fieldset>

      {error && (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}
      {saved && !error && <p className="text-sm text-success">Saved.</p>}

      <button
        type="submit"
        disabled={submitting}
        className="rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
      >
        {submitting ? 'Saving…' : 'Save'}
      </button>
    </form>
  );
}
