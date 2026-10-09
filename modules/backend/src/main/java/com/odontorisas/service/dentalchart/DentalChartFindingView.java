package com.odontorisas.service.dentalchart;

import com.odontorisas.common.FindingCondition;
import com.odontorisas.persistence.entity.DentalChartFinding;

import java.time.Instant;
import java.util.List;

public record DentalChartFindingView(
    Long id,
    Long blockId,
    String type,
    String subtype,
    FindingCondition condition,
    Short grade,
    FindingLocationData location,
    Instant createdAt,
    Long createdBy) {

    /** {@code teeth} viene de {@code dental_chart_finding_tooth}, aparte — este hallazgo no sabe
     *  de la tabla hija (D1). Vacía si {@code locationKind != TOOTH_RANGE}. */
    public static DentalChartFindingView from(DentalChartFinding f, List<Integer> teeth) {
        var l = f.getLocation();
        FindingLocationData location = new FindingLocationData(
            l.getLocationKind(), l.getToothA(), l.getToothB(), l.getSurface(), l.getArch(), teeth);
        return new DentalChartFindingView(
            f.getId(), f.getBlockId(), f.getType(), f.getSubtype(), f.getCondition(), f.getGrade(),
            location, f.getCreatedAt(), f.getCreatedBy());
    }
}
