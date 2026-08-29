'use client';

import { useKnowledgeBase } from '@/hooks/useKnowledgeBase';
import { useConversation } from '@/hooks/useConversation';
import { SplitPane } from './SplitPane';
import { ChatPanel } from '../chat/ChatPanel';
import { AdminPanel } from '../admin/AdminPanel';

export function ConsoleShell() {
  const {
    propertyName,
    knowledgeBase,
    activePresetId,
    activePreset,
    setPropertyName,
    setKnowledgeBase,
    selectPreset,
  } = useKnowledgeBase();

  const isKbInvalid = knowledgeBase.length > 20000 || knowledgeBase.trim().length === 0;
  const disabledReason = isKbInvalid
    ? knowledgeBase.length > 20000
      ? 'Knowledge base exceeds 20k chars'
      : 'Knowledge base is empty'
    : undefined;

  const {
    messages,
    status,
    sendMessage,
    retry,
    clearConversation,
  } = useConversation({
    propertyName,
    knowledgeBase,
  });

  return (
    <SplitPane
      propertyName={propertyName}
      chatPanel={
        <ChatPanel
          propertyName={propertyName}
          messages={messages}
          status={status}
          suggestedQuestions={activePreset.suggestedQuestions}
          disabledReason={disabledReason}
          onSendMessage={sendMessage}
          onSelectQuestion={sendMessage}
          onRetry={retry}
          onClear={clearConversation}
        />
      }
      adminPanel={
        <AdminPanel
          propertyName={propertyName}
          knowledgeBase={knowledgeBase}
          activePresetId={activePresetId}
          onPropertyNameChange={setPropertyName}
          onKnowledgeBaseChange={setKnowledgeBase}
          onSelectPreset={selectPreset}
        />
      }
    />
  );
}
