import { renderOgImage, OG_SIZE, OG_CONTENT_TYPE } from '@/lib/marketing/ogImage';

export const alt = 'About AltStay';
export const size = OG_SIZE;
export const contentType = OG_CONTENT_TYPE;

export default function Image() {
  return renderOgImage('About', 'Software built for hybrid stays, not hotel software with a new label on it.');
}
