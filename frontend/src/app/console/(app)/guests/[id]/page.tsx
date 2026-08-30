import Link from 'next/link';
import { notFound } from 'next/navigation';
import { requirePropertyContext } from '@/lib/server/property';
import { upstream } from '@/lib/server/session';
import { GuestDtoSchema } from '@/lib/contracts/booking';
import { GuestForm } from '@/components/staff/GuestForm';

export default async function GuestDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const { session } = await requirePropertyContext(`/console/guests/${id}`);

  const response = await upstream(`/api/v1/guests/${id}`, { cookieHeader: session.cookieHeader });
  if (response.status === 404) {
    notFound();
  }
  const guest = GuestDtoSchema.parse(await response.json());

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold">{guest.fullName}</h1>
        <Link href="/console/guests" className="text-sm text-accent hover:underline">
          ← Guests
        </Link>
      </div>
      <GuestForm guest={guest} />
    </div>
  );
}
