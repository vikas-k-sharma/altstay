package com.altstay.api.property;

import com.altstay.api.tenancy.CurrentTenantHolder;
import com.altstay.api.tenancy.MissingTenantException;
import com.altstay.api.tenancy.TenantScoped;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@TenantScoped
@ConditionalOnProperty(name = "spring.datasource.url")
public class PropertyService {

    private final PropertyRepository propertyRepository;

    public PropertyService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    @Transactional(readOnly = true)
    public List<Property> listProperties() {
        return propertyRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Property> getPropertyBySlug(String slug) {
        return propertyRepository.findBySlug(slug);
    }

    @Transactional
    public Property createProperty(String name, String slug) {
        UUID tenantId = CurrentTenantHolder.get()
                .orElseThrow(() -> new MissingTenantException("No authenticated tenant context"));
        return propertyRepository.save(new Property(tenantId, name, slug));
    }
}
