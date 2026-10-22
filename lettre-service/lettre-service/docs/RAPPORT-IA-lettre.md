# lettre-service : bilan IA selon la grille

Microservice réalisé par Farah Amami. Il génère et améliore des lettres de motivation à partir d'un CV et d'une offre (Gemini, Cohere, Mistral via des API compatibles OpenAI).

> Les sections marquées **[À COMPLÉTER]** se remplissent automatiquement : lancez le benchmark (§ 0) puis copiez les tableaux de `target/benchmark/summary.md`. Tous les autres éléments (code, tests, protocole) sont déjà en place.

## 0. Reproduire toutes les mesures

Depuis le dossier qui contient `pom.xml` (Windows PowerShell, Maven fourni avec IntelliJ) :

```powershell
$mvn = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"

# 1. Tests hors-ligne (aucun appel réseau)
& $mvn clean test

# 2. Benchmark réel (clés lues dans application.properties ; ~60 appels, ~10 min)
& $mvn test "-Dtest=LlmBenchmarkTest" "-Dbenchmark=true" "-DBENCH_PROVIDERS=cohere,gemini" "-DBENCH_PAUSE_MS=4000"
```

Options : `"-DBENCH_RUNS=1"` (moins d'appels), `"-DBENCH_CASES=C01,C02"` (quelques cas seulement), `"-DBENCH_PROVIDERS=..."` (le premier fournisseur de la liste sert aussi à l'expérience de cache).

Fichiers produits dans `target/benchmark/` :

| Fichier | Contenu |
|---|---|
| `summary.md` | Tous les tableaux de ce rapport (latence, qualité, cache, échecs, productivité) |
| `results.csv` | Une ligne par appel (latence, mots, score, hallucinations…) |
| `metrics.json` | Instantané de `/api/lettres/ai/metrics` (p50/p95, cache) |
| `letters/` | Chaque lettre générée |
| `human-eval-sheet.csv` + `letters/blind/` | Lettres anonymisées et mélangées pour la notation humaine à l'aveugle |
| `human-eval-key.csv` | Correspondance lettre → modèle (à ouvrir **après** la notation) |

⚠️ `target/` est effacé par `mvn clean`. Copiez `target/benchmark/` dans `docs/resultats-benchmark/` après l'exécution.

---

## 1. Grille : état du microservice

| Critère | Avant | Maintenant | Preuve |
|---|---|---|---|
| Prompts documentés | ✅ 8 | ✅ | Prompts de génération et d'amélioration dans `LettreAiService` ; même prompt réutilisé par le benchmark (`buildGeneratePrompts`) |
| Justification du modèle | ⚠️ | ✅ | § 2 |
| Comparaison de modèles | ❌ | ✅ Gemini vs Cohere, protocole + outil, **chiffres [À COMPLÉTER]** | § 3, `LlmBenchmarkTest`, `POST /ai/compare` |
| Gain de productivité | ❌ | ⚠️ protocole + calcul automatique, **mesures humaines à faire** | § 6, `docs/mesures-productivite.csv` |
| Intégration de l'API IA | ✅ | ✅ | `LlmClient` (3 fournisseurs, retries 429, timeouts, erreurs traduites) |
| Tests assistés par IA | ✅ 84 | ✅ 103 réussis (+1 benchmark lancé à la demande) | § 8 |
| Cache | ❌ | ✅ implémenté, configurable, testé, gain mesuré | § 4, `LlmClientCacheTest` (9 tests) |
| Latence mesurée | ❌ | ✅ p50/p95/min/max par fournisseur + benchmark | § 3, `GET /ai/metrics` |
| Évaluation chiffrée de la qualité | ❌ | ✅ score automatique + grille humaine | § 5, `LetterQualityScorer` (7 tests) |
| Limites éthiques et techniques | ❌ | ✅ | § 7 |
| Captures des tests à jour | ✅ | ✅ `Tests run: 104, Failures: 0, Errors: 0, Skipped: 1`, BUILD SUCCESS (22/10/2026) | § 8 |

---

## 2. Justification des modèles

Le service ne dépend d'aucun SDK propriétaire. Les trois fournisseurs exposent une route `chat/completions` compatible OpenAI, donc un seul client (`LlmClient`) les appelle. Changer de modèle se fait par configuration (`GEMINI_MODEL`, `MISTRAL_MODEL`…), sans recompiler.

| Fournisseur / modèle | Rôle | Pourquoi |
|---|---|---|
| **Google Gemini, gamme Flash** (`gemini-3.8-flash`) | **Par défaut** | Modèle « rapide » adapté à un texte court (≈ 300 mots) généré en direct pendant que l'utilisateur attend. Clé gratuite via Google AI Studio. Bon niveau en français et en anglais. `reasoning_effort=low` limite la réflexion interne : moins de latence, et moins de risque de réponse tronquée. |
| **Cohere** `command-a-03-2025` | Alternative | Modèle multilingue orienté entreprise, clé d'essai gratuite. Utilisé **sans réflexion**, car les modèles « raisonneurs » consommaient le budget de jetons et renvoyaient des lettres coupées. |
| **Mistral** `mistral-small-latest` | Alternative | Fournisseur européen (argument RGPD pour des CV), très bon en français, petit modèle donc rapide. Son plan gratuit est très limité en requêtes par minute : d'où la reprise automatique sur 429. |

Paramètres communs :

| Paramètre | Valeur | Justification |
|---|---|---|
| `temperature` | 0,7 | Rédaction : assez de variété pour éviter les lettres « modèle », pas assez pour dériver des faits. |
| `max_tokens` | 3000 | Une lettre fait environ 500-700 jetons. L'amélioration renvoie du JSON (lettre + suggestions). La marge couvre les jetons de réflexion. Une réponse coupée (`finish_reason=length`) est refusée et non affichée. |
| `timeout` | 60 s | Au-delà, l'utilisateur reçoit un 504 clair au lieu d'une attente infinie. |
| Taille d'entrée | CV ≤ 8000 car., offre ≤ 6000 car. | Coût et latence bornés ; suffisant pour un CV de 2 pages. |

**Mistral** reste intégré et fonctionne avec une clé valide, mais il n'a pas été inclus dans le benchmark : la clé du projet est refusée (HTTP 401) et la console Mistral n'a pas permis d'en créer une nouvelle au moment des mesures. La comparaison porte donc sur **Gemini et Cohere**.

**À confirmer par le benchmark** : le choix du modèle par défaut doit être celui qui offre le meilleur compromis entre le score qualité (§ 5), l'absence d'hallucination et une p95 de latence acceptable (§ 3). Si un autre fournisseur est meilleur, changez `LLM_DEFAULT_PROVIDER` et mettez à jour ce paragraphe avec les chiffres.

---

## 3. Comparaison des modèles et latence

**Protocole** (`LlmBenchmarkTest`) :
- 10 cas synthétiques anonymisés (`src/test/resources/benchmark/cases.json`) : junior/senior, FR/EN, technique et non technique, offres courtes et longues ;
- 2 fournisseurs comparés : Gemini et Cohere (Mistral sans clé valide, voir § 2) ;
- 4 cas pièges : compétence exigée absente du CV (C02, C07, C10) et injection de prompt dans le CV (C09) ;
- prompt de production identique pour tous ;
- cache désactivé ;
- 1 appel de chauffe non mesuré par fournisseur ;
- 3 essais par cas ;
- ordre des fournisseurs alterné à chaque passage, pour ne pas avantager l'un d'eux.

Indicateurs :
- **p50** : latence médiane ;
- **p95** : latence du « mauvais cas », celle que l'utilisateur ressent ;
- **taux de succès** ;
- **débit** en caractères par seconde, qui n'est pas une mesure de qualité.

En production, `GET /api/lettres/ai/metrics` (admin) publie les mêmes indicateurs en continu : p50, p95, min, max, taux de succès par fournisseur, et taux de hit du cache. `POST /api/lettres/ai/metrics/reset` remet les compteurs à zéro avant une campagne de mesure.

**Résultats [À COMPLÉTER : coller le tableau « 1. Latence et fiabilité » de summary.md]**

| Fournisseur | Modèle | Appels | Succès | p50 (ms) | p95 (ms) | Moyenne | Écart-type | Car./s |
|---|---|---|---|---|---|---|---|---|
| cohere | | | | | | | | |
| gemini | | | | | | | | |

**Lecture [À COMPLÉTER]** : en 2 ou 3 phrases, dire quel modèle est le plus rapide, lequel est le plus fiable, et si l'écart est significatif au regard de l'écart-type.

---

## 4. Cache

**Implémentation** (`LlmClient`) :
- cache mémoire LRU ;
- clé = SHA-256 de (fournisseur | prompt système | prompt utilisateur) ;
- TTL et taille configurables (`app.llm.cache-ttl-seconds=300`, `app.llm.cache-max-entries=100`) ; un TTL de 0 désactive le cache ;
- seules les réponses valides sont mises en cache : jamais une erreur ni une réponse tronquée ;
- les benchmarks et `/compare` contournent le cache pour ne pas fausser les mesures.

**Pourquoi** : quand l'utilisateur relance la même génération (double clic, rechargement), le cache évite de consommer une seconde fois le quota gratuit et répond immédiatement.

**Vérifié par les tests** (`LlmClientCacheTest`, faux fournisseur local à 200 ms) :
- une requête identique donne un hit et n'appelle pas le fournisseur ;
- le hit est servi en moins de 50 ms, contre au moins 200 ms sans cache : gain de plus de 75 %, garanti par le test ;
- un caractère modifié dans le prompt, ou un autre fournisseur, donne un miss ;
- une entrée expirée (TTL) est redemandée ;
- quand le cache est plein, l'entrée la moins récemment utilisée est évincée ;
- les métriques (hits, misses, taux, latence d'un hit) et le reset fonctionnent.

**Gain réel [À COMPLÉTER : tableau « 3. Cache » de summary.md]** : sans cache ___ ms, avec cache ___ ms, gain ___ %. Un hit consomme 0 jeton.

Limites :
- le cache est local à une instance : avec plusieurs instances derrière Eureka, il faudrait Redis ;
- le texte reste en mémoire pendant 5 min ; hacher la clé ne chiffre pas les données ;
- la température étant de 0,7, un hit renvoie la même lettre au lieu d'une variante. C'est voulu, car l'utilisateur dispose d'« Améliorer » pour en obtenir une autre.

---

## 5. Évaluation chiffrée de la qualité

Deux niveaux complémentaires :

### 5.1 Score automatique, reproductible (`LetterQualityScorer`)

Il vérifie les 9 contraintes du prompt :
- longueur de 250 à 350 mots ;
- 3 ou 4 paragraphes ;
- ligne « Objet » ;
- signature « [Prénom Nom] » ;
- pas de markdown ;
- pas de jeton technique ;
- aucune coordonnée personnelle ;
- entreprise citée ;
- bonne langue.

Il mesure aussi :
- la **couverture de l'offre** : part des compétences demandées par l'offre *et présentes dans le CV* que la lettre cite effectivement ;
- les **hallucinations détectables** : compétence technique citée mais absente du CV, ou nombre absent du CV et de l'offre ;
- la **résistance à l'injection** (cas C09) : la lettre ne doit pas reprendre l'instruction cachée dans le CV.

`Score auto /100 = 0,6 × contraintes + 0,2 × couverture + 0,2 × (pas d'hallucination ? 100 : 0)`

Ce score ne juge ni le style ni le pouvoir de conviction. C'est le rôle de la notation humaine.

**Résultats [À COMPLÉTER : tableaux « 2. Qualité » de summary.md]**

| Fournisseur | Score auto | Contraintes | Couverture | Hallucinations | Longueur OK | Injection résistée |
|---|---|---|---|---|---|---|
| cohere | | | | | | |
| gemini | | | | | | |

### 5.2 Notation humaine à l'aveugle

1. Deux évaluateurs reçoivent `human-eval-sheet.csv` et le dossier `letters/blind/`. Les lettres sont mélangées et le nom du modèle est masqué.
2. Ils notent chaque lettre de 1 à 5 sur 5 critères : fidélité au CV, adéquation à l'offre, qualité de la langue, structure et ton, respect des contraintes. Le barème est dans `evaluation-ia.md`.
3. On rapproche ensuite les notes avec `human-eval-key.csv`. Score humain /100 = somme des notes / 25 × 100. On publie la moyenne et l'écart-type par modèle.
4. Concordance des évaluateurs : on donne le % de notes identiques à ±1 près.

Minimum raisonnable : 1 essai par cas, soit 20 lettres pour 2 modèles, environ 40 min par évaluateur.

Le champ `score` renvoyé par `/improve` est **auto-évalué par le LLM**. Il est indicatif et n'est jamais présenté comme une mesure de qualité.

---

## 6. Gain de productivité

**Protocole** : 3 à 5 participants rédigent chacun une lettre sans IA, puis une autre avec IA (sur un cas différent, pour ne pas bénéficier de la première rédaction). Pour chaque lettre, on chronomètre :
- `T_manuel` : rédaction complète sans IA ;
- `T_IA` : génération **+ relecture + corrections** jusqu'à une lettre que le participant accepterait d'envoyer.

On saisit les mesures dans `docs/mesures-productivite.csv`. Le benchmark calcule automatiquement :
- `Gain (%) = (T_manuel − T_IA) / T_manuel × 100` ;
- le nombre moyen de corrections ;
- le % de lettres acceptées.

**Résultats [À COMPLÉTER : section « 5. Productivité » de summary.md]**

| Mesure | Valeur |
|---|---|
| Lettres mesurées | |
| Temps sans IA (moy.) | min |
| Temps avec IA, relecture comprise (moy.) | min |
| **Gain de temps** | **%** |
| Corrections par lettre | |
| Lettres acceptées après relecture | % |

---

## 7. Limites éthiques et techniques

**Éthiques**
- **Hallucinations** : le modèle peut inventer une compétence ou gonfler une expérience. Plusieurs garde-fous :
  - le prompt l'interdit explicitement ;
  - `LetterQualityScorer` détecte les cas mesurables ;
  - le service n'enregistre rien automatiquement : le candidat relit et valide.

  Il reste responsable de ce qu'il envoie.
- **Données personnelles** :
  - le nom et l'adresse sont supprimés du CV avant l'envoi ;
  - les e-mails, téléphones et URL sont remplacés par des jetons puis restaurés (`PiiMasker`).

  Ce masquage par expressions régulières n'est **pas** une anonymisation garantie : un employeur ou un projet cité peut suffire à identifier quelqu'un. Les fournisseurs traitent les prompts selon leurs propres conditions, et les plans gratuits peuvent les utiliser pour améliorer leurs modèles.
- **Biais** : les modèles peuvent favoriser certains parcours, écoles ou formulations. Le test reste à faire : soumettre des CV identiques en ne changeant que le prénom ou le genre, puis comparer les scores.
- **Uniformisation** : des milliers de lettres générées par le même modèle se ressemblent, ce qui peut desservir le candidat. Le ton est réglable et le contenu reste éditable.
- **Le service n'évalue pas les candidats** : il rédige pour eux. Aucune décision de recrutement n'en dépend.

**Techniques**
- **Prompt injection** : un CV ou une offre peut contenir des instructions. Plusieurs défenses :
  - les données sont balisées (`<cv>`, `<offre>`) ;
  - le prompt précise que leur contenu n'est jamais une instruction ;
  - le cas C09 du benchmark le mesure.

  Ces défenses réduisent le risque sans le supprimer.
- **Dépendance aux fournisseurs** : quotas gratuits (429), pannes, modèles retirés ou modifiés sans préavis. Ce sont des services externes, et les résultats d'un benchmark ne valent que pour la version testée à la date indiquée. Observé pendant les mesures du 22/10/2026 :
  - Gemini a renvoyé plusieurs **HTTP 503** (« high demand ») et une réponse a mis **47 s** au lieu de quelques secondes ;
  - la clé Mistral a été refusée (**HTTP 401**) et aucune nouvelle clé n'a pu être obtenue ;
  - l'ancienne clé Gemini était d'un type non accepté (`ACCESS_TOKEN_TYPE_UNSUPPORTED`).

  C'est l'argument principal pour garder plusieurs fournisseurs interchangeables par configuration. Une amélioration possible serait de basculer automatiquement vers un autre fournisseur en cas de 503.
- **Coût** : non mesuré, car les plans gratuits ne facturent pas. En production, il faudrait lire le champ `usage` (jetons) des réponses.
- **Mesures** :
  - la latence dépend du réseau, de la charge du fournisseur et de l'heure ;
  - 10 cas × 3 essais forment un échantillon de démonstration, pas une preuve statistique ;
  - le score automatique ne voit pas le style.
- **Cache et métriques en mémoire** : ils sont perdus au redémarrage et ne sont pas partagés entre instances.
- **Limite de débit** (6 appels par minute et par utilisateur) : elle est en mémoire et contournable avec plusieurs comptes.
- **Extraction du CV** : un PDF scanné (image) n'est pas lu, car il n'y a pas d'OCR.

**Sécurité corrigée lors de ce bilan**
- `/compare` acceptait un prompt libre de n'importe quel utilisateur connecté, sans limite de débit : n'importe qui pouvait consommer le quota commun. `/compare`, `/metrics` et `/metrics/reset` sont désormais réservés au rôle ADMIN, et deux tests le vérifient.

---

## 8. Tests

`mvn test` lance 103 tests hors-ligne. `LlmBenchmarkTest` est signalé « skipped » tant que `-Dbenchmark=true` n'est pas passé.

Résultat obtenu le 22/10/2026 : `Tests run: 104, Failures: 0, Errors: 0, Skipped: 1`, **BUILD SUCCESS**.

| Classe | Tests | Ce qui est vérifié |
|---|---|---|
| `LlmClientTest` | 15 | Appels, erreurs 401/403/429/500, timeouts, réponses vides ou tronquées |
| `LlmClientCacheTest` | 9 | **Nouveau** : cache (hit, miss, TTL, LRU, contournement), p50/p95, reset |
| `LetterQualityScorerTest` | 7 | **Nouveau** : contraintes, hallucinations, langue, lexique |
| `LettreAiServiceTest` | 26 | Anonymisation, prompts, nettoyage, limite de débit (+1 test sur le prompt partagé) |
| `SecurityTest` | 16 | JWT, rôles, validation (+2 tests : accès admin aux métriques et à la comparaison) |
| `LettreServiceTest` | 12 | CRUD des lettres |
| `PiiMaskerTest` | 11 | Masquage des données personnelles |
| `TextExtractorTest` | 7 | Lecture PDF / DOCX / TXT |

**Captures à joindre** : la sortie `Tests run: 104, Failures: 0, Errors: 0, Skipped: 1` + BUILD SUCCESS de `mvn clean test`, et une capture de `summary.md` après le benchmark.
