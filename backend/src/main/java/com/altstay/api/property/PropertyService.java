package com.altstay.api.property;

import com.altstay.api.amenity.PropertyAmenity;
import com.altstay.api.amenity.PropertyAmenityRepository;
import com.altstay.api.tenancy.CurrentTenantHolder;
import com.altstay.api.tenancy.MissingTenantException;
import com.altstay.api.tenancy.TenantScoped;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@TenantScoped
@ConditionalOnProperty(name = "spring.datasource.url")
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final PropertyAmenityRepository propertyAmenityRepository;

    public PropertyService(PropertyRepository propertyRepository, PropertyAmenityRepository propertyAmenityRepository) {
        this.propertyRepository = propertyRepository;
        this.propertyAmenityRepository = propertyAmenityRepository;
    }

    @Transactional(readOnly = true)
    public List<Property> listProperties() {
        return propertyRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Property> getPropertyBySlug(String slug) {
        return propertyRepository.findBySlug(slug);
    }

    @Transactional(readOnly = true)
    public Optional<Property> getPropertyById(UUID id) {
        return propertyRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<String> getPropertyAmenities(UUID propertyId) {
        return propertyAmenityRepository.findByPropertyId(propertyId)
                .stream()
                .map(PropertyAmenity::getAmenityCode)
                .toList();
    }

    /**
     * Minimal creation: everything optional left unset, but the <b>timezone and currency are still
     * required</b>. There is deliberately no overload that omits them — see
     * {@link #requireTimezone} for why.
     */
    @Transactional
    public Property createProperty(String name, String slug, String timezone, String currencyCode) {
        return createProperty(
                name, slug, null, null, "ACTIVE", timezone, currencyCode, null,
                null, null, null, null, null, null, null,
                null, null, null, List.of()
        );
    }

    @Transactional
    public Property createProperty(
            String name,
            String slug,
            String legalName,
            String description,
            String status,
            String timezone,
            String currencyCode,
            String countryCode,
            String addressLine1,
            String addressLine2,
            String city,
            String stateRegion,
            String postalCode,
            String contactEmail,
            String contactPhone,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            Integer taxRateBps,
            List<String> amenities
    ) {
        UUID tenantId = CurrentTenantHolder.get()
                .orElseThrow(() -> new MissingTenantException("No authenticated tenant context"));

        String tz = requireTimezone(timezone);
        String curr = requireCurrencyCode(currencyCode);

        Property property = new Property(tenantId, name, slug, tz, curr);
        if (legalName != null) property.setLegalName(legalName);
        if (description != null) property.setDescription(description);
        if (status != null && !status.isBlank()) {
            validateStatus(status);
            property.setStatus(status.toUpperCase());
        }
        if (countryCode != null && !countryCode.isBlank()) property.setCountryCode(countryCode.toUpperCase());
        if (addressLine1 != null) property.setAddressLine1(addressLine1);
        if (addressLine2 != null) property.setAddressLine2(addressLine2);
        if (city != null) property.setCity(city);
        if (stateRegion != null) property.setStateRegion(stateRegion);
        if (postalCode != null) property.setPostalCode(postalCode);
        if (contactEmail != null) property.setContactEmail(contactEmail);
        if (contactPhone != null) property.setContactPhone(contactPhone);
        if (checkInTime != null) property.setCheckInTime(checkInTime);
        if (checkOutTime != null) property.setCheckOutTime(checkOutTime);
        if (taxRateBps != null) {
            validateTaxRateBps(taxRateBps);
            property.setTaxRateBps(taxRateBps);
        }

        Property saved = propertyRepository.save(property);

        if (amenities != null && !amenities.isEmpty()) {
            List<PropertyAmenity> list = amenities.stream()
                    .map(code -> new PropertyAmenity(saved.getId(), code, tenantId))
                    .toList();
            propertyAmenityRepository.saveAll(list);
        }

        return saved;
    }

    @Transactional
    public Property updateProperty(
            String slug,
            String name,
            String legalName,
            String description,
            String status,
            String timezone,
            String currencyCode,
            String countryCode,
            String addressLine1,
            String addressLine2,
            String city,
            String stateRegion,
            String postalCode,
            String contactEmail,
            String contactPhone,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            Integer taxRateBps,
            List<String> amenities
    ) {
        UUID tenantId = CurrentTenantHolder.get()
                .orElseThrow(() -> new MissingTenantException("No authenticated tenant context"));

        Property property = propertyRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with slug: " + slug));

        if (name != null && !name.isBlank()) property.setName(name);
        if (legalName != null) property.setLegalName(legalName);
        if (description != null) property.setDescription(description);
        if (status != null && !status.isBlank()) {
            validateStatus(status);
            property.setStatus(status.toUpperCase());
        }
        if (timezone != null && !timezone.isBlank()) {
            validateTimezone(timezone);
            property.setTimezone(timezone);
        }
        if (currencyCode != null && !currencyCode.isBlank()) {
            String curr = currencyCode.toUpperCase();
            validateCurrencyCode(curr);
            property.setCurrencyCode(curr);
        }
        if (countryCode != null) property.setCountryCode(countryCode.isBlank() ? null : countryCode.toUpperCase());
        if (addressLine1 != null) property.setAddressLine1(addressLine1);
        if (addressLine2 != null) property.setAddressLine2(addressLine2);
        if (city != null) property.setCity(city);
        if (stateRegion != null) property.setStateRegion(stateRegion);
        if (postalCode != null) property.setPostalCode(postalCode);
        if (contactEmail != null) property.setContactEmail(contactEmail);
        if (contactPhone != null) property.setContactPhone(contactPhone);
        if (checkInTime != null) property.setCheckInTime(checkInTime);
        if (checkOutTime != null) property.setCheckOutTime(checkOutTime);
        if (taxRateBps != null) {
            validateTaxRateBps(taxRateBps);
            property.setTaxRateBps(taxRateBps);
        }

        Property saved = propertyRepository.save(property);

        if (amenities != null) {
            propertyAmenityRepository.deleteByPropertyId(saved.getId());
            if (!amenities.isEmpty()) {
                List<PropertyAmenity> list = amenities.stream()
                        .map(code -> new PropertyAmenity(saved.getId(), code, tenantId))
                        .toList();
                propertyAmenityRepository.saveAll(list);
            }
        }

        return saved;
    }

    /**
     * A property's timezone is required and is never defaulted.
     *
     * <p>Same fail-fast rule as {@code GOOGLE_API_KEY} and the three {@code ALTSTAY_DB_*} values,
     * and for the same reason (§2): every business-day boundary in a PMS is property-local — which
     * date "tonight" is, whether a 01:00 arrival is today's or yesterday's — and <b>a defaulted
     * timezone is a wrong answer that looks like a right one</b>. The bug surfaces later and
     * somewhere else, as "the arrivals list is empty at 6am".
     *
     * <p>This method exists because an earlier version silently substituted {@code Asia/Kolkata}
     * and {@code INR} whenever the caller omitted them, which made the migration's {@code not null}
     * unreachable: the database was never given the chance to refuse.
     */
    private String requireTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException(
                    "timezone is required and has no default: give an IANA zone id such as 'Asia/Kolkata'");
        }
        validateTimezone(timezone);
        return timezone;
    }

    /** Required for the same reason as the timezone, and never defaulted. See {@link #requireTimezone}. */
    private String requireCurrencyCode(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException(
                    "currencyCode is required and has no default: give an ISO 4217 code such as 'INR'");
        }
        String upper = currencyCode.toUpperCase();
        validateCurrencyCode(upper);
        return upper;
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timezone: " + timezone);
        }
    }

    private void validateCurrencyCode(String currencyCode) {
        try {
            Currency.getInstance(currencyCode);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid ISO 4217 currency code: " + currencyCode);
        }
    }

    private void validateStatus(String status) {
        if (!"ACTIVE".equalsIgnoreCase(status) && !"INACTIVE".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("Invalid status: " + status + ". Expected ACTIVE or INACTIVE.");
        }
    }

    private void validateTaxRateBps(Integer bps) {
        if (bps < 0 || bps > 10000) {
            throw new IllegalArgumentException("taxRateBps must be between 0 and 10000");
        }
    }
}
