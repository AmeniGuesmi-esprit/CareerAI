# skill-gap-service

Microservice **d'analyse CV / offre d'emploi par LLM** - CareerAI.
Le candidat importe son CV et colle la description d'une offre : le LLM renvoie un score d'adequation,
les **competences a ameliorer** (priorite, raison, action concrete), les points forts et des conseils pour le CV.

Port **8084** - enregistre dans Eureka - base MySQL `careerai_skillgap` (creee automatiquement).

## Lancer
1. MySQL (XAMPP), puis `eureka-server` (8761), `user-service` (8081), `api-gateway` (8222).
2. Variable d'environnement `LLM_API_KEY` (cle Groq).
3. `mvn spring-boot:run` ou Run `SkillGapServiceApplication` dans IntelliJ.

Variables optionnelles : `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (identique a user-service et gateway),
`LLM_API_URL`, `LLM_MODEL`, `LLM_REASONING_EFFORT` (low par defaut), `CV_STORAGE_DIR`.

## Entites
| Entite | Table | Role |
|---|---|---|
| `CvUpload` | `cv_uploads` | CV importe (fichier sur disque + texte extrait) + `userId` |
| `SkillGapAnalysis` | `skill_gap_analyses` | Offre saisie + resultat JSON du LLM + score + modele + `userId` |

## API (via la gateway : `http://localhost:8222`)
| Methode | URL | Description |
|---|---|---|
| POST | `/api/skill-gap/cvs` | Upload d'un CV (`multipart`, champ `file`) |
| GET | `/api/skill-gap/cvs` | Mes CV |
| DELETE | `/api/skill-gap/cvs/{id}` | Supprimer un CV (et ses analyses) |
| POST | `/api/skill-gap/analyses` | `{"cvId":1,"jobTitle":"...","jobDescription":"..."}` (50 a 6000 caracteres) |
| GET | `/api/skill-gap/analyses` | Mon historique |
| GET | `/api/skill-gap/analyses/{id}` | Une analyse |
| DELETE | `/api/skill-gap/analyses/{id}` | Supprimer une analyse |

## Limites Groq (tier on_demand : 8000 jetons / minute)
`llm.max-tokens` est compte dans la limite TPM : il est a 2500 (au lieu de 8192) et CV / offre sont plafonnes
(`analysis.max-cv-chars=9000`, offre 6000) pour rester sous 8000 par requete. Deux analyses a moins d'une minute
d'intervalle peuvent encore renvoyer 429.


## RAG (base de connaissances `src/main/resources/knowledge/*.json`)
Les fiches de competences les plus proches de l'offre sont ajoutees au prompt (balise `<contexte>`) pour que
"howToImprove" cite de vraies ressources. Environ 135 fiches, un fichier par domaine : informatique, marketing et
commerce, finance et comptabilite, ressources humaines, gestion de projet et qualite, ingenierie et industrie,
logistique, sante, juridique, design et communication, data et bureautique, competences transversales, langues.

- **Ajouter un domaine** : creer `knowledge/<domaine>.json` (champs `slug` unique, `skill`, `category`, `content` de
  320 caracteres max). Il est charge au prochain demarrage ; `KnowledgeBaseTest` verifie le format.
- **Recherche** : semantique (embeddings Hugging Face, `HF_API_KEY`) ou, sans cle, lexicale locale (TF-IDF). Les
  rubriques de presentation de l'offre (avantages, processus de recrutement, remuneration...) sont ignorees, puis
  l'offre est decoupee en blocs et chaque fiche garde son meilleur score
  (`rag.chunk-chars`, `rag.lexical-chunk-chars`, seuils `rag.min-score` / `rag.lexical-min-score`).
- **Ne citer que des ressources reelles** dans les fiches : le prompt interdit au LLM d'en inventer d'autres.

## Tests
`mvn test` (77 tests, sans MySQL ni appel reseau) :
- RAG : recherche sur la vraie base de connaissances (offres de plusieurs domaines), format des fiches ;
- donnees personnelles : masquage du CV et de l'offre, et verification que le LLM ne recoit que le texte masque ;
- LLM : faux serveur Groq local (cle absente ou invalide, quota, timeout, serveur injoignable, reponse tronquee) ;
- validation de la sortie du LLM, import des CV (PDF, DOCX, TXT), securite JWT (l'utilisateur vient du jeton).
