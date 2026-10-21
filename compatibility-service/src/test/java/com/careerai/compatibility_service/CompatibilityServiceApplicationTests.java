package com.careerai.compatibility_service;

import com.careerai.compatibility_service.repository.AnalysisRepository;
import com.careerai.compatibility_service.service.AnalysisCache;
import com.careerai.compatibility_service.service.OllamaAnalysisClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Charge le contexte complet (JPA sur H2 en mémoire, sécurité, Swagger ; sans Eureka ni Ollama). */
@SpringBootTest
@AutoConfigureMockMvc
class CompatibilityServiceApplicationTests {

	@Autowired private AnalysisRepository repository;
	@Autowired private OllamaAnalysisClient aiClient;
	@Autowired private AnalysisCache cache;
	@Autowired private MockMvc mvc;

	@Test
	void contextLoads() {
		assertThat(repository.count()).isZero();
		assertThat(aiClient.model()).isEqualTo("qwen-test");
		assertThat(cache.enabled()).isTrue();
	}

	@Test
	void laDocumentationSwaggerEstAccessibleSansJetonEtDeclareLeBearer() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
	}

	@Test
	void lApiExigeUnJeton() throws Exception {
		mvc.perform(get("/api/compatibility/analyses")).andExpect(status().isUnauthorized());
	}
}
