package com.careerai.skillgap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recherche lexicale du RAG (repli sans clé HF), sur la vraie base de connaissances (knowledge/*.json) :
 * offres de plusieurs domaines, avec la présentation de l'entreprise et les conditions qu'une vraie offre contient.
 */
class RagRetrievalTest {

    /** Mêmes valeurs que rag.lexical-min-score et rag.lexical-chunk-chars. */
    private static final double MIN_SCORE = 0.10;
    private static final int CHUNK_CHARS = 250;

    /** Présentation de l'entreprise, avantages, recrutement : présents dans presque toutes les offres. */
    private static final String FILLER = """
            Notre société, leader régional du conseil en transformation numérique, accompagne depuis quinze ans
            des clients prestigieux dans la banque, l'assurance et les télécommunications. Rejoindre notre structure,
            c'est intégrer une communauté bienveillante où chacun contribue à des missions variées et stimulantes.
            Avantages : mutuelle familiale, tickets restaurant, télétravail partiel deux jours par semaine,
            horaires flexibles, prime annuelle sur objectifs, séminaires d'intégration, salle de sport, parking.
            Localisation : bureaux modernes au centre-ville, accessibles en transports en commun.
            Qualités attendues : rigueur, autonomie, curiosité, sens du service client, esprit d'initiative,
            capacité à communiquer avec des interlocuteurs variés, goût pour le partage et l'entraide.
            Processus de recrutement : entretien téléphonique, entretien technique puis rencontre avec la direction.
            Contrat à durée indéterminée, rémunération attractive selon profil, période d'essai de trois mois.
            """;

    private static final String BACKEND = """
            Missions :
            - Développer des microservices Java avec Spring Boot
            - Conteneuriser avec Docker et déployer sur Kubernetes
            - Mettre en place la CI/CD avec GitLab CI
            - Échanges asynchrones avec Apache Kafka
            """;

    private static final List<String> SLUGS = new ArrayList<>();
    private static final List<String> TEXTS = new ArrayList<>();

    @BeforeAll
    static void loadKnowledgeBase() throws Exception {
        for (JsonNode n : RagService.readKnowledgeBase(new ObjectMapper())) {
            SLUGS.add(n.path("slug").asText());
            TEXTS.add(RagService.lexicalText(n.path("skill").asText(), n.path("content").asText()));
        }
    }

    // ── Informatique ──

    /** Régression : en comparant l'offre entière, une offre de plus de ~1000 caractères ne retenait aucune fiche. */
    @Test
    void longBackendOfferFindsRequestedSkills() {
        assertThat(retained(BACKEND + FILLER + FILLER))
                .contains("docker", "kubernetes", "kafka", "spring-boot", "ci-cd");
    }

    @Test
    void paragraphOfferFindsRequestedSkills() {
        String offer = "Au sein de l'équipe produit, vous développerez des microservices Java avec Spring Boot, "
                + "les conteneuriserez avec Docker et les déploierez sur Kubernetes, tout en participant à la "
                + "messagerie Apache Kafka et à l'amélioration continue de nos pipelines GitLab CI.\n" + FILLER;
        assertThat(retained(offer)).contains("docker", "kubernetes", "kafka", "spring-boot", "ci-cd");
    }

    /** Le « profil recherché » est souvent en fin d'offre : tous les blocs doivent être analysés. */
    @Test
    void skillsAtTheEndOfALongOfferAreFound() {
        String offer = FILLER.repeat(5) + BACKEND;
        assertThat(offer.length()).isLessThanOrEqualTo(6000);
        assertThat(retained(offer)).contains("docker", "kubernetes", "kafka", "spring-boot", "ci-cd");
    }

    @Test
    void dataOfferFindsDataSkills() {
        assertThat(retained("Profil recherché : Data Scientist. Vous maîtrisez Python, Pandas et NumPy, ainsi que "
                + "scikit-learn pour le machine learning. Une expérience MLflow est un plus.\n" + FILLER))
                .contains("python", "data-analysis", "machine-learning", "mlops");
    }

    // ── Autres domaines ──

    @Test
    void marketingOffer() {
        assertThat(retained("Chargé(e) de marketing digital. Missions : gérer les campagnes Google Ads et Meta Ads, "
                + "optimiser le référencement naturel (SEO) du site, suivre les performances dans Google Analytics 4, "
                + "animer les réseaux sociaux de la marque.\n" + FILLER))
                .contains("seo", "sea-google-ads", "social-ads", "google-analytics");
    }

    @Test
    void accountingOffer() {
        assertThat(retained("Comptable confirmé(e). Missions : tenue de la comptabilité générale, rapprochements "
                + "bancaires, déclarations fiscales (TVA, impôt sur les sociétés), préparation du bilan. Maîtrise de "
                + "Sage et d'Excel avancé (tableaux croisés dynamiques).\n" + FILLER))
                .contains("comptabilite-generale", "fiscalite", "excel-avance");
    }

    @Test
    void nursingOffer() {
        assertThat(retained("Infirmier(e) en service de chirurgie. Vous assurez les soins dans le respect des règles "
                + "d'hygiène hospitalière et de prévention des infections, maîtrisez les gestes d'urgence et assurez "
                + "la traçabilité dans le dossier patient informatisé.\n" + FILLER))
                .contains("hygiene-infections", "premiers-secours", "informatique-sante");
    }

    @Test
    void mechanicalEngineeringOffer() {
        assertThat(retained("Ingénieur(e) conception mécanique. Modélisation 3D sous SolidWorks, mises en plan, suivi "
                + "de la maintenance industrielle des équipements (AMDEC, GMAO, indicateurs MTBF).\n" + FILLER))
                .contains("solidworks", "maintenance-industrielle");
    }

    @Test
    void hrOfferFindsHrSkills() {
        assertThat(retained("Chargé(e) des ressources humaines. Missions : recrutement et sourcing des candidats, "
                + "administration du personnel et paie, plan de développement des compétences.\n" + FILLER))
                .contains("recrutement", "paie", "gpec");
    }

    // ── Bruit des offres ──

    /** « Processus de recrutement : entretien... » ne doit pas faire remonter la fiche RH d'une offre technique. */
    @Test
    void recruitmentBoilerplateDoesNotMatchHrFiche() {
        assertThat(retained(BACKEND + FILLER)).doesNotContain("recrutement");
    }

    @Test
    void offerWithoutRequirementsRetainsNothing() {
        assertThat(retained(FILLER)).isEmpty();
    }

    @Test
    void boilerplateSectionsAreRemovedFromTheQuery() {
        String offer = """
                Développeur Full-Stack Java / Angular Junior
                Profil recherché :
                - Java, Spring Boot et Angular
                - Une connaissance d'un fournisseur cloud (AWS ou Azure) est un plus

                Nous offrons : un encadrement par des développeurs seniors, une formation continue,
                des horaires flexibles et un jour de télétravail par semaine.

                Compétences appréciées : Docker
                """;
        String query = RagService.requirementsText(offer);
        assertThat(query).contains("Java, Spring Boot et Angular", "AWS ou Azure", "Docker")
                .doesNotContain("formation continue", "télétravail");
    }

    @Test
    void lineStartingWithAHeaderWordIsKeptWithoutColon() {
        assertThat(RagService.requirementsText("Compétences :\n- Localisation des applications (i18n)\n- Angular"))
                .contains("Localisation des applications");
    }

    private static List<String> retained(String offer) {
        // même enchaînement que RagService.retrieve en mode lexical
        double[] s = RagService.lexicalScores(TEXTS, RagService.requirementsText(offer), CHUNK_CHARS);
        return IntStream.range(0, s.length).filter(i -> s[i] >= MIN_SCORE).mapToObj(SLUGS::get).toList();
    }
}
