package com.odontorisas.infra.config;

import com.odontorisas.presentation.controller.AuthController;
import com.odontorisas.presentation.controller.BrandingController;
import com.odontorisas.presentation.dto.RegisterRequest;
import com.odontorisas.presentation.dto.UpdateBrandingRequest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida las reglas del customizer de errores transversales (design.md D3) <b>en local</b>
 * (surefire, sin Docker), sobre los {@code HandlerMethod} reales de los controllers.
 *
 * <p>Es la pieza de la que depende que las capacidades futuras hereden sus errores "gratis", así
 * que conviene que su lógica —derivar la ruta, detectar {@code @PreAuthorize}— no dependa de que
 * CI la ejerza: si se rompe, debe verse aquí.
 */
class OpenApiErrorsConfigTest {

    private final OperationCustomizer customizer = new OpenApiErrorsConfig().transversalErrorsCustomizer();

    private Operation customize(Class<?> controller, Object instance, String method, Class<?>... params)
            throws NoSuchMethodException {
        Method m = controller.getMethod(method, params);
        Operation operation = new Operation();
        customizer.customize(operation, new HandlerMethod(instance, m));
        return operation;
    }

    /** Operación protegida sin rol: hereda 401 y 500, pero no 403. */
    @Test
    void a_protected_operation_inherits_401_and_500_but_not_403() throws Exception {
        Operation operation = customize(BrandingController.class, new BrandingController(null), "getBranding");

        assertThat(operation.getResponses()).containsKeys("401", "500");
        assertThat(operation.getResponses()).doesNotContainKey("403");
    }

    /** Operación con {@code @PreAuthorize}: además hereda 403. */
    @Test
    void an_operation_restricted_by_role_also_inherits_403() throws Exception {
        Operation operation = customize(BrandingController.class, new BrandingController(null),
            "updateBranding", UpdateBrandingRequest.class);

        assertThat(operation.getResponses()).containsKeys("401", "403", "500");
    }

    /**
     * Ruta pública (/auth/**): NO puede fallar por "sesión ausente", así que no debe inventarse un
     * 401. Esto es lo que valida que la ruta se deriva bien de las anotaciones del controller.
     */
    @Test
    void a_public_operation_gets_500_but_no_session_401() throws Exception {
        Operation operation = customize(AuthController.class, new AuthController(null, null, null),
            "register", RegisterRequest.class);

        assertThat(operation.getResponses()).containsKey("500");
        assertThat(operation.getResponses()).doesNotContainKey("401");
    }

    /** Lo explícito manda: si la operación ya declara una respuesta, el customizer no la pisa. */
    @Test
    void it_never_overwrites_an_explicitly_declared_response() throws Exception {
        Method m = BrandingController.class.getMethod("getBranding");
        Operation operation = new Operation();
        operation.setResponses(new ApiResponses()
            .addApiResponse("500", new ApiResponse().description("mío, no lo toques")));

        customizer.customize(operation, new HandlerMethod(new BrandingController(null), m));

        assertThat(operation.getResponses().get("500").getDescription()).isEqualTo("mío, no lo toques");
    }
}
