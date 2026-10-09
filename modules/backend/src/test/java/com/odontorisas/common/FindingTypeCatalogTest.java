package com.odontorisas.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fija las 38 siglas del catálogo del odontograma (tarea {@code 1.5b}) contra el texto oficial de
 * NTS 188, verificado entrada por entrada en la tarea {@code 0.7} (2026-09-23). Son dato
 * normativo, se imprimen exactas: un typo en la sigla no lo atrapa ningún test de comportamiento,
 * y un {@code rename} del enum las arrastraría en silencio.
 *
 * <p>Cada fila de este test corresponde a una fila de {@code V16__dental_finding_catalog.sql}. Si
 * cambia una, cambia la otra — es exactamente lo que este test existe para atrapar.
 */
class FindingTypeCatalogTest {

    /** type, subtypeCode ("" si no tiene), sigla oficial esperada (null si el tipo no tiene sigla). */
    static Stream<Arguments> siglasOficiales() {
        return Stream.of(
            Arguments.of(FindingType.APARATO_ORTODONTICO_FIJO, "", null),
            Arguments.of(FindingType.APARATO_ORTODONTICO_REMOVIBLE, "", null),
            Arguments.of(FindingType.CORONA, "CM", "CM"),
            Arguments.of(FindingType.CORONA, "CF", "CF"),
            Arguments.of(FindingType.CORONA, "CMC", "CMC"),
            Arguments.of(FindingType.CORONA, "CV", "CV"),
            Arguments.of(FindingType.CORONA, "CLM", "CLM"),
            Arguments.of(FindingType.CORONA_TEMPORAL, "", "CT"),
            Arguments.of(FindingType.DEFECTOS_DESARROLLO_ESMALTE, "O", "O"),
            Arguments.of(FindingType.DEFECTOS_DESARROLLO_ESMALTE, "PE", "PE"),
            Arguments.of(FindingType.DIASTEMA, "", null),
            Arguments.of(FindingType.EDENTULO_TOTAL, "", null),
            Arguments.of(FindingType.ESPIGO_MUNON, "", null),
            Arguments.of(FindingType.FOSAS_FISURAS_PROFUNDAS, "", "FFP"),
            Arguments.of(FindingType.FRACTURA_DENTAL, "", null),
            Arguments.of(FindingType.FUSION, "", null),
            Arguments.of(FindingType.GEMINACION, "", null),
            Arguments.of(FindingType.GIROVERSION, "", null),
            Arguments.of(FindingType.IMPACTACION, "", "I"),
            Arguments.of(FindingType.IMPLANTE_DENTAL, "", "IMP"),
            Arguments.of(FindingType.LESION_CARIES, "MB", "MB"),
            Arguments.of(FindingType.LESION_CARIES, "CE", "CE"),
            Arguments.of(FindingType.LESION_CARIES, "CD", "CD"),
            Arguments.of(FindingType.LESION_CARIES, "CDP", "CDP"),
            Arguments.of(FindingType.MACRODONCIA, "", "MAC"),
            Arguments.of(FindingType.MICRODONCIA, "", "MIC"),
            Arguments.of(FindingType.MOVILIDAD_PATOLOGICA, "", "M"),
            Arguments.of(FindingType.PIEZA_AUSENTE, "DNE", "DNE"),
            Arguments.of(FindingType.PIEZA_AUSENTE, "DEX", "DEX"),
            Arguments.of(FindingType.PIEZA_AUSENTE, "DAO", "DAO"),
            Arguments.of(FindingType.PIEZA_EN_CLAVIJA, "", null),
            Arguments.of(FindingType.PIEZA_ECTOPICA, "", "E"),
            Arguments.of(FindingType.PIEZA_EN_ERUPCION, "", null),
            Arguments.of(FindingType.PIEZA_EXTRUIDA, "", null),
            Arguments.of(FindingType.PIEZA_INTRUIDA, "", null),
            Arguments.of(FindingType.PIEZA_SUPERNUMERARIA, "", "S"),
            Arguments.of(FindingType.PULPOTOMIA, "", "PP"),
            Arguments.of(FindingType.POSICION_ANORMAL, "M", "M"),
            Arguments.of(FindingType.POSICION_ANORMAL, "D", "D"),
            Arguments.of(FindingType.POSICION_ANORMAL, "V", "V"),
            Arguments.of(FindingType.POSICION_ANORMAL, "P", "P"),
            Arguments.of(FindingType.POSICION_ANORMAL, "L", "L"),
            Arguments.of(FindingType.PROTESIS_PARCIAL_FIJA, "", null),
            Arguments.of(FindingType.PROTESIS_COMPLETA, "", null),
            Arguments.of(FindingType.PROTESIS_PARCIAL_REMOVIBLE, "", null),
            Arguments.of(FindingType.REMANENTE_RADICULAR, "", "RR"),
            Arguments.of(FindingType.RESTAURACION_DEFINITIVA, "AM", "AM"),
            Arguments.of(FindingType.RESTAURACION_DEFINITIVA, "R", "R"),
            Arguments.of(FindingType.RESTAURACION_DEFINITIVA, "IV", "IV"),
            Arguments.of(FindingType.RESTAURACION_DEFINITIVA, "IM", "IM"),
            Arguments.of(FindingType.RESTAURACION_DEFINITIVA, "IE", "IE"),
            Arguments.of(FindingType.RESTAURACION_DEFINITIVA, "C", "C"),
            Arguments.of(FindingType.RESTAURACION_TEMPORAL, "", null),
            Arguments.of(FindingType.SELLANTE, "", "S"),
            Arguments.of(FindingType.SUPERFICIE_DESGASTADA, "", "DES"),
            Arguments.of(FindingType.TRATAMIENTO_CONDUCTO, "TC", "TC"),
            Arguments.of(FindingType.TRATAMIENTO_CONDUCTO, "PC", "PC"),
            Arguments.of(FindingType.TRANSPOSICION_DENTARIA, "", null)
        );
    }

    @ParameterizedTest(name = "{0} subtipo=\"{1}\" -> sigla {2}")
    @MethodSource("siglasOficiales")
    @DisplayName("cada (tipo, subtipo) tiene la sigla oficial de NTS 188 — ADR-0017 D7")
    void laSiglaCoincideConElTextoOficial(FindingType type, String subtypeCode, String expectedAcronym) {
        if (subtypeCode.isEmpty()) {
            assertThat(type.acronym()).as("sigla de %s sin subtipo", type).isEqualTo(expectedAcronym);
        } else {
            FindingSubtype subtype = type.subtypes().stream()
                .filter(s -> s.code().equals(subtypeCode))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                    type + " no declara el subtipo \"" + subtypeCode + "\""));
            assertThat(subtype.acronym()).as("sigla de %s/%s", type, subtypeCode).isEqualTo(expectedAcronym);
        }
    }

    @Test
    @DisplayName("el catálogo tiene exactamente 38 tipos (6.1.1-6.1.38)")
    void tiene38Tipos() {
        assertThat(FindingType.values()).hasSize(38);
    }

    @Test
    @DisplayName("cada tipo tiene sigla propia XOR subtipos con sigla — nunca ninguno, nunca ambos")
    void aciertoSiglaOSubtipos() {
        for (FindingType type : FindingType.values()) {
            boolean hasOwnAcronym = type.acronym() != null;
            boolean hasSubtypes = type.hasSubtypes();
            assertThat(hasOwnAcronym && hasSubtypes)
                .as("%s no puede tener sigla propia Y subtipos a la vez", type)
                .isFalse();
        }
    }

    @Test
    @DisplayName("6.1.19 movilidad patológica es ROJA, no azul (corrige a ADR-0013)")
    void movilidadEsRoja() {
        assertThat(FindingType.MOVILIDAD_PATOLOGICA.colorBehavior()).isEqualTo(ColorBehavior.SIEMPRE_ROJO);
        assertThat(FindingType.MOVILIDAD_PATOLOGICA.requiresGrade()).isTrue();
    }

    @Test
    @DisplayName("6.1.24/6.1.25 extruida e intruida son AZULES, no rojas")
    void extruidaEIntruidaSonAzules() {
        assertThat(FindingType.PIEZA_EXTRUIDA.colorBehavior()).isEqualTo(ColorBehavior.SIEMPRE_AZUL);
        assertThat(FindingType.PIEZA_INTRUIDA.colorBehavior()).isEqualTo(ColorBehavior.SIEMPRE_AZUL);
    }

    @Test
    @DisplayName("6.1.13 giroversión es azul SIN condicional — la regla de contagio no existe")
    void giroversionEsAzulSinCondicion() {
        assertThat(FindingType.GIROVERSION.colorBehavior()).isEqualTo(ColorBehavior.SIEMPRE_AZUL);
    }

    @Test
    @DisplayName("la sigla 'M' colisiona a propósito entre movilidad y mesializado")
    void colisionDeSiglaM() {
        assertThat(FindingType.MOVILIDAD_PATOLOGICA.acronym()).isEqualTo("M");
        FindingSubtype mesializado = FindingType.POSICION_ANORMAL.subtypes().stream()
            .filter(s -> s.code().equals("M"))
            .findFirst()
            .orElseThrow();
        assertThat(mesializado.acronym()).isEqualTo("M");
        // Mismo texto, tipos distintos: la clave real es (type, subtype), no la sigla.
        assertThat(FindingType.MOVILIDAD_PATOLOGICA).isNotEqualTo(FindingType.POSICION_ANORMAL);
    }

    @Test
    @DisplayName("la sigla 'S' colisiona a propósito entre supernumeraria y sellante")
    void colisionDeSiglaS() {
        assertThat(FindingType.PIEZA_SUPERNUMERARIA.acronym()).isEqualTo("S");
        assertThat(FindingType.SELLANTE.acronym()).isEqualTo("S");
        assertThat(FindingType.PIEZA_SUPERNUMERARIA.scope()).isEqualTo(LocationKind.BETWEEN_TEETH);
        assertThat(FindingType.SELLANTE.scope()).isEqualTo(LocationKind.SURFACE);
    }

    @Test
    @DisplayName("6.1.29/6.1.31 son de RANGO; 6.1.31 es prótesis removible, no recesión gingival")
    void protesisParcialEsRango() {
        assertThat(FindingType.PROTESIS_PARCIAL_FIJA.scope()).isEqualTo(LocationKind.TOOTH_RANGE);
        assertThat(FindingType.PROTESIS_PARCIAL_REMOVIBLE.scope()).isEqualTo(LocationKind.TOOTH_RANGE);
    }

    @Test
    @DisplayName("solo movilidad exige grado; solo corona/corona temporal/DDE exigen especificación")
    void requiresGradeYRequiresSpecificationSonExcepcionales() {
        List<FindingType> requiereGrado = Stream.of(FindingType.values())
            .filter(FindingType::requiresGrade)
            .toList();
        assertThat(requiereGrado).containsExactly(FindingType.MOVILIDAD_PATOLOGICA);

        List<FindingType> requiereEspecificacion = Stream.of(FindingType.values())
            .filter(FindingType::requiresSpecification)
            .toList();
        assertThat(requiereEspecificacion).containsExactlyInAnyOrder(
            FindingType.CORONA, FindingType.CORONA_TEMPORAL, FindingType.DEFECTOS_DESARROLLO_ESMALTE);
    }
}
