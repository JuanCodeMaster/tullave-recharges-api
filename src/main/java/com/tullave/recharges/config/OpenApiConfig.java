package com.tullave.recharges.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rechargesOpenApi() {
        return new OpenAPI().info(new Info()
                .title("tuLlave - API de Recargas Digitales")
                .version("v1")
                .description("""
                        API REST para registrar, consultar y eliminar recargas digitales de la tarjeta tuLlave.

                        Reglas de negocio:
                        - cardNumber: exactamente 16 dígitos numéricos.
                        - amount: entre 2.000 y 200.000 COP, máximo 2 decimales.
                        - paymentMethod: PSE, NEQUI, DAVIPLATA o CREDIT_CARD.
                        """)
                .contact(new Contact().name("Equipo tuLlave")));
    }
}
