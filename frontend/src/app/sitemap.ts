import type { MetadataRoute } from 'next';
import { SITE_URL } from '@/lib/marketing/site';

// phase-7 §8.2 — exactly the four public marketing routes. /concierge and /console stay out.
export default function sitemap(): MetadataRoute.Sitemap {
  return [
    { url: `${SITE_URL}/`, changeFrequency: 'monthly', priority: 1 },
    { url: `${SITE_URL}/product`, changeFrequency: 'monthly', priority: 0.8 },
    { url: `${SITE_URL}/about`, changeFrequency: 'monthly', priority: 0.5 },
    { url: `${SITE_URL}/contact`, changeFrequency: 'monthly', priority: 0.5 },
  ];
}
