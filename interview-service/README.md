# interview-service — Préparation d'entretien

Service Spring Boot (Java 17, Spring Boot 3.2.0, Spring Cloud 2023.0.0) qui :

- gère une bibliothèque de CV anonymisés (PDF) et des **sessions de préparation** (CRUD, réponses modifiables, suppression) ;
- génère des **questions d'entretien** et un **feedback** par réponse via un **RAG** sur une base de connaissances versionnée (`knowledge/`) ;
- utilise **Ollama en local** : `qwen3.5:4b` pour la génération, `bge-m3` pour les embeddings.

Port `8083`, accessible uniquement via l'API Gateway (`/api/interview/**`).

## Prérequis

| Élément | Commande / vérification |
|---|---|
| JDK 17 | `java -version` (le projet cible Java 17) |
| Maven 3.9+ | `mvn -v` |
| MySQL | service `mysql` de Docker Compose, ou XAMPP sur `localhost:3306` |
| Ollama | `ollama --version` |
| Modèles | `.\scripts\ollama-pull.ps1` (télécharge `qwen3.5:4b` et `bge-m3`) |

Ollama doit être joignable depuis le service :

- lancé avec `mvn` sur la machine : `http://localhost:11434` (défaut) ;
- lancé dans Docker Compose : `OLLAMA_BASE_URL=http://host.docker.internal:11434` (défaut du `docker-compose.yml`). Sous Windows, Ollama écoute sur `127.0.0.1` par défaut : définissez `OLLAMA_HOST=0.0.0.0:11434` pour que les conteneurs l'atteignent, puis redémarrez Ollama. Cela expose Ollama sur le réseau local : pare-feu recommandé. Alternative : `docker compose --profile ollama-docker up -d` lance Ollama dans un conteneur.

## Variables d'environnement

Voir `.env.example` à la racine. Aucune valeur secrète n'a de valeur par défaut dans la configuration : une variable manquante bloque le démarrage.

| Variable | Rôle | Défaut dans `application.yml` |
|---|---|---|
| `INTERVIEW_DB_URL` | JDBC MySQL de `interview_db` | `localhost:3306/interview_db` |
| `DB_USER` / `DB_PASSWORD` | identifiants MySQL | `root` / vide |
| `EUREKA_USER` / `EUREKA_PASSWORD` | identifiants Eureka | `admin` / `admin123` |
| `OLLAMA_BASE_URL` | point d'accès Ollama | `http://localhost:11434` |
| `OLLAMA_CHAT_MODEL` / `OLLAMA_EMBED_MODEL` | modèles | `qwen3.5:4b` / `bge-m3` |
| `RAG_KNOWLEDGE_PATH` | dossier de la base de connaissances | `../knowledge` |

Spring ne lit pas `.env` seul : pour un lancement local, chargez-le avec `. .\scripts\dev-env.ps1`.

## Lancement

Avec Docker Compose (depuis la racine) :

```powershell
Copy-Item .env.example .env   # puis remplir les valeurs
docker compose up -d --build
```

En local :

```powershell
. .\scripts\dev-env.ps1
cd interview-service
mvn spring-boot:run
```

La migration Flyway `V1__init.sql` crée le schéma au premier démarrage.

## Base de connaissances (RAG)

Le dossier `knowledge/` contient les ressources versionnées :

| Fichier | Type | Piste |
|---|---|---|
| `tracks/backend-java-spring.md` | question_bank (43 items) | backend |
| `tracks/frontend-angular.md` | question_bank (19 items) | frontend |
| `comportemental/questions-comportementales.md` | question_bank (24 items) | générique |
| `methodologie/methode-star.md` | guide | générique |
| `methodologie/grille-evaluation.md` | rubric | générique |

Format : front matter (`id`, `title`, `track`, `doc_type`, `lang: fr`, `version`), puis un `###` par item avec les champs fixes `Catégorie`, `Niveau` (junior, confirme, senior), `Tags`, `Intention`, `Points attendus`, `Réponse modèle (plan)`, `Pièges`. Un fichier mal formé (niveau inconnu, champ manquant, titre en double) fait échouer l'indexation avec un message précis.

Indexer (ou réindexer après modification) : ADMIN requis.

```powershell
.\scripts\rag-reindex.ps1 -Email <admin> -Password '<mot de passe>'
```

Le réindexage est incrémental : un chunk inchangé (même hash et même modèle d'embedding) n'est pas recalculé. Si Ollama est indisponible, l'index passe en mode **BM25 seul** et le réindexage est à relancer.

Pipeline : parsing → chunking (un item = un chunk, découpe par puces avec chevauchement au-delà de 1800 caractères) → embeddings bge-m3 (stockés en BLOB dans `rag_chunks`) → index en mémoire : vecteurs (cosinus) et Lucene BM25 (analyseur français). Recherche : requêtes déterministes (piste, niveau, compétences communes et lacunes), BM25 + dense fusionnés par RRF (k=60), filtres (piste ou `generic`, type de document, niveau), diversification MMR (λ = 0.7).

### Évaluation

```powershell
cd interview-service
mvn -Dtest=RagEvaluationTest test
```

Le test indexe la base sans embeddings (BM25) sur `src/test/resources/rag-eval/gold.yml` et écrit `target/rag-eval-report.md` (Recall@5, MRR@10, détail par requête). Les requêtes du jeu de test ont été rédigées à partir des titres de la base : il mesure la cohérence du pipeline, pas la généralisation. Pour une mesure hybride, réindexez avec Ollama actif et étendez le jeu de test.

## API

Toutes les routes exigent les en-têtes injectés par la passerelle (`X-User-Id`) ; les routes admin exigent aussi `X-User-Role: ADMIN`.

| Méthode | Route | Rôle |
|---|---|---|
| POST / GET | `/api/interview/cvs` | upload PDF (anonymisé) / liste paginée |
| GET / DELETE | `/api/interview/cvs/{id}` | détail / suppression |
| POST | `/api/interview/sessions` | crée une session (202, statut `GENERATING`) |
| GET | `/api/interview/sessions?page&size` | liste paginée |
| GET / PATCH / DELETE | `/api/interview/sessions/{id}` | détail / intitulé / suppression |
| POST | `/api/interview/sessions/{id}/retry` | relance une génération en échec |
| PUT / DELETE | `/api/interview/sessions/{id}/questions/{qid}/answer` | crée ou modifie / supprime une réponse (feedback recalculé) |
| POST | `/api/interview/admin/rag/reindex` | réindexation |
| GET | `/api/interview/admin/rag/status` | mode, modèles, santé d'Ollama, manifeste |
| POST | `/api/interview/admin/rag/search` | recherche de debug avec rangs par méthode |

Les générations tournent en arrière-plan sur un exécuteur mono-thread (un seul appel GPU à la fois). Le client interroge la session (ou la réponse) jusqu'à `READY` / `FAILED`. Au démarrage, les générations restées en cours sont marquées `FAILED` (`INTERRUPTED`).

## Erreurs

Format identique aux autres services : `{ timestamp, status, code, message }`. Codes IA : `AI_UNAVAILABLE` (503, Ollama injoignable), `AI_TIMEOUT` (504), `AI_MODEL_MISSING` (503, lancer `ollama pull`), `AI_INVALID_RESPONSE` (502, après une relance), `KB_INVALID` (422, base de connaissances invalide).

## Tests

```powershell
cd interview-service
mvn test
```

Couvre : validation de la base de connaissances et chunking, planificateur de requêtes, client Ollama (serveur HTTP factice : succès, modèle absent, délai dépassé, injoignable), extraction PDF et anonymisation, garde-fous des prompts, évaluation du retrieval.

## Dépannage

- **`AI_MODEL_MISSING`** : `ollama pull qwen3.5:4b` et `ollama pull bge-m3`.
- **Session bloquée en `GENERATING`** : vérifiez `ollama list` et les logs ; au redémarrage, elle passe en `FAILED` puis `POST .../retry`.
- **Réindexation en BM25 seul** : Ollama était injoignable ; relancez `rag-reindex.ps1`.
- **Ollama injoignable depuis Docker** : `OLLAMA_HOST=0.0.0.0:11434` (voir Prérequis).
- **`FAILED` avec `KB_INVALID`** : le message indique le fichier et l'item en faute.
