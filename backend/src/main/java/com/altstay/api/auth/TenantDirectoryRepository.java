package com.altstay.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantDirectoryRepository extends JpaRepository<TenantDirectory, String> {
    Optional<TenantDirectory> findBySlug(String slug);
}
