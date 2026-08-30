package com.altstay.api.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnitRepository extends JpaRepository<Unit, UUID> {
    List<Unit> findBySpaceId(UUID spaceId);
    List<Unit> findBySpaceIdIn(Collection<UUID> spaceIds);
    List<Unit> findBySpaceIdInAndIsActiveTrue(Collection<UUID> spaceIds);
    List<Unit> findBySpaceIdAndIsActiveTrue(UUID spaceId);
    Optional<Unit> findBySpaceIdAndLabel(UUID spaceId, String label);
    void deleteBySpaceId(UUID spaceId);
}
