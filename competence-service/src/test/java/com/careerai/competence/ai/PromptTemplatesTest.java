package com.careerai.competence.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplatesTest {

    @Test
    void advisorPrompt_containsRulesAndExample() {
        assertThat(PromptTemplates.ADVISOR_SYSTEM).contains("RÈGLES", "EXEMPLE");
    }

    @Test
    void judgePrompt_treatsTaggedContentAsData() {
        assertThat(PromptTemplates.JUDGE_SYSTEM).contains("<demande>", "<reponse>", "donnée");
    }

    @Test
    void judgeSchema_requiresVerdictAndReason() {
        assertThat(PromptTemplates.JUDGE_SCHEMA).containsKeys("type", "properties", "required");
        assertThat(PromptTemplates.JUDGE_SCHEMA.get("required").toString()).contains("verdict", "raison");
    }
}
