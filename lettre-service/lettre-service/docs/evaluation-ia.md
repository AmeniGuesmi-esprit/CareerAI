# Évaluation chiffrée du microservice IA

## Ce qui existe déjà dans le code

- `POST /api/lettres/ai/compare` (ADMIN) : compare 2 à 4 fournisseurs avec le même prompt et désactive le cache pour ces essais.
- `GET /api/lettres/ai/metrics` (ADMIN) : succès/échecs, hits/misses du cache, taux de hit, latence d'un hit, et par fournisseur latence moyenne, p50, p95, min, max et taux de succès.
- `POST /api/lettres/ai/metrics/reset` (ADMIN) : remet les compteurs à zéro et vide le cache avant une campagne de mesure.
- Cache mémoire LRU : TTL 5 minutes, maximum 100 entrées (configurable : `app.llm.cache-ttl-seconds`, `app.llm.cache-max-entries`).
- Benchmark automatisé `LlmBenchmarkTest` + score qualité automatique `LetterQualityScorer` : voir `RAPPORT-IA-lettre.md`.
- Protection : masquage de certaines données personnelles, limite de 6 générations/améliorations par minute et utilisateur.

Les métriques sont locales au processus et se réinitialisent au redémarrage. La latence de comparaison inclut l'appel réseau fournisseur, mais pas un benchmark statistique complet.

## 1. Comparaison de modèles reproductible

1. Choisir 10 à 20 cas de test représentatifs et anonymisés : profils junior/senior, offre courte/longue, CV français/anglais.
2. Utiliser exactement le même `systemPrompt` et `userPrompt` pour tous les fournisseurs.
3. Faire 1 appel de chauffe non mesuré, puis plusieurs essais mesurés par modèle. `LlmBenchmarkTest` automatise tout (voir `RAPPORT-IA-lettre.md`, § 0).
4. Noter pour chaque essai : fournisseur, modèle exact, date, latence, succès/échec, caractères produits et coût réel si disponible.
5. Comparer la médiane et le 95e percentile de latence, le taux d'erreur, puis la qualité humaine. Ne conclure pas sur un seul essai.

### Mesures à calculer

- Latence médiane (ms) : médiane des temps d'appel.
- P95 (ms) : valeur sous laquelle se trouvent 95 % des appels.
- Taux de succès (%) = appels réussis / appels totaux × 100.
- Gain de latence (%) = (latence référence − latence candidate) / latence référence × 100.
- Débit de sortie (caractères/s) = caractères produits / secondes écoulées. Ce n'est qu'un indicateur de débit, pas une mesure de qualité.
- Coût par lettre : coût total des appels / nombre de lettres réussies, si le fournisseur expose les coûts ou tokens.

## 2. Évaluation chiffrée de la qualité

Créer un jeu fixe de 10 à 20 cas et faire noter chaque sortie par deux évaluateurs, sans leur montrer le nom du modèle. Utiliser une note de 1 à 5 par critère :

| Critère | 1 | 3 | 5 |
|---|---|---|---|
| Fidélité au CV | Inventions majeures | Quelques écarts | Aucun fait inventé |
| Adéquation à l'offre | Générique | Partiellement ciblée | Liens concrets et pertinents |
| Qualité du français/anglais | Nombreuses erreurs | Compréhensible | Correct et naturel |
| Structure et ton | Inadaptés | Acceptables | Professionnels et cohérents |
| Respect des contraintes | Plusieurs violations | Une violation mineure | Toutes respectées |

Score qualité /100 = somme des notes / (nombre de critères × 5) × 100.
Publier la moyenne et l'écart-type par modèle. Mesurer séparément le taux d'hallucination : sorties contenant au moins un fait non étayé / sorties évaluées × 100. Les évaluateurs doivent justifier toute note basse avec un exemple.

Ne pas présenter le score auto-déclaré par un modèle comme une mesure objective. Le champ `score` renvoyé par l'amélioration est généré par le LLM lui-même et doit être indiqué comme indicatif, non comme preuve de qualité.

## 3. Gain de productivité

Pour les mêmes cas, faire mesurer :
- `Tmanuel` : temps pour rédiger une lettre sans IA ;
- `TIA` : temps total avec IA, y compris relecture/correction ;
- `Gain (%) = (Tmanuel − TIA) / Tmanuel × 100`.

Calculer aussi le nombre moyen de corrections nécessaires et le pourcentage de lettres acceptées après relecture. Faire le test avec plusieurs utilisateurs et les mêmes consignes. Ne compter pas seulement le temps de génération : la relecture fait partie du temps IA.

## 4. Évaluer le cache

1. Appeler deux fois la même requête de génération avec les mêmes paramètres.
2. Lire `GET /api/lettres/ai/metrics` avant/après et vérifier que le second appel augmente `cacheHits`.
3. Modifier un seul caractère du prompt : cela doit produire un miss.
4. Attendre plus de 5 minutes ou redémarrer le service et retester.
5. Vérifier que la sortie en cache est identique et que la latence du second appel baisse. Rapporter le taux de hit et le gain de latence observé.

Attention : le cache est en mémoire, partagé par le processus, et conserve temporairement les sorties. Ne pas envoyer de vrais CV dans les tests de démonstration. Pour un déploiement multi-instance, ce cache et ses métriques ne sont pas partagés entre instances.

## 5. Limites éthiques et techniques à documenter

- Biais : les modèles peuvent favoriser certains parcours, noms, genres ou formulations. Tester des CV synthétiques équivalents avec des attributs modifiés et comparer les notes.
- Confidentialité : le masquage par expressions régulières n'est pas une anonymisation garantie ; les employeurs, écoles ou projets peuvent identifier une personne. Les fournisseurs peuvent traiter les prompts selon leurs propres conditions.
- Hallucinations : le modèle peut inventer des compétences ou exagérer l'expérience. Exiger la vérification humaine avant envoi.
- Cache : la clé est hachée mais le résultat et les données de requête restent en mémoire pendant le TTL. Le hachage de clé ne chiffre pas les données en cache.
- Dépendance fournisseur : limites de débit, disponibilité, changements de modèles et coûts variables.
- Mesure : latence dépend du réseau, de la charge et de la longueur du prompt ; un petit échantillon ne permet pas une conclusion générale.
- Sécurité : les CV/offres peuvent contenir des instructions malveillantes (prompt injection). Le balisage et le prompt réduisent le risque sans le supprimer.
- Accessibilité et équité : le résultat doit rester modifiable par le candidat et ne doit pas décider de son recrutement.

## 6. Commandes de vérification

Depuis la racine du projet (le dossier contenant `pom.xml`) :

```bash
mvn test
```

Avec l'application démarrée et un jeton JWT valide (rôle ADMIN pour `/metrics` et `/compare`) :
- `GET http://localhost:8095/api/lettres/ai/providers`
- `GET http://localhost:8095/api/lettres/ai/metrics`
- `POST http://localhost:8095/api/lettres/ai/compare`

Exemple de corps JSON pour `/compare` :

```json
{
  "providers": ["gemini", "mistral"],
  "systemPrompt": "Rédige une lettre de motivation professionnelle sans inventer de faits.",
  "userPrompt": "CV anonymisé : développeur Java, 2 ans d'expérience. Offre : développeur Java Spring."
}
```

Les fournisseurs doivent être configurés et les appels réels peuvent consommer quota/crédits.
