package com.altstay.api.knowledgebase;

import com.altstay.api.auth.TenantUserDetails;
import com.altstay.api.tenancy.CurrentTenantHolder;
import com.altstay.api.tenancy.MissingTenantException;
import com.altstay.api.tenancy.TenantScoped;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing tenant-scoped knowledge base versioning and history.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Allocate version_no inside transaction from max(version_no) + 1 with retry on conflict.</li>
 *   <li>Unchanged content returns existing version without creating a new row.</li>
 *   <li>authored_by is derived from the authenticated principal and is never null.</li>
 * </ul>
 */
@Service
@TenantScoped
@ConditionalOnProperty(name = "spring.datasource.url")
@Slf4j
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseVersionRepository knowledgeBaseVersionRepository;
    private final KnowledgeBaseTxHelper txHelper;

    public KnowledgeBaseService(KnowledgeBaseRepository knowledgeBaseRepository,
                                KnowledgeBaseVersionRepository knowledgeBaseVersionRepository,
                                KnowledgeBaseTxHelper txHelper) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.knowledgeBaseVersionRepository = knowledgeBaseVersionRepository;
        this.txHelper = txHelper;
    }

    @Transactional(readOnly = true)
    public Optional<KnowledgeBaseVersion> getCurrent(UUID propertyId) {
        UUID tenantId = CurrentTenantHolder.get()
                .orElseThrow(() -> new MissingTenantException("No authenticated tenant context"));

        Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findByPropertyId(propertyId);
        if (kbOpt.isEmpty()) {
            return Optional.empty();
        }
        KnowledgeBase kb = kbOpt.get();
        if (!tenantId.equals(kb.getTenantId())) {
            return Optional.empty();
        }
        if (kb.getCurrentVersionId() == null) {
            return Optional.empty();
        }
        Optional<KnowledgeBaseVersion> versionOpt = knowledgeBaseVersionRepository.findById(kb.getCurrentVersionId());
        if (versionOpt.isEmpty()) {
            return Optional.empty();
        }
        KnowledgeBaseVersion version = versionOpt.get();
        if (!tenantId.equals(version.getTenantId())) {
            return Optional.empty();
        }
        return Optional.of(version);
    }

    @Transactional
    public KnowledgeBaseVersion save(UUID propertyId, String content) {
        UUID tenantId = CurrentTenantHolder.get()
                .orElseThrow(() -> new MissingTenantException("No authenticated tenant context"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = null;
        if (auth != null && auth.getPrincipal() instanceof TenantUserDetails userDetails) {
            userId = userDetails.getUserId();
        }
        if (userId == null) {
            throw new MissingTenantException("Authenticated user principal is required to author a knowledge base version");
        }

        String sha256 = computeSha256(content);
        // codePointCount, not length(). char_count is checked by Postgres against `between 1 and
        // 20000`, and Postgres counts characters while String.length() counts UTF-16 code units -
        // so an emoji or any other non-BMP character makes the two disagree near the boundary.
        int charCount = content.codePointCount(0, content.length());

        try {
            return txHelper.attemptSave(tenantId, propertyId, content, sha256, charCount, userId);
        } catch (DataIntegrityViolationException e) {
            log.warn("Version conflict on knowledge base for property {}, retrying once...", propertyId);
            try {
                return txHelper.attemptSave(tenantId, propertyId, content, sha256, charCount, userId);
            } catch (DataIntegrityViolationException ex) {
                log.error("Knowledge base save failed after retry due to persistent conflict on property {}", propertyId, ex);
                throw new KnowledgeBaseConflictException("Someone else saved first", ex);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<KnowledgeBaseVersion> history(UUID propertyId, int limit) {
        UUID tenantId = CurrentTenantHolder.get()
                .orElseThrow(() -> new MissingTenantException("No authenticated tenant context"));

        Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findByPropertyId(propertyId);
        if (kbOpt.isEmpty()) {
            return List.of();
        }
        KnowledgeBase kb = kbOpt.get();
        if (!tenantId.equals(kb.getTenantId())) {
            return List.of();
        }

        int maxLimit = limit > 0 ? Math.min(limit, 100) : 50;
        Pageable pageable = PageRequest.of(0, maxLimit);
        List<KnowledgeBaseVersion> versions = knowledgeBaseVersionRepository
                .findByKnowledgeBaseIdOrderByVersionNoDesc(kb.getId(), pageable);

        return versions.stream()
                .filter(v -> tenantId.equals(v.getTenantId()))
                .toList();
    }

    public static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
