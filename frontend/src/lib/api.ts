import { ChatRequest, ChatResponse, ChatResponseSchema, ProblemDetail } from './contracts';

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

    if (response.status === 502 || response.status === 503) {
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
