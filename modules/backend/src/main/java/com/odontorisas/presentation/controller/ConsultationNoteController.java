package com.odontorisas.presentation.controller;

import com.odontorisas.presentation.dto.ActiveBackgroundResponse;
import com.odontorisas.presentation.dto.ApiProblem;
import com.odontorisas.presentation.dto.BackgroundEntryRequest;
import com.odontorisas.presentation.dto.BackgroundEntryResponse;
import com.odontorisas.presentation.dto.ConsultationNoteRequest;
import com.odontorisas.presentation.dto.ConsultationNoteResponse;
import com.odontorisas.presentation.dto.CorrectionRequest;
import com.odontorisas.presentation.dto.CorrectionResponse;
import com.odontorisas.presentation.dto.DiagnosisRequest;
import com.odontorisas.presentation.dto.NoteDiagnosisResponse;
import com.odontorisas.presentation.dto.OpenConsultationNoteRequest;
import com.odontorisas.presentation.dto.ValidationProblem;
import com.odontorisas.service.clinicalhistory.DiagnosisData;
import com.odontorisas.service.consultationnote.BackgroundEntryData;
import com.odontorisas.service.consultationnote.ClinicalBackgroundService;
import com.odontorisas.service.consultationnote.ConsultationNoteCorrectionService;
import com.odontorisas.service.consultationnote.ConsultationNoteData;
import com.odontorisas.service.consultationnote.ConsultationNoteDiagnosisService;
import com.odontorisas.service.consultationnote.ConsultationNoteService;
import com.odontorisas.service.consultationnote.CorrectionData;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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

import java.util.List;

/**
 * Notas de consulta del paciente y sus antecedentes vigentes (`update-clinical-history`, ADR-0020).
 * Todo cuelga del paciente. <b>Sin {@code @PreAuthorize}</b> (design.md D9): cualquier miembro
 * autenticado de la clínica opera hasta que llegue {@code staff}.
 */
@RestController
@RequestMapping("/patients/{patientId}")
@Tag(name = "consultation-notes", description = "Notas de consulta posteriores a la admisión")
public class ConsultationNoteController {

    private static final String PROBLEM = "application/problem+json";

    private final ConsultationNoteService noteService;
    private final ConsultationNoteDiagnosisService diagnosisService;
    private final ConsultationNoteCorrectionService correctionService;
    private final ClinicalBackgroundService backgroundService;

    public ConsultationNoteController(ConsultationNoteService noteService,
                                      ConsultationNoteDiagnosisService diagnosisService,
                                      ConsultationNoteCorrectionService correctionService,
                                      ClinicalBackgroundService backgroundService) {
        this.noteService = noteService;
        this.diagnosisService = diagnosisService;
        this.correctionService = correctionService;
        this.backgroundService = backgroundService;
    }

    @PostMapping("/consultation-notes")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Nota abierta.",
            content = @Content(schema = @Schema(implementation = ConsultationNoteResponse.class))),
        @ApiResponse(responseCode = "400", description = "Fecha de atención futura o anterior a la última nota sellada.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "404", description = "No existe el paciente en la clínica activa.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "Historia no sellada, paciente dado de baja, o ya hay una nota abierta.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
    })
    public ConsultationNoteResponse openConsultationNote(@PathVariable Long patientId,
                                         @Valid @RequestBody OpenConsultationNoteRequest request) {
        return ConsultationNoteResponse.from(noteService.open(patientId, request.attendedAt()));
    }

    @GetMapping("/consultation-notes")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notas del paciente en orden cronológico de atención.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ConsultationNoteResponse.class)))),
        @ApiResponse(responseCode = "404", description = "No existe el paciente en la clínica activa.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
    })
    public List<ConsultationNoteResponse> listConsultationNotes(@PathVariable Long patientId) {
        return noteService.list(patientId).stream().map(ConsultationNoteResponse::from).toList();
    }

    @GetMapping("/consultation-notes/{noteId}")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "La nota con sus diagnósticos, antecedentes y correcciones.",
            content = @Content(schema = @Schema(implementation = ConsultationNoteResponse.class))),
        @ApiResponse(responseCode = "404", description = "No existe la nota para ese paciente en la clínica activa.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
    })
    public ConsultationNoteResponse getConsultationNote(@PathVariable Long patientId, @PathVariable Long noteId) {
        return ConsultationNoteResponse.from(noteService.get(patientId, noteId));
    }

    @PutMapping("/consultation-notes/{noteId}")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Nota guardada; sigue abierta.",
            content = @Content(schema = @Schema(implementation = ConsultationNoteResponse.class))),
        @ApiResponse(responseCode = "400", description = "Fecha de atención o de próxima cita inválida.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "404", description = "No existe la nota.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "La nota está sellada.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
    })
    public ConsultationNoteResponse updateConsultationNote(@PathVariable Long patientId, @PathVariable Long noteId,
                                           @Valid @RequestBody ConsultationNoteRequest r) {
        return ConsultationNoteResponse.from(noteService.update(patientId, noteId, new ConsultationNoteData(
            r.attendedAt(), r.reason(), r.illnessDuration(), r.symptomsAndSigns(), r.previousTreatment(),
            r.biologicalFunctions(), r.examination(), r.treatment(), r.auxiliaryExams(), r.referral(),
            r.nextAppointmentOn())));
    }

    @DeleteMapping("/consultation-notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Nota vacía descartada."),
        @ApiResponse(responseCode = "404", description = "No existe la nota.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "La nota tiene contenido o está sellada.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
    })
    public void discardConsultationNote(@PathVariable Long patientId, @PathVariable Long noteId) {
        noteService.discard(patientId, noteId);
    }

    @PostMapping("/consultation-notes/{noteId}/seal")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Nota sellada.",
            content = @Content(schema = @Schema(implementation = ConsultationNoteResponse.class))),
        @ApiResponse(responseCode = "400", description = "Falta contenido obligatorio (missingFields) o la fecha quedó inválida.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "404", description = "No existe la nota.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "La nota ya está sellada.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
    })
    public ConsultationNoteResponse sealConsultationNote(@PathVariable Long patientId, @PathVariable Long noteId) {
        return ConsultationNoteResponse.from(noteService.seal(patientId, noteId));
    }

    @PostMapping("/consultation-notes/{noteId}/diagnoses")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Diagnóstico agregado.",
            content = @Content(schema = @Schema(implementation = NoteDiagnosisResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos inválidos, código inexistente o repetitivo sin registro previo.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "404", description = "No existe la nota.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "La nota está sellada.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
    })
    public NoteDiagnosisResponse addNoteDiagnosis(@PathVariable Long patientId, @PathVariable Long noteId,
                                              @Valid @RequestBody DiagnosisRequest request) {
        return NoteDiagnosisResponse.from(diagnosisService.add(patientId, noteId, toData(request)));
    }

    @PutMapping("/consultation-notes/{noteId}/diagnoses/{diagnosisId}")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Diagnóstico actualizado.",
            content = @Content(schema = @Schema(implementation = NoteDiagnosisResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos inválidos, código inexistente o repetitivo sin registro previo.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "404", description = "No existe la nota o el diagnóstico.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "La nota está sellada.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
    })
    public NoteDiagnosisResponse updateNoteDiagnosis(@PathVariable Long patientId, @PathVariable Long noteId,
                                                 @PathVariable Long diagnosisId,
                                                 @Valid @RequestBody DiagnosisRequest request) {
        return NoteDiagnosisResponse.from(diagnosisService.update(patientId, noteId, diagnosisId, toData(request)));
    }

    @DeleteMapping("/consultation-notes/{noteId}/diagnoses/{diagnosisId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Diagnóstico eliminado."),
        @ApiResponse(responseCode = "404", description = "No existe la nota o el diagnóstico.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "La nota está sellada.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
    })
    public void removeNoteDiagnosis(@PathVariable Long patientId, @PathVariable Long noteId,
                                @PathVariable Long diagnosisId) {
        diagnosisService.remove(patientId, noteId, diagnosisId);
    }

    @PostMapping("/consultation-notes/{noteId}/background-entries")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Asiento registrado en la nota.",
            content = @Content(schema = @Schema(implementation = BackgroundEntryResponse.class))),
        @ApiResponse(responseCode = "400", description = "Alta sin categoría o descripción, o baja sin el antecedente que cierra.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "404", description = "No existe la nota.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "La nota está sellada, o el antecedente a dar de baja no está vigente.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
    })
    public BackgroundEntryResponse addBackgroundEntry(@PathVariable Long patientId, @PathVariable Long noteId,
                                                      @Valid @RequestBody BackgroundEntryRequest r) {
        return BackgroundEntryResponse.from(backgroundService.add(patientId, noteId,
            new BackgroundEntryData(r.kind(), r.category(), r.description(), r.closesEntryId())));
    }

    @DeleteMapping("/consultation-notes/{noteId}/background-entries/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Asiento quitado de la nota abierta."),
        @ApiResponse(responseCode = "404", description = "No existe la nota o el asiento.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "La nota está sellada.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
    })
    public void removeBackgroundEntry(@PathVariable Long patientId, @PathVariable Long noteId,
                                      @PathVariable Long entryId) {
        backgroundService.remove(patientId, noteId, entryId);
    }

    @PostMapping("/consultation-notes/{noteId}/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Corrección registrada; el original no cambia.",
            content = @Content(schema = @Schema(implementation = CorrectionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Sin motivo, campo inexistente (p. ej. un diagnóstico) o campo que estaba vacío.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "403", description = "Solo quien selló la nota puede corregirla.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "404", description = "No existe la nota.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "La nota sigue abierta.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
    })
    public CorrectionResponse addNoteCorrection(@PathVariable Long patientId, @PathVariable Long noteId,
                                            @Valid @RequestBody CorrectionRequest r) {
        return CorrectionResponse.from(correctionService.add(patientId, noteId,
            new CorrectionData(r.field(), r.correctedText(), r.reason())));
    }

    @GetMapping("/clinical-background")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Antecedentes vigentes con su origen.",
            content = @Content(schema = @Schema(implementation = ActiveBackgroundResponse.class))),
        @ApiResponse(responseCode = "404", description = "No existe el paciente en la clínica activa.",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
    })
    public ActiveBackgroundResponse getActiveBackground(@PathVariable Long patientId) {
        return ActiveBackgroundResponse.from(backgroundService.active(patientId));
    }

    private static DiagnosisData toData(DiagnosisRequest r) {
        return new DiagnosisData(r.cie10Code(), r.ubicacion(), r.fdiNumber(), r.tipoDiagnostico());
    }
}
