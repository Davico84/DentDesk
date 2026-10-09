package com.odontorisas.presentation.controller;

import com.odontorisas.presentation.dto.ApiProblem;
import com.odontorisas.presentation.dto.DentalChartCreateRequest;
import com.odontorisas.presentation.dto.DentalChartFindingCreatedResponse;
import com.odontorisas.presentation.dto.DentalChartFindingRequest;
import com.odontorisas.presentation.dto.DentalChartLocationRequest;
import com.odontorisas.presentation.dto.DentalChartResponse;
import com.odontorisas.presentation.dto.DentalChartTextRequest;
import com.odontorisas.presentation.dto.ValidationProblem;
import com.odontorisas.service.dentalchart.DentalChartBlockView;
import com.odontorisas.service.dentalchart.DentalChartFindingData;
import com.odontorisas.service.dentalchart.DentalChartFindingService;
import com.odontorisas.service.dentalchart.DentalChartService;
import com.odontorisas.service.dentalchart.FindingLocationData;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Odontograma inicial: bloque + hallazgos (ADR-0012, ADR-0017 D3-D7). Todas las rutas cuelgan del
 * paciente ({@code patientId}) — hay como mucho un bloque {@code INITIAL} por paciente. El
 * <b>sellado no tiene ruta propia acá</b>: se sella junto con la Historia Clínica, a través de
 * {@code POST /patients/{id}/clinical-history/complete} (design.md D4) — {@code sealInitial()} no
 * es invocable fuera de {@code CloseAdmissionService}. Sin {@code @PreAuthorize} (design.md D10):
 * cualquier miembro autenticado de la clínica opera.
 */
@RestController
@RequestMapping("/patients/{patientId}/dental-chart")
@Tag(name = "dental-chart", description = "Odontograma inicial: bloque y hallazgos")
public class DentalChartController {

    private final DentalChartService dentalChartService;
    private final DentalChartFindingService findingService;

    public DentalChartController(DentalChartService dentalChartService, DentalChartFindingService findingService) {
        this.dentalChartService = dentalChartService;
        this.findingService = findingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Odontograma inicial creado en OPEN.",
            content = @Content(schema = @Schema(implementation = DentalChartResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos inválidos.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "404", description = "No existe el paciente en la clínica activa.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "El paciente ya tiene un odontograma inicial.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public DentalChartResponse create(@PathVariable Long patientId, @Valid @RequestBody DentalChartCreateRequest request) {
        return toResponse(dentalChartService.createInitial(patientId, request.examinedOn()));
    }

    @GetMapping
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "El odontograma del paciente, con sus hallazgos.",
            content = @Content(schema = @Schema(implementation = DentalChartResponse.class))),
        @ApiResponse(responseCode = "404", description = "El paciente no tiene odontograma en la clínica activa.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public DentalChartResponse get(@PathVariable Long patientId) {
        return toResponse(dentalChartService.getByPatientId(patientId));
    }

    @PutMapping("/specifications")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Especificaciones registradas.",
            content = @Content(schema = @Schema(implementation = DentalChartResponse.class))),
        @ApiResponse(responseCode = "404", description = "El paciente no tiene odontograma.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "El odontograma ya está sellado.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public DentalChartResponse setSpecifications(@PathVariable Long patientId, @RequestBody DentalChartTextRequest request) {
        return toResponse(dentalChartService.setSpecifications(patientId, request.text()));
    }

    @PutMapping("/observations")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Observaciones registradas.",
            content = @Content(schema = @Schema(implementation = DentalChartResponse.class))),
        @ApiResponse(responseCode = "404", description = "El paciente no tiene odontograma.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "El odontograma ya está sellado.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public DentalChartResponse setObservations(@PathVariable Long patientId, @RequestBody DentalChartTextRequest request) {
        return toResponse(dentalChartService.setObservations(patientId, request.text()));
    }

    @PostMapping("/findings")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Hallazgo agregado.",
            content = @Content(schema = @Schema(implementation = DentalChartFindingCreatedResponse.class))),
        @ApiResponse(responseCode = "400", description = "Localización incoherente con el tipo, o rango inválido.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "404", description = "El paciente no tiene odontograma.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "El odontograma está sellado, o el hallazgo ya existe.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public DentalChartFindingCreatedResponse addFinding(@PathVariable Long patientId,
                                                          @Valid @RequestBody DentalChartFindingRequest request) {
        return DentalChartFindingCreatedResponse.from(findingService.add(patientId, toData(request)));
    }

    @PutMapping("/findings/{findingId}")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Hallazgo actualizado.",
            content = @Content(schema = @Schema(implementation = DentalChartFindingCreatedResponse.class))),
        @ApiResponse(responseCode = "400", description = "Localización incoherente con el tipo, o rango inválido.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "404", description = "No existe ese hallazgo en el odontograma.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "El odontograma está sellado, o el hallazgo entra en conflicto.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public DentalChartFindingCreatedResponse updateFinding(@PathVariable Long patientId, @PathVariable Long findingId,
                                                             @Valid @RequestBody DentalChartFindingRequest request) {
        return DentalChartFindingCreatedResponse.from(findingService.update(patientId, findingId, toData(request)));
    }

    @DeleteMapping("/findings/{findingId}")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Hallazgo eliminado."),
        @ApiResponse(responseCode = "404", description = "No existe ese hallazgo en el odontograma.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "El odontograma ya está sellado.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFinding(@PathVariable Long patientId, @PathVariable Long findingId) {
        findingService.remove(patientId, findingId);
    }

    private DentalChartResponse toResponse(DentalChartBlockView block) {
        return DentalChartResponse.from(block, findingService.listByBlock(block.patientId()));
    }

    private static DentalChartFindingData toData(DentalChartFindingRequest r) {
        return new DentalChartFindingData(r.type(), r.subtype(), r.condition(), r.grade(), toData(r.location()));
    }

    private static FindingLocationData toData(DentalChartLocationRequest l) {
        return new FindingLocationData(l.locationKind(), l.toothA(), l.toothB(), l.surface(), l.arch(), l.teeth());
    }
}
