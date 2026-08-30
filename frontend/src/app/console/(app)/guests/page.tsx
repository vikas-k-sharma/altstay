import Link from 'next/link';
import { requirePropertyContext } from '@/lib/server/property';
import { upstream } from '@/lib/server/session';
import { GuestDtoSchema } from '@/lib/contracts/booking';
import { GuestForm } from '@/components/staff/GuestForm';

export default async function GuestsPage() {
  const { session } = await requirePropertyContext('/console/guests');

  const response = await upstream('/api/v1/guests', { cookieHeader: session.cookieHeader });
  const guests = response.ok ? GuestDtoSchema.array().parse(await response.json()) : [];

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">Guests</h1>

      <GuestForm />

      {guests.length === 0 ? (
        <p className="text-sm text-text-muted">No guests yet.</p>
      ) : (
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-text-muted">
              <th className="p-2">Name</th>
              <th className="p-2">Email</th>
              <th className="p-2">Phone</th>
            </tr>
          </thead>
          <tbody>
            {guests.map((guest) => (
              <tr key={guest.id} className="border-t border-border">
                <td className="p-2">
                  <Link href={`/console/guests/${guest.id}`} className="text-accent hover:underline">
                    {guest.fullName}
                  </Link>
                </td>
                <td className="p-2">{guest.email ?? '—'}</td>
                <td className="p-2">{guest.phone ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
