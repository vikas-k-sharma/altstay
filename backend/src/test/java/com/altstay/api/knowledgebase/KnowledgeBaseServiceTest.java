package com.altstay.api.knowledgebase;

import com.altstay.api.auth.AuthRole;
import com.altstay.api.auth.TenantUserDetails;
import com.altstay.api.property.Property;
import com.altstay.api.property.PropertyRepository;
import com.altstay.api.tenancy.MissingTenantException;
import com.altstay.api.tenancy.TenantContextTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private KnowledgeBaseVersionRepository knowledgeBaseVersionRepository;

    @Mock
    private PropertyRepository propertyRepository;

    private KnowledgeBaseTxHelper txHelper;
    private KnowledgeBaseService knowledgeBaseService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID foreignTenantId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        txHelper = new KnowledgeBaseTxHelper(
                knowledgeBaseRepository,
                knowledgeBaseVersionRepository,
                propertyRepository
        );
        knowledgeBaseService = new KnowledgeBaseService(
                knowledgeBaseRepository,
                knowledgeBaseVersionRepository,
                txHelper
        );

        TenantUserDetails principal = new TenantUserDetails(
                userId,
                tenantId,
                "test-tenant",
                "user@test.com",
                "hash",
                "Test User",
                true,
                Set.of(AuthRole.OWNER)
        );
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("save produces exactly one new version row and repoints current_version_id")
    void save_producesNewVersion_repointsCurrentVersionId() {
        Property property = new Property(tenantId, "Goa Beach", "goa-beach", "Asia/Kolkata", "INR");
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));

        UUID kbId = UUID.randomUUID();
        KnowledgeBase kb = new KnowledgeBase(tenantId, propertyId);
        kb.setId(kbId);
        when(knowledgeBaseRepository.findByPropertyId(propertyId)).thenReturn(Optional.of(kb));

        when(knowledgeBaseVersionRepository.findMaxVersionNoByKnowledgeBaseId(kbId)).thenReturn(0);
        when(knowledgeBaseVersionRepository.saveAndFlush(any(KnowledgeBaseVersion.class)))
                .thenAnswer(invocation -> {
                    KnowledgeBaseVersion v = invocation.getArgument(0);
                    v.setId(UUID.randomUUID());
                    return v;
                });
        when(knowledgeBaseRepository.saveAndFlush(any(KnowledgeBase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeBaseVersion result = TenantContextTestSupport.runAs(tenantId, () ->
                knowledgeBaseService.save(propertyId, "Initial house rules")
        );

        assertThat(result).isNotNull();
        assertThat(result.getVersionNo()).isEqualTo(1);
        assertThat(result.getContent()).isEqualTo("Initial house rules");
        assertThat(result.getAuthoredBy()).isEqualTo(userId);
        assertThat(kb.getCurrentVersionId()).isEqualTo(result.getId());

        verify(knowledgeBaseVersionRepository, times(1)).saveAndFlush(any(KnowledgeBaseVersion.class));
        verify(knowledgeBaseRepository, times(1)).saveAndFlush(kb);
    }

    @Test
    @DisplayName("save with unchanged content produces NO new version and returns existing version")
    void save_withUnchangedContent_producesNoNewVersion_returnsExisting() {
        Property property = new Property(tenantId, "Goa Beach", "goa-beach", "Asia/Kolkata", "INR");
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));

        String content = "Standard house rules content";
        String sha256 = KnowledgeBaseService.computeSha256(content);

        UUID kbId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        KnowledgeBase kb = new KnowledgeBase(tenantId, propertyId);
        kb.setId(kbId);
        kb.setCurrentVersionId(versionId);
        when(knowledgeBaseRepository.findByPropertyId(propertyId)).thenReturn(Optional.of(kb));

        KnowledgeBaseVersion currentVersion = new KnowledgeBaseVersion(
                tenantId, kbId, 1, content, sha256, content.length(), userId
        );
        currentVersion.setId(versionId);
        when(knowledgeBaseVersionRepository.findById(versionId)).thenReturn(Optional.of(currentVersion));

        KnowledgeBaseVersion result = TenantContextTestSupport.runAs(tenantId, () ->
                knowledgeBaseService.save(propertyId, content)
        );

        assertThat(result).isEqualTo(currentVersion);
        assertThat(result.getId()).isEqualTo(versionId);

        verify(knowledgeBaseVersionRepository, never()).saveAndFlush(any(KnowledgeBaseVersion.class));
        verify(knowledgeBaseRepository, never()).saveAndFlush(any(KnowledgeBase.class));
    }

    @Test
    @DisplayName("save allocates sequential version numbers based on coalesce(max(version_no), 0) + 1")
    void save_allocatesSequentialVersionNumbers() {
        Property property = new Property(tenantId, "Goa Beach", "goa-beach", "Asia/Kolkata", "INR");
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));

        UUID kbId = UUID.randomUUID();
        KnowledgeBase kb = new KnowledgeBase(tenantId, propertyId);
        kb.setId(kbId);
        kb.setCurrentVersionId(UUID.randomUUID());
        when(knowledgeBaseRepository.findByPropertyId(propertyId)).thenReturn(Optional.of(kb));

        KnowledgeBaseVersion oldVersion = new KnowledgeBaseVersion(
                tenantId, kbId, 5, "Old content", "oldsha", 11, userId
        );
        when(knowledgeBaseVersionRepository.findById(kb.getCurrentVersionId())).thenReturn(Optional.of(oldVersion));

        when(knowledgeBaseVersionRepository.findMaxVersionNoByKnowledgeBaseId(kbId)).thenReturn(5);
        when(knowledgeBaseVersionRepository.saveAndFlush(any(KnowledgeBaseVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeBaseVersion result = TenantContextTestSupport.runAs(tenantId, () ->
                knowledgeBaseService.save(propertyId, "New updated content")
        );

        assertThat(result.getVersionNo()).isEqualTo(6);
    }

    @Test
    @DisplayName("authored_by is derived from authenticated principal and is never null")
    void save_authoredByNeverNull() {
        Property property = new Property(tenantId, "Goa Beach", "goa-beach", "Asia/Kolkata", "INR");
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));

        UUID kbId = UUID.randomUUID();
        KnowledgeBase kb = new KnowledgeBase(tenantId, propertyId);
        kb.setId(kbId);
        when(knowledgeBaseRepository.findByPropertyId(propertyId)).thenReturn(Optional.of(kb));
        when(knowledgeBaseVersionRepository.findMaxVersionNoByKnowledgeBaseId(kbId)).thenReturn(0);
        when(knowledgeBaseVersionRepository.saveAndFlush(any(KnowledgeBaseVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeBaseVersion result = TenantContextTestSupport.runAs(tenantId, () ->
                knowledgeBaseService.save(propertyId, "House rules authored by user")
        );

        assertThat(result.getAuthoredBy()).isNotNull();
        assertThat(result.getAuthoredBy()).isEqualTo(userId);
    }

    @Test
    @DisplayName("save with no authenticated principal throws MissingTenantException")
    void save_withNoPrincipal_throwsMissingTenantException() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() ->
                TenantContextTestSupport.runAs(tenantId, () -> knowledgeBaseService.save(propertyId, "Rules"))
        )
                .isInstanceOf(MissingTenantException.class)
                .hasMessageContaining("Authenticated user principal is required");
    }

    @Test
    @DisplayName("application-level tenant check: rejects foreign property when repository is mocked (defence in depth)")
    void applicationLevelTenantCheck_rejectsForeignProperty_whenRepoMocked() {
        Property foreignProperty = new Property(foreignTenantId, "Foreign Beach", "foreign-beach", "Asia/Kolkata", "INR");
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(foreignProperty));

        assertThatThrownBy(() ->
                TenantContextTestSupport.runAs(tenantId, () -> knowledgeBaseService.save(propertyId, "Rules"))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Property does not belong to tenant");
    }

    @Test
    @DisplayName("application-level tenant check: rejects foreign knowledge base when repository is mocked")
    void applicationLevelTenantCheck_rejectsForeignKnowledgeBase_whenRepoMocked() {
        Property property = new Property(tenantId, "Goa Beach", "goa-beach", "Asia/Kolkata", "INR");
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));

        KnowledgeBase foreignKb = new KnowledgeBase(foreignTenantId, propertyId);
        when(knowledgeBaseRepository.findByPropertyId(propertyId)).thenReturn(Optional.of(foreignKb));

        assertThatThrownBy(() ->
                TenantContextTestSupport.runAs(tenantId, () -> knowledgeBaseService.save(propertyId, "Rules"))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Knowledge base does not belong to tenant");
    }

    @Test
    @DisplayName("getCurrent returns empty when knowledge base belongs to foreign tenant")
    void getCurrent_filtersOutForeignTenantKnowledgeBase() {
        KnowledgeBase foreignKb = new KnowledgeBase(foreignTenantId, propertyId);
        when(knowledgeBaseRepository.findByPropertyId(propertyId)).thenReturn(Optional.of(foreignKb));

        Optional<KnowledgeBaseVersion> result = TenantContextTestSupport.runAs(tenantId, () ->
                knowledgeBaseService.getCurrent(propertyId)
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("history filters out versions belonging to foreign tenant")
    void history_filtersOutForeignTenantVersions() {
        UUID kbId = UUID.randomUUID();
        KnowledgeBase kb = new KnowledgeBase(tenantId, propertyId);
        kb.setId(kbId);
        when(knowledgeBaseRepository.findByPropertyId(propertyId)).thenReturn(Optional.of(kb));

        KnowledgeBaseVersion ownVersion = new KnowledgeBaseVersion(tenantId, kbId, 1, "Own", "sha1", 3, userId);
        KnowledgeBaseVersion foreignVersion = new KnowledgeBaseVersion(foreignTenantId, kbId, 2, "Foreign", "sha2", 7, userId);

        when(knowledgeBaseVersionRepository.findByKnowledgeBaseIdOrderByVersionNoDesc(eq(kbId), any(Pageable.class)))
                .thenReturn(List.of(foreignVersion, ownVersion));

        List<KnowledgeBaseVersion> history = TenantContextTestSupport.runAs(tenantId, () ->
                knowledgeBaseService.history(propertyId, 10)
        );

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getContent()).isEqualTo("Own");
    }

    @Test
    @DisplayName("concurrent save: retry once succeeds after first attempt DataIntegrityViolationException")
    void concurrentSave_retriesOnceAndSucceeds() {
        Property property = new Property(tenantId, "Goa Beach", "goa-beach", "Asia/Kolkata", "INR");
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));

        UUID kbId = UUID.randomUUID();
        KnowledgeBase kb = new KnowledgeBase(tenantId, propertyId);
        kb.setId(kbId);
        when(knowledgeBaseRepository.findByPropertyId(propertyId)).thenReturn(Optional.of(kb));

        // First attempt throws DataIntegrityViolationException (unique version_no collision)
        // Second attempt succeeds with new max version
        when(knowledgeBaseVersionRepository.findMaxVersionNoByKnowledgeBaseId(kbId))
                .thenReturn(1) // first attempt sees max=1 -> next=2
                .thenReturn(2); // second attempt sees max=2 -> next=3

        when(knowledgeBaseVersionRepository.saveAndFlush(any(KnowledgeBaseVersion.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(knowledgeBaseRepository.saveAndFlush(any(KnowledgeBase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeBaseVersion result = TenantContextTestSupport.runAs(tenantId, () ->
                knowledgeBaseService.save(propertyId, "Concurrent save content")
        );

        assertThat(result).isNotNull();
        assertThat(result.getVersionNo()).isEqualTo(3);
        verify(knowledgeBaseVersionRepository, times(2)).saveAndFlush(any(KnowledgeBaseVersion.class));
    }

    @Test
    @DisplayName("concurrent save: second violation throws KnowledgeBaseConflictException (409)")
    void concurrentSave_secondViolationThrowsKnowledgeBaseConflictException() {
        Property property = new Property(tenantId, "Goa Beach", "goa-beach", "Asia/Kolkata", "INR");
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));

        UUID kbId = UUID.randomUUID();
        KnowledgeBase kb = new KnowledgeBase(tenantId, propertyId);
        kb.setId(kbId);
        when(knowledgeBaseRepository.findByPropertyId(propertyId)).thenReturn(Optional.of(kb));
        when(knowledgeBaseVersionRepository.findMaxVersionNoByKnowledgeBaseId(kbId)).thenReturn(1);

        when(knowledgeBaseVersionRepository.saveAndFlush(any(KnowledgeBaseVersion.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key collision 1"))
                .thenThrow(new DataIntegrityViolationException("duplicate key collision 2"));

        assertThatThrownBy(() ->
                TenantContextTestSupport.runAs(tenantId, () -> knowledgeBaseService.save(propertyId, "Content"))
        )
                .isInstanceOf(KnowledgeBaseConflictException.class)
                .hasMessageContaining("Someone else saved first");
    }
}
