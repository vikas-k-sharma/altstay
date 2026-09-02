import type { MetadataRoute } from 'next';
import { SITE_URL, IS_CANONICAL_HOST } from '@/lib/marketing/site';

// phase-7 §8.2 — a demo and a login screen have no business in search results.
export default function robots(): MetadataRoute.Robots {
  // A non-canonical host (staging on *.azurecontainerapps.io, a preview build) advertises
  // canonicals for a domain it is not served from, so it is withheld from crawlers entirely
  // rather than competing with altstay.in for its own pages.
  if (!IS_CANONICAL_HOST) {
    return {
      rules: { userAgent: '*', disallow: '/' },
    };
  }

  return {
    rules: {
      userAgent: '*',
      allow: '/',
      disallow: ['/concierge', '/console'],
    },
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}
