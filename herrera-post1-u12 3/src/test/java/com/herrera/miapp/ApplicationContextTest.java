package com.herrera.miapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class ApplicationContextTest {

    @Test
    void contextLoads() {
        // Verifica que todos los beans se construyen con el perfil dev (H2 en memoria).
    }
}
