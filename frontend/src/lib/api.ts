import { ChatRequest, ChatResponse, ChatResponseSchema, ProblemDetail } from './contracts';

const SESSION_STORAGE_KEY = 'altstay_session_id';

/**
 * Stable per-browser-tab session id, sent as `x-altstay-session`.
 *
 * The BFF names the capture file after this header and falls back to `local-<date>` without it.
 * That fallback meant two beta sessions run on the same day appended to a single interleaved
 * JSONL — see phase-3-validation.md §3.3, which always assumed the console sent this.
 *
 * `sessionStorage` rather than `localStorage` is deliberate: a session is one sitting with one
 * person. A new tab is a new session; a reload during the sitting is not.
 */
function getSessionId(): string {
  const fallback = `local-${new Date().toISOString().slice(0, 10)}`;
  if (typeof window === 'undefined') return fallback;

  try {
    const existing = window.sessionStorage.getItem(SESSION_STORAGE_KEY);
    if (existing) return existing;

    const created = `session-${new Date().toISOString().slice(0, 10)}-${crypto.randomUUID().slice(0, 8)}`;
    window.sessionStorage.setItem(SESSION_STORAGE_KEY, created);
    return created;
  } catch {
    // Private mode, or storage disabled. Capture grouping degrades; chat must not.
    return fallback;
  }
}

export class ChatApiError extends Error {
  status?: number;
  problemDetail?: ProblemDetail;

  constructor(message: string, status?: number, problemDetail?: ProblemDetail) {
    super(message);
    this.name = 'ChatApiError';
    this.status = status;
    this.problemDetail = problemDetail;
  }
}

export async function sendChatMessage(request: ChatRequest): Promise<ChatResponse> {
  let response: Response;

  try {
    response = await fetch('/api/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-altstay-session': getSessionId(),
      },
      body: JSON.stringify(request),
    });
  } catch {
    throw new ChatApiError('Unable to connect to server. Please check your internet connection.', 0);
  }

  if (!response.ok) {
    let problemDetail: ProblemDetail | undefined;
    let fallbackMessage = `Request failed (${response.status})`;

    try {
      const data = await response.json();
      problemDetail = data as ProblemDetail;

      if (problemDetail.errors) {
        const firstErrorField = Object.keys(problemDetail.errors)[0];
        fallbackMessage = `Validation error: ${firstErrorField} ${problemDetail.errors[firstErrorField]}`;
      } else if (problemDetail.detail) {
        fallbackMessage = problemDetail.detail;
      }
    } catch {
      // Body not JSON, keep fallback status message
    }

    if (response.status === 429) {
      fallbackMessage = 'One moment \u2014 catching up.';
    } else if (response.status === 503) {
      fallbackMessage = 'The concierge is paused right now.';
    } else if (response.status === 502) {
      fallbackMessage = 'The concierge is offline for a moment. Please retry.';
    } else if (response.status === 504) {
      fallbackMessage = 'The request timed out. Please retry.';
    }

    throw new ChatApiError(fallbackMessage, response.status, problemDetail);
  }

  const json = await response.json();
  const parseResult = ChatResponseSchema.safeParse(json);

  if (!parseResult.success) {
    throw new ChatApiError('Received invalid response format from backend.');
  }

  return parseResult.data;
}
