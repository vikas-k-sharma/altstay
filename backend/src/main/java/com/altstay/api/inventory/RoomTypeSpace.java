package com.altstay.api.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "room_type_space")
@IdClass(RoomTypeSpaceId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomTypeSpace {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "room_type_id", nullable = false)
    private UUID roomTypeId;

    @Id
    @Column(name = "space_id", nullable = false)
    private UUID spaceId;
}
