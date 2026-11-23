package com.catedra.turnos.web;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void validationErrorIncludesFieldErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "appointmentHoldRequestDTO");
        bindingResult.addError(new FieldError(
                "appointmentHoldRequestDTO",
                "professionalId",
                null,
                false,
                null,
                null,
                "must not be null"
        ));
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("target", String.class),
                0
        );

        ResponseEntity<ProblemDetail> response = new GlobalExceptionHandler()
                .handleValidation(new MethodArgumentNotValidException(parameter, bindingResult));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("VALIDATION_ERROR");
        List<?> fieldErrors = (List<?>) response.getBody().getProperties().get("fieldErrors");
        assertThat(fieldErrors).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> fieldError = (Map<String, Object>) fieldErrors.get(0);
        assertThat(fieldError)
                .containsEntry("objectName", "appointmentHoldRequestDTO")
                .containsEntry("field", "professionalId")
                .containsEntry("message", "must not be null");
    }

    private void target(String value) {
    }
}
