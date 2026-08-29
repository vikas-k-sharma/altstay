package com.altstay.api.conversation;

import com.altstay.api.chat.dto.ChatResponse;
import com.altstay.api.chat.dto.TokenUsage;
import com.altstay.api.property.Property;
import com.altstay.api.property.PropertyRepository;
import com.altstay.api.tenancy.MissingTenantException;
import com.altstay.api.tenancy.TenantContextTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The application-level half of Track D's tenancy proof, with <strong>every repository mocked</strong>.
 *
 * <p>phase-4-foundations.md §3.7 finding 1: RLS will silently cover for a missing application-level
 * predicate, and the whole suite stays green while it does - deleting a tenant predicate from
 * {@code AppUserRepository} left {@code AuthLoginIT} passing 5/5 because Postgres filtered the row
 * the query no longer did. {@code ConversationPersistenceIT} runs against real RLS and therefore
 * cannot tell these guards from their own absence. This class can: there is no database here, so
 * every assertion below fails the moment the corresponding check is deleted.
 *
 * <p>Both {@code propertyId} and {@code conversationId} are supplied by the caller, which is what
 * makes them worth guarding.
 */
@ExtendWith(MockitoExtension.class)
class ConversationPersistenceServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationTurnRepository turnRepository;

    @Mock
    private PropertyRepository propertyRepository;

    @InjectMocks
    private ConversationPersistenceService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID foreignTenantId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();

    private ChatResponse response() {
        return new ChatResponse("Check-in is 2 PM.", false, "gemini-2.5-flash", new TokenUsage(10, 5, 15), 120L);
    }

    private Property propertyOwnedBy(UUID owner) {
        Property property = new Property();
        property.setId(propertyId);
        property.setTenantId(owner);
        return property;
    }

    @Test
    @DisplayName("A propertyId belonging to another tenant is rejected by the application, not just by RLS")
    void foreignProperty_isRejectedWithRepositoriesMocked() {
        TenantContextTestSupport.runAs(tenantId, () -> {
            when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(propertyOwnedBy(foreignTenantId)));

            assertThatThrownBy(() -> service.persistTurns(propertyId, null, "hi", response()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not belong to tenant");

            verify(conversationRepository, never()).saveAndFlush(any());
            verify(turnRepository, never()).saveAndFlush(any());
        });
    }

    @Test
    @DisplayName("A conversationId belonging to another tenant cannot be appended to")
    void foreignConversation_isRejectedWithRepositoriesMocked() {
        UUID foreignConversationId = UUID.randomUUID();

        TenantContextTestSupport.runAs(tenantId, () -> {
            when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(propertyOwnedBy(tenantId)));

            Conversation foreign = new Conversation(foreignTenantId, propertyId);
            foreign.setId(foreignConversationId);
            when(conversationRepository.findById(foreignConversationId)).thenReturn(Optional.of(foreign));

            assertThatThrownBy(() -> service.persistTurns(propertyId, foreignConversationId, "hi", response()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not belong to tenant");

            verify(turnRepository, never()).saveAndFlush(any());
        });
    }

    @Test
    @DisplayName("A conversationId belonging to a different property of the same tenant is rejected")
    void conversationFromAnotherProperty_isRejected() {
        UUID otherPropertyId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        TenantContextTestSupport.runAs(tenantId, () -> {
            when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(propertyOwnedBy(tenantId)));

            Conversation otherProperty = new Conversation(tenantId, otherPropertyId);
            otherProperty.setId(conversationId);
            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(otherProperty));

            assertThatThrownBy(() -> service.persistTurns(propertyId, conversationId, "hi", response()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not belong to property");

            verify(turnRepository, never()).saveAndFlush(any());
        });
    }

    @Test
    @DisplayName("With no tenant context at all, nothing is written and no second path supplies one")
    void noTenantContext_throwsAndWritesNothing() {
        lenient().when(propertyRepository.findById(any())).thenReturn(Optional.of(propertyOwnedBy(tenantId)));

        assertThatThrownBy(() -> service.persistTurns(propertyId, null, "hi", response()))
                .isInstanceOf(MissingTenantException.class);

        verify(conversationRepository, never()).saveAndFlush(any());
        verify(turnRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("The tenant's own property and conversation persist both turns with the usage figures")
    void ownPropertyAndConversation_persistsBothTurns() {
        UUID conversationId = UUID.randomUUID();

        TenantContextTestSupport.runAs(tenantId, () -> {
            when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(propertyOwnedBy(tenantId)));

            Conversation own = new Conversation(tenantId, propertyId);
            own.setId(conversationId);
            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(own));
            when(conversationRepository.saveAndFlush(any(Conversation.class))).thenAnswer(i -> i.getArgument(0));
            when(turnRepository.findMaxSeqByConversationId(conversationId)).thenReturn(0);
            when(turnRepository.saveAndFlush(any(ConversationTurn.class))).thenAnswer(i -> i.getArgument(0));

            UUID result = service.persistTurns(propertyId, conversationId, "what time is check-in?", response());

            assertThat(result).isEqualTo(conversationId);
            verify(turnRepository, org.mockito.Mockito.times(2)).saveAndFlush(any(ConversationTurn.class));
        });
    }
}
