import { renderOgImage, OG_SIZE, OG_CONTENT_TYPE } from '@/lib/marketing/ogImage';

export const alt = 'AltStay — Property Management for Hybrid Stays';
export const size = OG_SIZE;
export const contentType = OG_CONTENT_TYPE;

export default function Image() {
  return renderOgImage('Property management', 'A PMS built for hostels, not hotels wearing a hostel skin.');
}
