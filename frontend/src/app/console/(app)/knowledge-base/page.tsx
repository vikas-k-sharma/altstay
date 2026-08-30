import { requirePropertyContext } from '@/lib/server/property';
import { upstream } from '@/lib/server/session';
import { KnowledgeBaseVersionResponseSchema } from '@/lib/contracts/knowledgeBase';
import { KnowledgeBaseEditor } from '@/components/staff/KnowledgeBaseEditor';

export default async function KnowledgeBasePage() {
  const { session, selected: property } = await requirePropertyContext('/console/knowledge-base', [
    'OWNER',
    'MANAGER',
  ]);
  if (!property) {
    // Unreachable in practice — the (app) layout already blocks rendering when the tenant has no
    // property. Fail loudly rather than render a page that assumes data it doesn't have.
    throw new Error('No active property resolved');
  }

  // This path takes a property id, not a slug — the one endpoint in the console that does
  // (phase-6 §3.1, §4.10).
  const [currentResponse, historyResponse] = await Promise.all([
    upstream(`/api/v1/properties/${property.id}/knowledge-base`, { cookieHeader: session.cookieHeader }),
    upstream(`/api/v1/properties/${property.id}/knowledge-base/history`, { cookieHeader: session.cookieHeader }),
  ]);

  const current = currentResponse.ok ? KnowledgeBaseVersionResponseSchema.parse(await currentResponse.json()) : null;
  const history = historyResponse.ok
    ? KnowledgeBaseVersionResponseSchema.array().parse(await historyResponse.json())
    : [];

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">Knowledge base</h1>
      <p className="text-sm text-text-muted">
        A persisted, versioned record of the property&apos;s rules. Save is explicit, not on blur
        — nothing is written until you press Save. This is not yet wired into the guest-facing
        concierge chat, which still takes its knowledge base from each request rather than reading
        it from here (CLAUDE.md: the concierge stays untouched until the October beta gate).
      </p>
      <KnowledgeBaseEditor propertyId={property.id} current={current} history={history} />
    </div>
  );
}
