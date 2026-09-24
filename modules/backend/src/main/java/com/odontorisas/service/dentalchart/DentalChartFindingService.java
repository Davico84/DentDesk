package com.odontorisas.service.dentalchart;

import com.odontorisas.common.Ages;
import com.odontorisas.common.DentalChartBlockStatus;
import com.odontorisas.common.FdiTooth;
import com.odontorisas.common.FindingType;
import com.odontorisas.common.LocationKind;
import com.odontorisas.persistence.entity.DentalChartBlock;
import com.odontorisas.persistence.entity.DentalChartFinding;
import com.odontorisas.persistence.entity.DentalChartFindingTooth;
import com.odontorisas.persistence.entity.DentalChartLocation;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.DentalChartBlockRepository;
import com.odontorisas.persistence.repository.DentalChartFindingRepository;
import com.odontorisas.persistence.repository.DentalChartFindingToothRepository;
import com.odontorisas.persistence.repository.PatientRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hallazgos del odontograma: agregar, corregir y eliminar mientras el bloque esté {@code OPEN}
 * (ADR-0017 D5/D7). {@code type}/{@code subtype}/{@code grade}/{@code condition} y la coherencia
 * de {@code location} ya llegaron validados por {@code @ValidFindingLocation} en el DTO
 * (design.md D9) cuando el caller es el controller; este servicio valida lo que necesita el
 * estado ya persistido: bloque abierto, duplicados, pieza ausente, cardinalidad del rango.
 */
@Service
@Transactional
public class DentalChartFindingService {

    private static final int MIN_RANGE_TEETH = 2;

    private final DentalChartBlockRepository blockRepository;
    private final DentalChartFindingRepository findingRepository;
    private final DentalChartFindingToothRepository findingToothRepository;
    private final PatientRepository patientRepository;

    public DentalChartFindingService(DentalChartBlockRepository blockRepository,
                                      DentalChartFindingRepository findingRepository,
                                      DentalChartFindingToothRepository findingToothRepository,
                                      PatientRepository patientRepository) {
        this.blockRepository = blockRepository;
        this.findingRepository = findingRepository;
        this.findingToothRepository = findingToothRepository;
        this.patientRepository = patientRepository;
    }

    /** Agrega un hallazgo; requiere el odontograma {@code OPEN} (spec: hallazgos solo mientras el
     *  odontograma esté abierto). */
    public AddFindingResult add(Long patientId, DentalChartFindingData data) {
        DentalChartBlock block = findOpenBlockOrThrow(patientId);
        FindingLocationData location = data.location();

        DentalChartFinding saved;
        List<Integer> teeth;
        if (location.locationKind() == LocationKind.TOOTH_RANGE) {
            teeth = validateAndNormalizeRange(location.teeth());
            saved = addRangeFinding(block, data, teeth);
        } else {
            requireNotOverAbsentTooth(block.getId(), data);
            saved = saveSimpleFinding(block, data, normalizeBetweenTeeth(data));
            teeth = List.of();
        }
        boolean warning = denticionWarning(block.getPatientId(), allTeeth(data, teeth));
        return new AddFindingResult(DentalChartFindingView.from(saved, teeth), warning);
    }

    /** Reemplaza un hallazgo existente; requiere el odontograma {@code OPEN}. */
    public AddFindingResult update(Long patientId, Long findingId, DentalChartFindingData data) {
        DentalChartBlock block = findOpenBlockOrThrow(patientId);
        DentalChartFinding existing = findFindingOrThrow(block.getId(), findingId);
        findingToothRepository.deleteAll(findingToothRepository.findByFindingIdOrderByPosition(findingId));

        FindingLocationData location = data.location();
        List<Integer> teeth;
        if (location.locationKind() == LocationKind.TOOTH_RANGE) {
            teeth = validateAndNormalizeRange(location.teeth());
            requireRangeNotDuplicated(block.getId(), data, teeth, findingId);
            applyToEntity(existing, data, new DentalChartLocation(LocationKind.TOOTH_RANGE, null, null, null, null));
            DentalChartFinding saved = save(existing);
            saveRangeTeeth(saved.getId(), teeth);
            boolean warning = denticionWarning(block.getPatientId(), teeth);
            return new AddFindingResult(DentalChartFindingView.from(saved, teeth), warning);
        }

        requireNotOverAbsentTooth(block.getId(), data, findingId);
        DentalChartLocation entityLocation = normalizeBetweenTeeth(data);
        applyToEntity(existing, data, entityLocation);
        DentalChartFinding saved = save(existing);
        teeth = List.of();
        boolean warning = denticionWarning(block.getPatientId(), allTeeth(data, teeth));
        return new AddFindingResult(DentalChartFindingView.from(saved, teeth), warning);
    }

    /** Elimina un hallazgo; requiere el odontograma {@code OPEN}. */
    public void remove(Long patientId, Long findingId) {
        DentalChartBlock block = findOpenBlockOrThrow(patientId);
        DentalChartFinding finding = findFindingOrThrow(block.getId(), findingId);
        findingToothRepository.deleteAll(findingToothRepository.findByFindingIdOrderByPosition(findingId));
        findingRepository.delete(finding);
    }

    @Transactional(readOnly = true)
    public List<DentalChartFindingView> listByBlock(Long patientId) {
        DentalChartBlock block = findAnyBlockOrThrow(patientId);
        return findingRepository.findByBlockId(block.getId()).stream()
            .map(f -> {
                List<Integer> teeth = f.getLocation().getLocationKind() == LocationKind.TOOTH_RANGE
                    ? findingToothRepository.findByFindingIdOrderByPosition(f.getId()).stream()
                        .map(DentalChartFindingTooth::getTooth).toList()
                    : List.<Integer>of();
                return DentalChartFindingView.from(f, teeth);
            })
            .toList();
    }

    // --- localización simple (SURFACE / TOOTH / BETWEEN_TEETH / ARCH) ---

    private DentalChartFinding saveSimpleFinding(DentalChartBlock block, DentalChartFindingData data,
                                                  DentalChartLocation location) {
        DentalChartFinding finding = DentalChartFinding.builder()
            .blockId(block.getId())
            .type(data.type().name())
            .subtype(data.subtype() == null ? "" : data.subtype())
            .condition(data.condition())
            .grade(data.grade())
            .location(location)
            .build();
        return save(finding);
    }

    private DentalChartFinding save(DentalChartFinding finding) {
        try {
            return findingRepository.save(finding);
        } catch (DataIntegrityViolationException raceLostToAnotherInsert) {
            throw new DentalChartFindingAlreadyExistsException();
        }
    }

    /** Orden canónico (design.md D2, spec: el mismo espacio entre piezas no se duplica por el
     *  orden): {@code toothA < toothB} siempre, para que {@code (11,21)} y {@code (21,11)} sean la
     *  misma localización. Solo aplica a {@code BETWEEN_TEETH}; las demás variantes se pasan tal cual. */
    private DentalChartLocation normalizeBetweenTeeth(DentalChartFindingData data) {
        FindingLocationData l = data.location();
        if (l.locationKind() != LocationKind.BETWEEN_TEETH || l.toothA() == null || l.toothB() == null) {
            return new DentalChartLocation(l.locationKind(), l.toothA(), l.toothB(), l.surface(), l.arch());
        }
        int a = Math.min(l.toothA(), l.toothB());
        int b = Math.max(l.toothA(), l.toothB());
        return new DentalChartLocation(l.locationKind(), a, b, l.surface(), l.arch());
    }

    private void applyToEntity(DentalChartFinding finding, DentalChartFindingData data, DentalChartLocation location) {
        finding.setType(data.type().name());
        finding.setSubtype(data.subtype() == null ? "" : data.subtype());
        finding.setCondition(data.condition());
        finding.setGrade(data.grade());
        finding.setLocation(location);
    }

    /** Rechaza un hallazgo de SUPERFICIE sobre una pieza marcada {@code PIEZA_AUSENTE} en el mismo
     *  odontograma (spec: pieza ausente con superficie) — solo SURFACE, no TOOTH: el implante
     *  (TOOTH) coexiste con la ausencia a propósito (ADR-0017 D7). */
    private void requireNotOverAbsentTooth(Long blockId, DentalChartFindingData data) {
        requireNotOverAbsentTooth(blockId, data, null);
    }

    private void requireNotOverAbsentTooth(Long blockId, DentalChartFindingData data, Long excludingFindingId) {
        FindingLocationData l = data.location();
        if (l.locationKind() != LocationKind.SURFACE || l.toothA() == null) {
            return;
        }
        boolean absent = findingRepository.findByBlockId(blockId).stream()
            .filter(f -> excludingFindingId == null || !f.getId().equals(excludingFindingId))
            .anyMatch(f -> FindingType.PIEZA_AUSENTE.name().equals(f.getType())
                && l.toothA().equals(f.getLocation().getToothA()));
        if (absent) {
            throw new SurfaceOnAbsentToothException(l.toothA());
        }
    }

    // --- localización de rango (TOOTH_RANGE) ---

    private DentalChartFinding addRangeFinding(DentalChartBlock block, DentalChartFindingData data,
                                                List<Integer> teeth) {
        // Bloquea el bloque para serializar la creación de rangos: es la única variante cuya
        // unicidad NO garantiza un índice de la base (design.md D2/D3).
        DentalChartBlock locked = blockRepository.lockById(block.getId()).orElseThrow(DentalChartBlockNotFoundException::new);
        DentalChartService.requireOpen(locked);

        requireRangeNotDuplicated(block.getId(), data, teeth, null);

        DentalChartFinding finding = DentalChartFinding.builder()
            .blockId(block.getId())
            .type(data.type().name())
            .subtype(data.subtype() == null ? "" : data.subtype())
            .condition(data.condition())
            .grade(data.grade())
            .location(new DentalChartLocation(LocationKind.TOOTH_RANGE, null, null, null, null))
            .build();
        DentalChartFinding saved = findingRepository.save(finding);
        saveRangeTeeth(saved.getId(), teeth);
        return saved;
    }

    private void saveRangeTeeth(Long findingId, List<Integer> teeth) {
        int position = 1;
        for (Integer tooth : teeth) {
            findingToothRepository.save(DentalChartFindingTooth.builder()
                .findingId(findingId)
                .position(position++)
                .tooth(tooth)
                .build());
        }
    }

    /** Dos invariantes de cardinalidad que ningún {@code CHECK} de SQL puede expresar (design.md
     *  D2): al menos dos piezas, sin repetidas, todas FDI reales. Defensa en profundidad — el
     *  boundary ({@code @ValidFindingLocation}) ya las exige cuando el caller es el controller. */
    private List<Integer> validateAndNormalizeRange(List<Integer> teeth) {
        if (teeth == null || teeth.size() < MIN_RANGE_TEETH) {
            throw new InvalidToothRangeException("Un rango exige al menos dos piezas.");
        }
        Set<Integer> seen = new HashSet<>();
        for (Integer tooth : teeth) {
            if (tooth == null || !FdiTooth.isValid(tooth)) {
                throw new InvalidToothRangeException("El rango incluye una pieza inválida: " + tooth + ".");
            }
            if (!seen.add(tooth)) {
                throw new InvalidToothRangeException("La pieza " + tooth + " está repetida dentro del rango.");
            }
        }
        return List.copyOf(teeth);
    }

    /** {@code TOOTH_RANGE} no tiene índice único posible (la identidad vive en la tabla hija): se
     *  compara contra los rangos existentes del mismo tipo+subtipo en el bloque, con el bloque ya
     *  bloqueado por el caller (design.md D2/D3). La comparación es por CONJUNTO de piezas, no por
     *  orden — dos registros con las mismas piezas son el mismo hallazgo aunque se hayan tecleado
     *  en otro orden. */
    private void requireRangeNotDuplicated(Long blockId, DentalChartFindingData data, List<Integer> teeth,
                                            Long excludingFindingId) {
        Set<Integer> newSet = new HashSet<>(teeth);
        String subtype = data.subtype() == null ? "" : data.subtype();
        boolean duplicate = findingRepository.findByBlockId(blockId).stream()
            .filter(f -> excludingFindingId == null || !f.getId().equals(excludingFindingId))
            .filter(f -> f.getLocation().getLocationKind() == LocationKind.TOOTH_RANGE)
            .filter(f -> data.type().name().equals(f.getType()) && subtype.equals(f.getSubtype()))
            .anyMatch(f -> {
                Set<Integer> existingSet = new HashSet<>(findingToothRepository
                    .findByFindingIdOrderByPosition(f.getId()).stream()
                    .map(DentalChartFindingTooth::getTooth).toList());
                return existingSet.equals(newSet);
            });
        if (duplicate) {
            throw new DentalChartFindingAlreadyExistsException();
        }
    }

    // --- aviso no bloqueante de dentición↔edad (INV-15) ---

    /** Solo advierte el caso que la norma documenta como real: una pieza TEMPORAL en un paciente
     *  ADULTO. El caso inverso (pieza permanente en un niño muy pequeño) no tiene un umbral claro
     *  en la norma — afirmar uno sería inventar contenido normativo (ver docs/adr/README.md,
     *  dimensión 1), así que deliberadamente no se comprueba. */
    private boolean denticionWarning(Long patientId, List<Integer> teeth) {
        if (teeth.isEmpty()) {
            return false;
        }
        Patient patient = patientRepository.findById(patientId).orElse(null);
        boolean isAdult = patient != null && !Ages.isMinor(patient.getBirthDate());
        if (!isAdult) {
            return false;
        }
        return teeth.stream().anyMatch(FdiTooth::isTemporary);
    }

    private List<Integer> allTeeth(DentalChartFindingData data, List<Integer> rangeTeeth) {
        if (!rangeTeeth.isEmpty()) {
            return rangeTeeth;
        }
        FindingLocationData l = data.location();
        List<Integer> teeth = new ArrayList<>();
        if (l.toothA() != null) {
            teeth.add(l.toothA());
        }
        if (l.toothB() != null) {
            teeth.add(l.toothB());
        }
        return teeth;
    }

    private DentalChartFinding findFindingOrThrow(Long blockId, Long findingId) {
        DentalChartFinding finding = findingRepository.findById(findingId)
            .orElseThrow(DentalChartFindingNotFoundException::new);
        if (!finding.getBlockId().equals(blockId)) {
            throw new DentalChartFindingNotFoundException();
        }
        return finding;
    }

    private DentalChartBlock findOpenBlockOrThrow(Long patientId) {
        DentalChartBlock block = findAnyBlockOrThrow(patientId);
        DentalChartService.requireOpen(block);
        return block;
    }

    private DentalChartBlock findAnyBlockOrThrow(Long patientId) {
        return blockRepository.findByPatientIdAndStatus(patientId, DentalChartBlockStatus.OPEN)
            .or(() -> blockRepository.findByPatientIdAndStatus(patientId, DentalChartBlockStatus.SEALED))
            .orElseThrow(DentalChartBlockNotFoundException::new);
    }
}
