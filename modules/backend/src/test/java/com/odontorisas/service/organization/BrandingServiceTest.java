package com.odontorisas.service.organization;

import com.odontorisas.common.BrandPreset;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.persistence.entity.OrganizationBranding;
import com.odontorisas.persistence.repository.OrganizationBrandingRepository;
import com.odontorisas.persistence.repository.OrganizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandingServiceTest {

    private static final long ORG = 42L;

    @Mock OrganizationBrandingRepository brandingRepository;
    @Mock OrganizationRepository organizationRepository;

    @InjectMocks BrandingService service;

    @BeforeEach
    void setContext() {
        OrganizationContext.set(ORG);
    }

    @AfterEach
    void clearContext() {
        OrganizationContext.clear();
    }

    @Test
    void get_returns_persisted_branding() {
        when(brandingRepository.findById(ORG)).thenReturn(Optional.of(
            OrganizationBranding.builder().organizationId(ORG).brandPreset(BrandPreset.TEAL).build()));

        Branding result = service.getForCurrentOrganization();

        assertThat(result.brandPreset()).isEqualTo(BrandPreset.TEAL);
    }

    @Test
    void get_returns_empty_when_no_row() {
        when(brandingRepository.findById(ORG)).thenReturn(Optional.empty());

        Branding result = service.getForCurrentOrganization();

        assertThat(result.brandPreset()).isNull();
        assertThat(result.logoUrl()).isNull();
    }

    @Test
    void update_upserts_branding() {
        when(brandingRepository.findById(ORG)).thenReturn(Optional.empty());
        when(organizationRepository.getReferenceById(ORG)).thenReturn(null);
        when(brandingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Branding result = service.update(
            new Branding(BrandPreset.VIOLET, "https://cdn.test/logo.png"));

        assertThat(result.brandPreset()).isEqualTo(BrandPreset.VIOLET);
        assertThat(result.logoUrl()).isEqualTo("https://cdn.test/logo.png");
        verify(brandingRepository).save(any(OrganizationBranding.class));
    }

    @Test
    void update_with_all_null_deletes_the_row() {
        Branding result = service.update(new Branding(null, null));

        assertThat(result.brandPreset()).isNull();
        verify(brandingRepository).deleteById(ORG);
        verify(brandingRepository, never()).save(any());
    }
}
