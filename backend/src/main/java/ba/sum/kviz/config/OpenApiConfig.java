package ba.sum.kviz.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI quizOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Kviz API")
                        .version("1.0")
                        .description("""
                                REST API sustava za organizaciju i provođenje online kvizova
                                s automatskim vrednovanjem odgovora.

                                Sudionici mogu sudjelovati pojedinačno ili u timovima.
                                Svako pitanje ima vremensko ograničenje, a broj bodova ovisi
                                o točnosti i brzini odgovora.

                                Za pristup zaštićenim rutama prijavite se kroz
                                POST /api/auth/login i unesite dobiveni token
                                klikom na dugme Authorize.
                                """)
                        .contact(new Contact().name("Marko")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token dobiven prijavom")));
    }
}