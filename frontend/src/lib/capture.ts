import fs from 'fs';
import path from 'path';
import crypto from 'crypto';

export interface CaptureTurnParams {
  sessionId: string;
  propertyName?: string;
  knowledgeBase: string;
  historyTurns: number;
  message: string;
  reply: string | null;
  escalated?: boolean;
  model?: string;
  usage?: { promptTokens: number; completionTokens: number; totalTokens: number } | null;
  latencyMs: number;
  status: number;
  errorTitle?: string;
}

// In-memory track of last known kbHash per sessionId to avoid duplicate kb lines in same process run
const sessionKbHashMap = new Map<string, string>();

export async function captureSessionTurn(params: CaptureTurnParams): Promise<void> {
  const captureDir = process.env.ALTSTAY_CAPTURE_DIR;
  if (!captureDir) {
    return;
  }

  try {
    const resolvedDir = path.resolve(captureDir);
    await fs.promises.mkdir(resolvedDir, { recursive: true });

    const safeSessionId = params.sessionId.replace(/[^a-zA-Z0-9_-]/g, '_') || `local-${new Date().toISOString().slice(0, 10)}`;
    const filePath = path.join(resolvedDir, `${safeSessionId}.jsonl`);

    const kbHash = crypto.createHash('sha256').update(params.knowledgeBase || '').digest('hex').slice(0, 8);
    const kbRef = `kb-${kbHash}`;

    let linesToWrite = '';

    const lastKbHash = sessionKbHashMap.get(safeSessionId);
    if (lastKbHash !== kbHash) {
      sessionKbHashMap.set(safeSessionId, kbHash);
      const kbRecord = {
        type: 'kb',
        kbRef,
        at: new Date().toISOString(),
        propertyName: params.propertyName || 'AltStay Property',
        chars: (params.knowledgeBase || '').length,
        knowledgeBase: params.knowledgeBase || '',
      };
      linesToWrite += JSON.stringify(kbRecord) + '\n';
    }

    const turnRecord = {
      type: 'turn',
      at: new Date().toISOString(),
      kbRef,
      historyTurns: params.historyTurns,
      message: params.message,
      reply: params.reply,
      escalated: params.escalated ?? false,
      model: params.model ?? (params.status === 200 ? 'gemini-2.5-flash' : 'unknown'),
      usage: params.usage ?? null,
      latencyMs: params.latencyMs,
      status: params.status,
      ...(params.errorTitle ? { errorTitle: params.errorTitle } : {}),
    };

    linesToWrite += JSON.stringify(turnRecord) + '\n';

    await fs.promises.appendFile(filePath, linesToWrite, 'utf8');
  } catch {
    // Non-fatal, fire-and-forget per spec
  }
}
