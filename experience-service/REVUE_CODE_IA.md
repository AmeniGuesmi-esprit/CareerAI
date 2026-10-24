# Revue de code assistée par IA : vulnérabilités et recommandations de refactoring

Service analysé : `experience-service` (Spring Boot 3.2.0, Java 17).

## Méthode et limites

- Revue **statique** faite avec Claude (lecture du code, pas d'exécution) sur : `GeminiClient`, `PitchService`, `PitchController`,
  `ExperienceController`, `ExperienceService`, `JwtAuthFilter`, `SecurityConfig`, `GlobalExceptionHandler`, DTO, entité,
  `application.properties`, `Dockerfile`, prompts.
- Les niveaux de gravité sont un **jugement** (impact x probabilité dans le contexte d'un projet pédagogique), pas un score CVSS.
- Aucun outil d'analyse automatique n'a été lancé. À faire pour confirmer : `mvn test`, SpotBugs, et
  `mvn org.owasp:dependency-check-maven:check` (dépendances vulnérables).
- Le comportement de l'IA a été mesuré séparément par le banc d'évaluation (`PitchEvaluationTest`, 18 cas).

## Points positifs vérifiés

| Point | Où |
|---|---|
| Pas d'IDOR : l'identifiant utilisateur vient du JWT, jamais du corps, et chaque accès passe par `findByIdAndUserId` ; un objet d'un autre utilisateur renvoie 404 | `ExperienceController`, `ExperienceService.findOwned` |
| Service protégé même hors gateway (`anyRequest().authenticated()`, filtre JWT interne) | `SecurityConfig` |
| Clé Gemini dans un en-tête, jamais dans l'URL ni les logs ; message d'erreur interne non renvoyé au client | `GeminiClient`, `PitchController` (test `noCause_returns502_withoutLeak`) |
| Validation des entrées et bornes de taille (50 à 5000 caractères, 150/2000 pour l'expérience) | DTO |
| Défense contre l'injection de prompt : balises de délimitation neutralisées, données traitées comme données, contrôle déterministe des chiffres, juge LLM | `PitchService` |
| Masquage des e-mails, liens et téléphones avant l'envoi au LLM | `PitchService.maskPii` |
| Conteneur non root, build multi-étapes | `Dockerfile` |

## Vulnérabilités et risques

| # | Gravité | Constat | Recommandation | Statut |
|---|---|---|---|---|
| S1 | Moyenne | Valeurs par défaut sensibles dans `application.properties` : mot de passe Eureka `admin123`, base MySQL en `root` sans mot de passe, `useSSL=false`, `allowPublicKeyRetrieval=true`. | Aucun mot de passe par défaut ; profils `dev`/`prod` ; SSL activé en prod ; compte MySQL dédié à privilèges limités. | À faire |
| S2 | Moyenne | `JwtAuthFilter` : un jeton **sans date d'expiration** est accepté indéfiniment ; si la revendication `role` est absente, le rôle devient `ROLE_null` ; toute erreur est ignorée (`catch (Exception ignored)`) sans trace. | Refuser un jeton sans `exp` ; valider `role` contre une liste connue ; journaliser l'échec (sans le jeton) ; vérifier l'émetteur. | À faire |
| S3 | Moyenne | Appel **synchrone** très long : 3 appels LLM x jusqu'à 3 tentatives x timeout de 120 s. Latence observée jusqu'à 347 s. Chaque requête bloque un thread Tomcat ; la limite de 10 requêtes/min est **par utilisateur**, pas globale : plusieurs utilisateurs peuvent saturer le pool. | Délai global par requête ; limite de concurrence globale (sémaphore/bulkhead) ; à terme, traitement asynchrone (tâche + consultation du résultat). | À faire |
| S4 | Faible | Limite de débit et cache **en mémoire locale** : contournables avec plusieurs instances (Eureka), perdus au redémarrage ; le cache se vide entièrement quand il est plein (`cache.clear()`). | Caffeine (taille et TTL) pour le cache ; Bucket4j/Redis si plusieurs instances. | À faire |
| S5 | Faible | Données personnelles : seuls e-mails, liens et téléphones sont masqués. Noms, adresses, employeurs partent vers un tiers. Avec un palier **gratuit**, les conditions d'utilisation de l'API peuvent autoriser la réutilisation des données (à vérifier dans les conditions de Google). | Documenter la limite dans le rapport ; ne pas envoyer de données réelles en test ; palier payant et information de l'utilisateur en production (RGPD). | Limite à documenter |
| S6 | Faible | Injection de prompt : la neutralisation ne retire que 4 balises exactes ; le juge est lui-même un LLM. Impact limité (aucun outil ni effet de bord, le pire cas est un texte faux). Les 3 cas d'injection de l'évaluation sont conformes. | Ajouter des cas de test (balises avec espaces ou variantes Unicode, instructions en anglais). | Acceptable |
| S7 | Faible | Journaux : `log.debug` des problèmes du juge peut contenir des extraits du texte utilisateur ; `log.error(..., e)` écrit des traces complètes. | Garder ces lignes en DEBUG uniquement (jamais en production) ; ne pas journaliser le texte brut. | Acceptable |
| S8 | Faible | Dépendances anciennes : Spring Boot 3.2.0 (nov. 2023), JJWT 0.11.5. Des correctifs de sécurité existent dans les versions ultérieures. Non vérifié ici faute d'outil d'analyse. | Mettre à jour vers la dernière version 3.x supportée ; lancer OWASP dependency-check. | À vérifier |
| S9 | Faible | `ddl-auto=update` : le schéma évolue automatiquement, risqué en production. Dockerfile : images non épinglées, `-DskipTests`, pas de `HEALTHCHECK`. | Flyway/Liquibase + `validate` ; épingler les versions d'images ; lancer les tests en CI. | À faire |

## Robustesse et bugs

| # | Constat | Recommandation | Statut |
|---|---|---|---|
| B1 | `PitchController.handleGemini` ne reconnaissait la panne réseau que si la cause directe était `ResourceAccessException`. Or Spring enveloppe les timeouts de lecture dans une `RestClientException` : l'utilisateur recevait **502** au lieu de **503**. | Tester toute la chaîne de causes. | **Corrigé** (+ test `readTimeoutDuringBody_returns503`) |
| B2 | Handlers **morts** dans `PitchController` (`ResourceAccessException`, `RestClientResponseException`, `RestClientException`) : `GeminiClient` enveloppe tout dans `GeminiException`. Les tests de ces handlers simulent des cas qui n'arrivent plus. | Supprimer ces handlers et leurs tests. | À faire |
| B3 | **Deux formats d'erreur** : `GlobalExceptionHandler` renvoie `ApiResponse`, `PitchController` renvoie `ProblemDetail`. Les handlers locaux masquent les globaux sur `/api/pitch`. | Un seul `@RestControllerAdvice` et un seul format (ProblemDetail, RFC 7807). | À faire |
| B4 | Pas de handler générique `Exception` : on dépend du comportement par défaut de Spring. | Ajouter un handler qui renvoie un 500 générique et journalise le détail. | À faire |
| B5 | Deux requêtes identiques simultanées appellent toutes deux le LLM (pas de « single flight »), donc un coût doublé. | Caffeine `Cache.get(key, loader)`. | À faire |
| B6 | `Experience` utilise `@Data` sur une entité JPA : `equals`/`hashCode` sur tous les champs, instables pour une entité. | `@Getter @Setter` (+ `equals` sur l'id si besoin). | À faire |

## Recommandations de refactoring

1. **Découper `PitchService`** (environ 500 lignes, 6 responsabilités : limite de débit, cache, masquage, extraction, rédaction/validation, juge). Extraire :
   `RequestRateLimiter`, `PitchCache`, `PiiMasker`, `PitchValidator` (longueurs et chiffres), `PromptRepository`. `PitchService` ne garde que l'orchestration. Chaque classe devient testable seule.
2. **Externaliser les constantes** (`MAX_ATTEMPTS`, TTL du cache, limite de débit, bornes de longueur) dans un `@ConfigurationProperties`. Cela permet de régler le système sans recompiler, et de comparer des variantes dans l'évaluation.
3. **Choisir le modèle par étape** : `GeminiClient.generate` utilise un seul modèle. Un modèle plus rapide et moins cher pour l'extraction et le juge, un modèle plus fort pour la rédaction, serait une optimisation de coût et de latence à mesurer.
4. **Métriques de production** (Micrometer) : jetons, latence et taux de retry par étape, pour retrouver en production ce que le banc d'évaluation mesure hors ligne.
5. **Unifier la gestion d'erreurs** (B2, B3, B4) en un seul composant.

## Comment l'IA a servi à détecter les anomalies

- Le banc d'évaluation a révélé un défaut de transport : 6 échecs sur 18 (33 %), tous `RestClientException ... application/octet-stream`.
- Le diagnostic (timeout de lecture de 60 s enveloppé par Spring, donc jamais retenté) a été fait avec Claude à partir des journaux et du code. Après correction : 0 erreur brute, 16/18 conformes (89 %).
- La relecture des résultats a aussi mis en évidence que le test d'évaluation fixait le timeout à 60 s en dur, et conduit à B1.
- La présente revue de code a été conduite avec Claude selon la méthode ci-dessus.
