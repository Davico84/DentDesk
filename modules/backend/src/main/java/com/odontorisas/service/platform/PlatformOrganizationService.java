package com.odontorisas.service.platform;

import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationStatus;
import com.odontorisas.common.OrganizationType;
import com.odontorisas.persistence.entity.Organization;
import com.odontorisas.persistence.entity.OrganizationSettings;
import com.odontorisas.persistence.entity.User;
import com.odontorisas.persistence.repository.OrganizationRepository;
import com.odontorisas.persistence.repository.OrganizationSettingsRepository;
import com.odontorisas.persistence.repository.PlatformAdminRepository;
import com.odontorisas.persistence.repository.UserRepository;
import com.odontorisas.service.organization.OrganizationSlugService;
import com.odontorisas.service.staff.StaffService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PlatformOrganizationService {
    private final PlatformAdminRepository platformAdmins;
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final OrganizationSettingsRepository settings;
    private final StaffService staffService;
    private final OrganizationSlugService slugService;

    public PlatformOrganizationService(PlatformAdminRepository platformAdmins, UserRepository users,
                                       OrganizationRepository organizations,
                                       OrganizationSettingsRepository settings,
                                       StaffService staffService, OrganizationSlugService slugService) {
        this.platformAdmins = platformAdmins;
        this.users = users;
        this.organizations = organizations;
        this.settings = settings;
        this.staffService = staffService;
        this.slugService = slugService;
    }

    public ProvisionedOrganization provision(String name, String adminEmail) {
        Long platformUserId = IdentityContext.get();
        if (platformUserId == null || !platformAdmins.existsByUserId(platformUserId)) {
            throw new AccessDeniedException("Solo un administrador de plataforma puede aprovisionar organizaciones.");
        }
        User inviter = users.findById(platformUserId).orElseThrow();
        Organization organization = organizations.save(Organization.builder()
            .name(name.trim())
            .slug(slugService.slugFor(name))
            .type(OrganizationType.CLINIC)
            .status(OrganizationStatus.ACTIVE)
            .build());
        settings.save(OrganizationSettings.defaultsFor(organization));
        var invitation = staffService.inviteInitialAdmin(adminEmail, organization, inviter);
        return new ProvisionedOrganization(organization.getId(), organization.getName(),
            organization.getSlug(), invitation);
    }

    public record ProvisionedOrganization(Long organizationId, String name, String slug,
                                          StaffService.IssuedInvitation invitation) {}
}
