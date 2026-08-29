package com.altstay.api.conversation;

import com.altstay.api.chat.dto.ChatResponse;
import com.altstay.api.property.Property;
import com.altstay.api.property.PropertyRepository;
import com.altstay.api.tenancy.CurrentTenantHolder;
import com.altstay.api.tenancy.MissingTenantException;
import com.altstay.api.tenancy.TenantScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@TenantScoped
@ConditionalOnProperty(name = "spring.datasource.url")
@Slf4j
@RequiredArgsConstructor
public class ConversationPersistenceService {

    private final ConversationRepository conversationRepository;
    private final ConversationTurnRepository turnRepository;
    private final PropertyRepository propertyRepository;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public UUID persistTurns(UUID propertyId, UUID conversationId, String userMessage, ChatResponse response) {
        // CurrentTenantHolder is the only source. TenantBindingAspect has already thrown if it were
        // empty, so there is no second path to a tenant id here - and deliberately so: roadmap §4.1's
        // "the tenant id must never be a client-supplied value" is a property of there being exactly
        // one writer, and an alternative resolution path is how that erodes.
        UUID tenantId = CurrentTenantHolder.get()
                .orElseThrow(() -> new MissingTenantException(
                        "Cannot persist conversation turns with no authenticated tenant context"));

        // §3.7 finding 1: RLS will silently cover for a missing application-level predicate, and the
        // suite stays green while it does. propertyId and conversationId are both CLIENT-SUPPLIED,
        // so each one is checked here as well as by RLS. ConversationPersistenceServiceTest proves
        // these checks with the repositories mocked, where RLS cannot stand in for them.
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertyId));
        if (!tenantId.equals(property.getTenantId())) {
            throw new IllegalArgumentException("Property does not belong to tenant: " + propertyId);
        }

        Conversation conversation;
        if (conversationId != null) {
            Optional<Conversation> existing = conversationRepository.findById(conversationId);
            if (existing.isPresent()) {
                conversation = existing.get();
                if (!tenantId.equals(conversation.getTenantId())) {
                    throw new IllegalArgumentException(
                            "Conversation does not belong to tenant: " + conversationId);
                }
                if (!propertyId.equals(conversation.getPropertyId())) {
                    throw new IllegalArgumentException(
                            "Conversation does not belong to property: " + conversationId);
                }
            } else {
                Conversation created = new Conversation(tenantId, propertyId);
                created.setId(conversationId);
                conversation = conversationRepository.saveAndFlush(created);
            }
        } else {
            conversation = conversationRepository.saveAndFlush(new Conversation(tenantId, propertyId));
        }

        conversation.setLastActivityAt(OffsetDateTime.now());
        conversationRepository.saveAndFlush(conversation);

        int maxSeq = turnRepository.findMaxSeqByConversationId(conversation.getId());
        int userSeq = maxSeq + 1;
        int assistantSeq = maxSeq + 2;

        ConversationTurn userTurn = new ConversationTurn(tenantId, conversation.getId(), userSeq, "USER", userMessage);
        userTurn.setPromptTokens(0);
        userTurn.setCompletionTokens(0);
        userTurn.setTotalTokens(0);
        userTurn.setLatencyMs(0);
        turnRepository.saveAndFlush(userTurn);

        ConversationTurn assistantTurn = new ConversationTurn(tenantId, conversation.getId(), assistantSeq, "ASSISTANT", response.reply());
        assistantTurn.setEscalated(response.escalated());
        assistantTurn.setModel(response.model());
        if (response.usage() != null) {
            assistantTurn.setPromptTokens(response.usage().promptTokens());
            assistantTurn.setCompletionTokens(response.usage().completionTokens());
            assistantTurn.setTotalTokens(response.usage().totalTokens());
        }
        assistantTurn.setLatencyMs((int) response.latencyMs());
        turnRepository.saveAndFlush(assistantTurn);

        log.info("Persisted conversation turns: conversationId={}, tenantId={}, userSeq={}, assistantSeq={}, totalTokens={}",
                conversation.getId(), tenantId, userSeq, assistantSeq, assistantTurn.getTotalTokens());

        return conversation.getId();
    }
}
