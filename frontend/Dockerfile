# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Dependency stage. Split from the build so an edit under src/ reuses the
# cached npm install instead of re-resolving the whole tree.
# `npm ci` (not `install`) so the image is built from package-lock.json
# exactly — an image that quietly floats a dependency is not reproducible.
# ---------------------------------------------------------------------------
FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# ---------------------------------------------------------------------------
# Build stage.
# ---------------------------------------------------------------------------
FROM node:24-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# BACKEND_URL is read at REQUEST time by src/lib/server/session.ts, never at
# build time — but that module throws when it is unset, and `next build`
# imports it while collecting page data. This placeholder only has to exist;
# it is never dialled, and the real value arrives as an ACA env var. Do not
# put a real hostname here: it would be baked into the image.
ENV BACKEND_URL="http://build-time-placeholder.invalid"

# The origin this build serves from. Build-time, not runtime: metadataBase and
# the static robots.txt / sitemap.xml routes are evaluated during `next build`.
# Left unset it defaults to https://altstay.in and the build declares itself
# indexable — so CI must pass the real ACA hostname, or a staging deploy will
# publish canonicals for a domain it is not served from.
ARG ALTSTAY_SITE_URL
ENV ALTSTAY_SITE_URL=${ALTSTAY_SITE_URL}

ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# ---------------------------------------------------------------------------
# Runtime stage. Ships .next/standalone only — see next.config.ts.
# ---------------------------------------------------------------------------
FROM node:24-alpine AS runtime
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

RUN addgroup --system --gid 1001 nodejs \
 && adduser --system --uid 1001 --ingroup nodejs nextjs

# public/ and .next/static are NOT bundled into standalone by design — Next
# expects them copied alongside. Omitting either yields a server that boots
# clean and then serves a page with no CSS and no images.
COPY --from=build --chown=nextjs:nodejs /app/public ./public
COPY --from=build --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000

ENV PORT=3000
# 0.0.0.0, not the localhost default: a container listening on loopback is
# unreachable from the ACA ingress and presents as an endless 503.
ENV HOSTNAME=0.0.0.0

CMD ["node", "server.js"]
