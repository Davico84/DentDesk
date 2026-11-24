package com.odontorisas.service.appointment;

import com.odontorisas.common.OrganizationContext;
import com.odontorisas.persistence.entity.OrganizationSettings;
import com.odontorisas.persistence.entity.OrganizationSettings.StoredSegment;
import com.odontorisas.persistence.repository.OrganizationSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Horario de atención y zona horaria de la organización activa (add-appointments design.md D3/D5).
 *
 * <p>{@code organization_settings} no tiene RLS (metadato del tenant): el aislamiento lo da que la fila sea
 * <b>siempre</b> la de {@link OrganizationContext}, que sale del claim {@code oid} del token y nunca de la
 * petición. La escritura exige además {@code ADMIN} ({@code @PreAuthorize} en el controller).
 */
@Service
public class BusinessHoursService {

    private final OrganizationSettingsRepository settingsRepository;

    public BusinessHoursService(OrganizationSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    /** Horario de la clínica con su zona horaria: las reglas civiles de la agenda se evalúan en ella. */
    public record ClinicSchedule(ZoneId zone, BusinessHours hours) {
    }

    @Transactional(readOnly = true)
    public ClinicSchedule current() {
        return toSchedule(settingsRepository.findById(currentOrganizationId()).orElseThrow(
            () -> new IllegalStateException("La organización activa no tiene configuración.")));
    }

    /** Reemplaza la semana completa bajo {@code FOR UPDATE}; un horario inválido no llega acá ({@link BusinessHours#of}). */
    @Transactional
    public ClinicSchedule replace(BusinessHours hours) {
        OrganizationSettings settings = settingsRepository.findForUpdate(currentOrganizationId()).orElseThrow(
            () -> new IllegalStateException("La organización activa no tiene configuración."));
        settings.setBusinessHours(toStored(hours));
        return toSchedule(settingsRepository.saveAndFlush(settings));
    }

    private static ClinicSchedule toSchedule(OrganizationSettings settings) {
        return new ClinicSchedule(ZoneId.of(settings.getTimezone()), fromStored(settings.getBusinessHours()));
    }

    static BusinessHours fromStored(Map<DayOfWeek, List<StoredSegment>> stored) {
        if (stored == null) {
            return BusinessHours.DEFAULT;
        }
        Map<DayOfWeek, List<BusinessHours.Segment>> days = new EnumMap<>(DayOfWeek.class);
        stored.forEach((day, segments) -> days.put(day, segments.stream()
            .map(s -> new BusinessHours.Segment(LocalTime.parse(s.opens()), LocalTime.parse(s.closes())))
            .toList()));
        return BusinessHours.of(days);
    }

    static Map<DayOfWeek, List<StoredSegment>> toStored(BusinessHours hours) {
        Map<DayOfWeek, List<StoredSegment>> stored = new EnumMap<>(DayOfWeek.class);
        hours.asMap().forEach((day, segments) -> stored.put(day, segments.stream()
            .map(s -> new StoredSegment(s.opens().toString(), s.closes().toString()))
            .toList()));
        return stored;
    }

    private static Long currentOrganizationId() {
        Long organizationId = OrganizationContext.get();
        if (organizationId == null) {
            throw new IllegalStateException("No hay organización en el contexto de la petición.");
        }
        return organizationId;
    }
}
