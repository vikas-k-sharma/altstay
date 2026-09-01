import type { MetadataRoute } from 'next';
import { SITE_URL } from '@/lib/marketing/site';

// phase-7 §8.2 — a demo and a login screen have no business in search results.
export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: '*',
      allow: '/',
      disallow: ['/concierge', '/console'],
    },
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}
