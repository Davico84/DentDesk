package com.odontorisas.presentation.dto.validation;

import com.odontorisas.common.ColorBehavior;
import com.odontorisas.common.FdiTooth;
import com.odontorisas.common.FindingSubtype;
import com.odontorisas.common.FindingType;
import com.odontorisas.common.LocationKind;
import com.odontorisas.presentation.dto.DentalChartFindingRequest;
import com.odontorisas.presentation.dto.DentalChartLocationRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Comprueba {@link ValidFindingLocation}: seis invariantes cruzadas del hallazgo (design.md D9).
 * Acumula <b>todas</b> las violaciones encontradas antes de rechazar, no solo la primera — un
 * cliente que manda un hallazgo mal armado ve de una vez qué campos corregir.
 *
 * <p>Si {@code type} o {@code location} son {@code null}, no reporta nada aquí — lo cubren sus
 * propios {@code @NotNull} (mismo criterio que {@link ValidDiagnosisLocationValidator}).
 */
public class ValidFindingLocationValidator
    implements ConstraintValidator<ValidFindingLocation, DentalChartFindingRequest> {

    @Override
    public boolean isValid(DentalChartFindingRequest request, ConstraintValidatorContext context) {
        if (request == null || request.type() == null || request.location() == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        FindingType type = request.type();
        DentalChartLocationRequest location = request.location();

        boolean valid = true;
        valid &= checkLocation(type, location, context);
        valid &= checkSubtype(type, request.subtype(), context);
        valid &= checkGrade(type, request.grade(), context);
        valid &= checkCondition(type, request.condition(), context);
        return valid;
    }

    private boolean checkLocation(FindingType type, DentalChartLocationRequest location,
                                   ConstraintValidatorContext context) {
        if (location.locationKind() == null) {
            return reject(context, "location.locationKind", "La forma de localización es obligatoria.");
        }
        if (location.locationKind() != type.scope()) {
            return reject(context, "location.locationKind",
                "La localización no corresponde al alcance de " + type + " (" + type.scope() + ").");
        }
        return switch (location.locationKind()) {
            case SURFACE -> checkSurface(location, context);
            case TOOTH -> checkTooth(location, context);
            case BETWEEN_TEETH -> checkBetweenTeeth(location, context);
            case TOOTH_RANGE -> checkToothRange(location, context);
            case ARCH -> checkArch(location, context);
        };
    }

    private boolean checkSurface(DentalChartLocationRequest l, ConstraintValidatorContext context) {
        boolean valid = true;
        valid &= requireValidTooth(l.toothA(), "location.toothA", context);
        if (l.surface() == null) {
            valid = reject(context, "location.surface", "La superficie es obligatoria para este alcance.");
        } else if (l.toothA() != null && FdiTooth.isValid(l.toothA())
            && !FdiTooth.applicableSurfaces(l.toothA()).contains(l.surface())) {
            valid = reject(context, "location.surface",
                "La superficie " + l.surface() + " no aplica a la pieza " + l.toothA() + ".");
        }
        valid &= requireAbsent(l.toothB(), "location.toothB", context);
        valid &= requireAbsent(l.arch(), "location.arch", context);
        valid &= requireEmpty(l.teeth(), "location.teeth", context);
        return valid;
    }

    private boolean checkTooth(DentalChartLocationRequest l, ConstraintValidatorContext context) {
        boolean valid = requireValidTooth(l.toothA(), "location.toothA", context);
        valid &= requireAbsent(l.toothB(), "location.toothB", context);
        valid &= requireAbsent(l.surface(), "location.surface", context);
        valid &= requireAbsent(l.arch(), "location.arch", context);
        valid &= requireEmpty(l.teeth(), "location.teeth", context);
        return valid;
    }

    private boolean checkBetweenTeeth(DentalChartLocationRequest l, ConstraintValidatorContext context) {
        boolean valid = requireValidTooth(l.toothA(), "location.toothA", context);
        valid &= requireValidTooth(l.toothB(), "location.toothB", context);
        if (l.toothA() != null && l.toothA().equals(l.toothB())) {
            valid = reject(context, "location.toothB", "Las dos piezas no pueden ser la misma.");
        }
        valid &= requireAbsent(l.surface(), "location.surface", context);
        valid &= requireAbsent(l.arch(), "location.arch", context);
        valid &= requireEmpty(l.teeth(), "location.teeth", context);
        return valid;
    }

    private boolean checkToothRange(DentalChartLocationRequest l, ConstraintValidatorContext context) {
        boolean valid = true;
        List<Integer> teeth = l.teeth();
        if (teeth == null || teeth.size() < 2) {
            valid = reject(context, "location.teeth", "Un rango exige al menos dos piezas.");
        } else {
            Set<Integer> seen = new HashSet<>();
            for (Integer tooth : teeth) {
                if (tooth == null || !FdiTooth.isValid(tooth)) {
                    valid = reject(context, "location.teeth",
                        "El rango incluye una pieza inválida: " + tooth + ".");
                } else if (!seen.add(tooth)) {
                    valid = reject(context, "location.teeth",
                        "La pieza " + tooth + " está repetida dentro del rango.");
                }
            }
        }
        valid &= requireAbsent(l.toothA(), "location.toothA", context);
        valid &= requireAbsent(l.toothB(), "location.toothB", context);
        valid &= requireAbsent(l.surface(), "location.surface", context);
        valid &= requireAbsent(l.arch(), "location.arch", context);
        return valid;
    }

    private boolean checkArch(DentalChartLocationRequest l, ConstraintValidatorContext context) {
        boolean valid = true;
        if (l.arch() == null) {
            valid = reject(context, "location.arch", "El arco es obligatorio para este alcance.");
        }
        valid &= requireAbsent(l.toothA(), "location.toothA", context);
        valid &= requireAbsent(l.toothB(), "location.toothB", context);
        valid &= requireAbsent(l.surface(), "location.surface", context);
        valid &= requireEmpty(l.teeth(), "location.teeth", context);
        return valid;
    }

    private boolean checkSubtype(FindingType type, String subtype, ConstraintValidatorContext context) {
        boolean blank = subtype == null || subtype.isBlank();
        if (type.hasSubtypes()) {
            if (blank) {
                return reject(context, "subtype", "Este tipo exige elegir un subtipo.");
            }
            boolean belongsToType = type.subtypes().stream()
                .map(FindingSubtype::code)
                .anyMatch(code -> code.equals(subtype));
            if (!belongsToType) {
                return reject(context, "subtype", "\"" + subtype + "\" no es un subtipo de " + type + ".");
            }
            return true;
        }
        if (!blank) {
            return reject(context, "subtype", "Este tipo no admite subtipo.");
        }
        return true;
    }

    private boolean checkGrade(FindingType type, Short grade, ConstraintValidatorContext context) {
        if (type.requiresGrade()) {
            if (grade == null) {
                return reject(context, "grade", "Este tipo exige un grado.");
            }
            if (grade < 1 || grade > 3) {
                return reject(context, "grade", "El grado debe estar entre 1 y 3.");
            }
            return true;
        }
        if (grade != null) {
            return reject(context, "grade", "Este tipo no admite grado.");
        }
        return true;
    }

    private boolean checkCondition(FindingType type, Object condition, ConstraintValidatorContext context) {
        boolean requiresCondition = type.colorBehavior() == ColorBehavior.SEGUN_ESTADO;
        if (requiresCondition && condition == null) {
            return reject(context, "condition", "Este tipo exige el estado de conservación.");
        }
        if (!requiresCondition && condition != null) {
            return reject(context, "condition",
                "Este tipo no admite estado de conservación: su color es fijo.");
        }
        return true;
    }

    private boolean requireValidTooth(Integer tooth, String field, ConstraintValidatorContext context) {
        if (tooth == null) {
            return reject(context, field, "La pieza es obligatoria.");
        }
        if (!FdiTooth.isValid(tooth)) {
            return reject(context, field, "El número FDI no corresponde a ninguna pieza válida.");
        }
        return true;
    }

    private boolean requireAbsent(Object value, String field, ConstraintValidatorContext context) {
        if (value != null) {
            return reject(context, field, "Este campo no aplica a esta forma de localización.");
        }
        return true;
    }

    private boolean requireEmpty(List<?> value, String field, ConstraintValidatorContext context) {
        if (value != null && !value.isEmpty()) {
            return reject(context, field, "Este campo no aplica a esta forma de localización.");
        }
        return true;
    }

    private boolean reject(ConstraintValidatorContext context, String propertyPath, String message) {
        var builder = context.buildConstraintViolationWithTemplate(message);
        String[] segments = propertyPath.split("\\.");
        var nodeBuilder = builder.addPropertyNode(segments[0]);
        for (int i = 1; i < segments.length; i++) {
            nodeBuilder = nodeBuilder.addPropertyNode(segments[i]);
        }
        nodeBuilder.addConstraintViolation();
        return false;
    }
}
