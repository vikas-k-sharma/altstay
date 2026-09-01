import { renderOgImage, OG_SIZE, OG_CONTENT_TYPE } from '@/lib/marketing/ogImage';

export const alt = 'Contact AltStay';
export const size = OG_SIZE;
export const contentType = OG_CONTENT_TYPE;

export default function Image() {
  return renderOgImage('Contact', 'No form. WhatsApp for the fastest route, email for the rest.');
}
