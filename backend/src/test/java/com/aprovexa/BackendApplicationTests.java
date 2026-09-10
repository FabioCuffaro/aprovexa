package com.aprovexa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackendApplicationTests {

    @Test
    void applicationEntryPointExists() {
        assertThat(BackendApplication.class).isNotNull();
    }
}
