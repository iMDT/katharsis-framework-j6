package io.katharsis.errorhandling;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ErrorResponseBuilderTest {

    private static final int STATUS = 500;

    @Test
    public void shouldSetStatus() throws Exception {
        ErrorResponse response = ErrorResponse.builder()
                .setStatus(STATUS)
                .build();

        assertThat(response.getHttpStatus()).isEqualTo(STATUS);
    }

}