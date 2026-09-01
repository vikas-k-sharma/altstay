import { renderOgImage, OG_SIZE, OG_CONTENT_TYPE } from '@/lib/marketing/ogImage';

export const alt = 'AltStay Product';
export const size = OG_SIZE;
export const contentType = OG_CONTENT_TYPE;

export default function Image() {
  return renderOgImage('Product', 'Concierge, inventory and bookings on one inventory model.');
}
