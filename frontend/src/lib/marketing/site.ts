// Single source of truth for the production origin — used by sitemap.ts, robots.ts and
// per-page metadata canonicals. Matches the domain already implied by CONTACT_EMAIL
// (hello@altstay.in) and the contact page's own "altstay.in/console" reference.
export const CANONICAL_SITE_URL = 'https://altstay.in';

/**
 * The origin this build actually serves from. Overridden by `ALTSTAY_SITE_URL` at BUILD time
 * (a Docker build arg — see frontend/Dockerfile), not at runtime: `metadataBase` and the static
 * robots.txt/sitemap.xml routes are evaluated during `next build`, so a runtime-only variable
 * would be read after the values it feeds are already baked into the output.
 */
export const SITE_URL = process.env.ALTSTAY_SITE_URL || CANONICAL_SITE_URL;

/**
 * Whether this build is the real altstay.in and may be indexed. Fails closed: any other origin —
 * a *.azurecontainerapps.io staging host, a preview build — is treated as not-for-search rather
 * than advertising canonicals to a domain it is not served from.
 */
export const IS_CANONICAL_HOST = SITE_URL === CANONICAL_SITE_URL;
