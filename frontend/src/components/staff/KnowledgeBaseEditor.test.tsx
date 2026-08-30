import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { KnowledgeBaseEditor } from './KnowledgeBaseEditor';
import versionFixture from '@/lib/contracts/__fixtures__/knowledge-base-version.json';
import type { KnowledgeBaseVersionResponse } from '@/lib/contracts/knowledgeBase';

const current = versionFixture as KnowledgeBaseVersionResponse;
const refresh = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh }),
}));

describe('KnowledgeBaseEditor', () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    refresh.mockClear();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('pre-fills from the current version and shows the character count', () => {
    render(<KnowledgeBaseEditor propertyId="prop-1" current={current} history={[]} />);
    expect(screen.getByLabelText('Knowledge base')).toHaveValue(current.content);
    expect(screen.getByText(`${current.content.length} / 20,000 characters`)).toBeInTheDocument();
  });

  it('shows "No changes to save" and does not call the network when content is unchanged', async () => {
    global.fetch = vi.fn();
    render(<KnowledgeBaseEditor propertyId="prop-1" current={current} history={[]} />);

    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    expect(await screen.findByText('No changes to save.')).toBeInTheDocument();
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('saves changed content and reports the new version number', async () => {
    const saved = { ...current, versionNo: 4, content: 'Check-in is 12 PM now.' };
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(saved), { status: 200 }));

    render(<KnowledgeBaseEditor propertyId="prop-1" current={current} history={[]} />);
    fireEvent.change(screen.getByLabelText('Knowledge base'), { target: { value: 'Check-in is 12 PM now.' } });
    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    expect(await screen.findByText('Saved as version 4.')).toBeInTheDocument();
    expect(refresh).toHaveBeenCalled();
    const body = JSON.parse((vi.mocked(global.fetch).mock.calls[0][1] as RequestInit).body as string);
    expect(body).toEqual({ content: 'Check-in is 12 PM now.' });
  });

  it('offers Reload on a 409 conflict', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(JSON.stringify({ detail: 'Someone else saved first' }), { status: 409 })
    );

    render(<KnowledgeBaseEditor propertyId="prop-1" current={current} history={[]} />);
    fireEvent.change(screen.getByLabelText('Knowledge base'), { target: { value: 'Different content.' } });
    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Someone else saved first');
    fireEvent.click(screen.getByRole('button', { name: /reload/i }));
    expect(refresh).toHaveBeenCalled();
  });

  it('refuses to save over the 20,000-character limit, without calling the network', async () => {
    global.fetch = vi.fn();
    render(<KnowledgeBaseEditor propertyId="prop-1" current={null} history={[]} />);

    fireEvent.change(screen.getByLabelText('Knowledge base'), { target: { value: 'x'.repeat(20_001) } });
    expect(screen.getByRole('button', { name: /^save$/i })).toBeDisabled();
  });

  it('stages a history version into the editor without saving it', () => {
    const older = { ...current, versionNo: 2, content: 'Check-in is 3 PM (old rule).' };
    render(<KnowledgeBaseEditor propertyId="prop-1" current={current} history={[older, current]} />);

    fireEvent.click(screen.getByRole('button', { name: /show history/i }));
    fireEvent.click(screen.getAllByRole('button', { name: /copy into editor/i })[0]);

    expect(screen.getByLabelText('Knowledge base')).toHaveValue('Check-in is 3 PM (old rule).');
  });
});
