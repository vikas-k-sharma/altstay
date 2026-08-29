package com.altstay.api.knowledgebase;

import com.altstay.api.property.Property;
import com.altstay.api.property.PropertyRepository;
import com.altstay.api.tenancy.TenantScoped;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Transactional helper executing an individual save attempt in a new isolated transaction.
 *
 * <p>Isolated transactions ({@link Propagation#REQUIRES_NEW}) ensure that on concurrency conflict,
 * the aborted PostgreSQL transaction is cleanly rolled back so a retry can execute against a fresh connection.
 */
@Component
@TenantScoped
@ConditionalOnProperty(name = "spring.datasource.url")
public class KnowledgeBaseTxHelper {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseVersionRepository knowledgeBaseVersionRepository;
    private final PropertyRepository propertyRepository;

    public KnowledgeBaseTxHelper(KnowledgeBaseRepository knowledgeBaseRepository,
                                 KnowledgeBaseVersionRepository knowledgeBaseVersionRepository,
                                 PropertyRepository propertyRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.knowledgeBaseVersionRepository = knowledgeBaseVersionRepository;
        this.propertyRepository = propertyRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KnowledgeBaseVersion attemptSave(UUID tenantId, UUID propertyId, String content,
                                            String contentSha256, int charCount, UUID authoredBy) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertyId));

        // Application-level tenant check (defence in depth, verifiable when repository is mocked)
        if (!tenantId.equals(property.getTenantId())) {
            throw new IllegalArgumentException("Property does not belong to tenant: " + propertyId);
        }

        KnowledgeBase kb = knowledgeBaseRepository.findByPropertyId(propertyId)
                .orElseGet(() -> {
                    KnowledgeBase newKb = new KnowledgeBase(tenantId, propertyId);
                    return knowledgeBaseRepository.saveAndFlush(newKb);
                });

        // Application-level tenant check on KnowledgeBase
        if (!tenantId.equals(kb.getTenantId())) {
            throw new IllegalArgumentException("Knowledge base does not belong to tenant: " + kb.getId());
        }

        if (kb.getCurrentVersionId() != null) {
            Optional<KnowledgeBaseVersion> currentOpt = knowledgeBaseVersionRepository.findById(kb.getCurrentVersionId());
            if (currentOpt.isPresent()) {
                KnowledgeBaseVersion current = currentOpt.get();
                if (tenantId.equals(current.getTenantId()) && current.getContentSha256().equals(contentSha256)) {
                    // Unchanged content: no-op, return existing version
                    return current;
                }
            }
        }

        int maxVersion = knowledgeBaseVersionRepository.findMaxVersionNoByKnowledgeBaseId(kb.getId());
        int nextVersion = maxVersion + 1;

        KnowledgeBaseVersion newVersion = new KnowledgeBaseVersion(
                tenantId,
                kb.getId(),
                nextVersion,
                content,
                contentSha256,
                charCount,
                authoredBy
        );
        newVersion = knowledgeBaseVersionRepository.saveAndFlush(newVersion);

        kb.setCurrentVersionId(newVersion.getId());
        kb.setUpdatedAt(OffsetDateTime.now());
        knowledgeBaseRepository.saveAndFlush(kb);

        return newVersion;
    }
}
