package com.altstay.api.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomTypeSpaceRepository extends JpaRepository<RoomTypeSpace, RoomTypeSpaceId> {
    List<RoomTypeSpace> findByRoomTypeId(UUID roomTypeId);
    List<RoomTypeSpace> findBySpaceId(UUID spaceId);
    List<RoomTypeSpace> findByRoomTypeIdIn(Collection<UUID> roomTypeIds);
    Optional<RoomTypeSpace> findByRoomTypeIdAndSpaceId(UUID roomTypeId, UUID spaceId);
    void deleteByRoomTypeIdAndSpaceId(UUID roomTypeId, UUID spaceId);
}
