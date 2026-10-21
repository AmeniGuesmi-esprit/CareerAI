# Intégration IA — competence-service (Yesmine)

Module : conseiller d'orientation (`POST /api/orientation/advice`). Chaîne en deux étapes : un **conseiller** (Gemini, prompt système + few-shot) puis un **juge** (Gemini, sortie JSON forcée par schéma) qui relit la réponse.

> Les cases **« À COMPLÉTER »** sont des mesures que seul ton poste peut produire (clé Gemini réelle, ton temps réel). Ne mets jamais un chiffre inventé : la grille demande des résultats mesurés.

---

## 1. Comparaison de modèles

### Méthode
- Même jeu de test pour tous les modèles : **15 cas** (`EvaluationDataset`) répartis en 7 catégories : conseil, format imposé, piège « promesse de salaire », hors-sujet, injection de prompt, certification inventée (hallucination), question en anglais.
- Même prompt système, même température (0,4), même juge (le modèle par défaut) : **seul le modèle du conseiller change**.
- Deux mesures complémentaires :
  1. **Règles automatiques** (`RuleChecker`, sans IA) : longueur, nombre de points, absence de promesse, refus des hors-sujet, non-fuite du prompt système, doute exprimé sur un sujet inventé, langue.
  2. **Note du juge** /5 = moyenne de 4 critères (pertinence, fiabilité, format, prudence).
- Latence : durée de l'appel conseiller (moyenne, p50, p95).

### Lancer la mesure
```bash
# PowerShell : $env:GEMINI_API_KEY="ta_cle"
mvn test -Dtest=ModelBenchmarkTest -Dbenchmark=true
# modèles personnalisés (ceux disponibles dans ton Google AI Studio) :
mvn test -Dtest=ModelBenchmarkTest -Dbenchmark=true -Dbenchmark.models=modeleA,modeleB,modeleC
```
Sortie : `docs/evaluation/rapport-modeles.md` (tableaux) et `rapport-modeles.csv`. Colle le tableau de synthèse ci-dessous.

### Résultats (mesurés le 21/10/2026, 15 cas, un passage par modèle)
| Modèle | Conformité aux règles | Note du juge (/5) | Réponses « vérifiées » | Latence moy. | p50 | p95 | Erreurs |
|---|---|---|---|---|---|---|---|
| gemini-3.1-flash-lite | 100,0 % (15/15) | 4,86 | 80,0 % | 7384 ms | 5873 ms | 14224 ms | 0 |
| gemini-3.5-flash-lite | 93,3 % (14/15) | 5,0 | 100,0 % | 1378 ms | 1333 ms | 2135 ms | 0 |
| gemini-3.8-flash | non évaluable (1/15) | 5,0 (1 seul cas) | — | 10559 ms (1 seul cas) | — | — | 14 sur 15 |

Détail complet : `docs/evaluation/rapport-modeles.md` et `.csv`.

### Lecture des résultats
- **gemini-3.8-flash : non évaluable.** 14 cas sur 15 ont échoué (erreurs 503 « forte demande » de Google et délais dépassés). Ses chiffres ne reposent que sur un cas et ne mesurent pas sa qualité, mais sa **disponibilité** : pendant le test, il était inutilisable. Il n'est donc pas retenu. À retester à un autre moment avec un délai d'attente plus long et un retry.
- **gemini-3.1-flash-lite** (modèle initial) : parfaitement conforme aux règles (15/15) mais 3 réponses sur 15 non « vérifiées » (2 jugées `A_VERIFIER` sur des cas de format, 1 sans verdict car le juge n'a pas répondu) et nettement plus lent (latence moyenne 7,4 s, p95 14,2 s).
- **gemini-3.5-flash-lite** : toutes les réponses « vérifiées », note 5,0, et environ **5 fois plus rapide** en moyenne (1,4 s contre 7,4 s ; p95 2,1 s contre 14,2 s). Une seule règle violée : sur le cas A02 (programme d'une université inventée), la réponse n'exprime pas de doute alors que le juge a donné 5/5. Ce cas montre l'intérêt des règles automatiques : elles ont repéré ce que le juge n'a pas vu.

### Justification du choix
Modèle retenu : **gemini-3.5-flash-lite**, pour quatre raisons :
1. 100 % de réponses « vérifiées » et note du juge de 5,0 (contre 80 % et 4,86).
2. Latence 5 fois plus faible, ce qui compte pour un conseiller utilisé comme un chat (p95 de 2,1 s).
3. Disponibilité : aucune erreur sur 15 cas, quota gratuit large (15 requêtes par minute, 500 par jour).
4. Même famille « lite » que le modèle d'origine : le prompt n'a pas eu besoin d'être modifié.

Contrepartie assumée : 1 cas sur 15 de non-respect de la règle « exprimer un doute sur un sujet inventé » (93,3 % de conformité contre 100 %). C'est pourquoi les réponses restent accompagnées du message `limites` et du statut de vérification.

**Réserves sur la méthode :** un seul passage de 15 cas, donc des écarts de quelques points ne sont pas significatifs ; la latence dépend de la charge de Google au moment du test (le premier modèle a été testé pendant des pics de demande) ; le juge est `gemini-3.1-flash-lite`, identique pour tous les modèles.

*Limite de la méthode :* le juge est un modèle Gemini, donc il peut favoriser le style de sa propre famille ; c'est pour cela que les règles automatiques servent de contrôle indépendant.

---

## 2. Gain de productivité

### Méthode
Pour chaque tâche, mesurer avec un chrono le temps **sans IA** (estimation honnête d'après une tâche équivalente faite à la main) et **avec IA** (génération + relecture + corrections). Compter aussi les corrections nécessaires.

| Tâche du service competence | Sans IA | Avec IA (relecture incluse) | Gain | Corrections à la main |
|---|---|---|---|---|
| Écrire les prompts système + few-shot | À COMPLÉTER | À COMPLÉTER | | |
| Générer les tests unitaires (JUnit / Mockito) | À COMPLÉTER | À COMPLÉTER | | |
| Écrire le client Gemini + gestion d'erreurs (429, 401, 404) | À COMPLÉTER | À COMPLÉTER | | |
| Écrire le jeu de test d'évaluation et les règles | À COMPLÉTER | À COMPLÉTER | | |
| Rédiger la documentation | À COMPLÉTER | À COMPLÉTER | | |

**Formule :** gain (%) = (temps sans IA − temps avec IA) / temps sans IA × 100.

### Analyse à rédiger
- Où l'IA fait gagner le plus (code répétitif, tests, documentation) et où elle fait perdre du temps (corriger une réponse trop sûre d'elle, API Gemini mal appelée).
- Ce qui doit rester humain : vérification des faits (certifications), décisions d'architecture, sécurité.

---

## 3. Latence mesurée et évaluation chiffrée de la qualité (en production)

Chaque réponse de `/api/orientation/advice` contient désormais un bloc `metriques` :

```json
"metriques": {
  "modele": "gemini-3.1-flash-lite",
  "latenceConseilMs": 1840,
  "latenceJugeMs": 920,
  "latenceTotaleMs": 2760,
  "scoreQualite": 4.5
}
```
- Latences mesurées avec `System.nanoTime()` autour de chaque appel Gemini, et écrites dans les logs (`Conseil généré : modèle=… conseil=…ms juge=…ms total=…ms score=…`).
- `scoreQualite` : moyenne /5 des notes du juge (pertinence, fiabilité, format, prudence) ; `null` si le juge est désactivé ou en panne.
- Une réponse servie par le cache garde les métriques de sa génération d'origine (le cache évite tout nouvel appel Gemini).

Évaluation hors production : voir section 1 (taux de conformité, note moyenne, taux « vérifiée », latence p50/p95).

---

## 4. Limites éthiques et techniques

### Limites techniques
| Limite | Conséquence | Mesure en place |
|---|---|---|
| **Hallucinations** : le modèle peut inventer une certification ou un organisme | Mauvaise orientation de l'étudiant | Règle « ne cite que ce qui existe, sinon dis-le » ; le juge vérifie la fiabilité ; statut `A_VERIFIER` + avertissement ; cas de test A01/A02 |
| **Le juge est aussi une IA** | Il peut laisser passer une erreur ou signaler à tort | Règles automatiques indépendantes ; le juge est un « bonus » : s'il tombe, statut `NON_VERIFIEE` |
| **Connaissances figées** | Formations et certifications peuvent avoir changé | Le prompt demande de vérifier sur le site officiel de l'éditeur |
| **Injection de prompt** | Un utilisateur peut tenter de détourner le conseiller ou d'extraire le prompt système | Nettoyage de l'entrée (caractères de contrôle, balises `<demande>/<reponse>` retirées), contenu traité comme donnée par le juge, cas de test I01 |
| **Non-déterminisme** | Deux réponses différentes pour le même prompt | Température 0,4 pour le conseiller, 0 pour le juge ; cache d'1 h |
| **Disponibilité et quotas** | Erreurs 429/503 ; latence variable | Rate limiter (5 req/min/utilisateur), timeouts (5 s connexion, 30 s lecture), messages d'erreur clairs, dégradation sans juge |
| **Dépendance à un fournisseur** (Google) | Changement de modèle, de prix ou arrêt | Modèle configurable (`app.gemini.model`), appels isolés dans `GeminiClient` |
| **Cache en mémoire** | Perdu au redémarrage, non partagé entre instances | Acceptable pour une démo ; passer à Redis en production |
| **Taille limitée** | Prompt 20–1500 caractères, réponse coupée à 4000 | Validation Bean Validation + `cut()` |

### Limites éthiques
- **Biais** : les conseils peuvent refléter les biais des données d'entraînement (genre, pays, prestige des écoles, marché occidental). Le conseiller ne connaît pas le marché tunisien en détail ; un conseil n'est jamais une décision.
- **Responsabilité** : l'outil ne remplace pas un conseiller d'orientation humain. Le message `limites` est renvoyé avec chaque réponse pour que l'interface l'affiche.
- **Promesses** : interdiction de promettre salaire ou embauche (prompt + juge + règle automatique + cas P01/P02).
- **Vie privée** : aucune donnée personnelle n'est volontairement envoyée à Gemini (le prompt ne contient que la question de l'utilisateur, le `userId` sert seulement au rate limiter). Mais l'utilisateur peut taper des informations personnelles dans son texte : elles partent chez Google. À indiquer à l'utilisateur et à ne pas stocker. Le cache n'est partagé que parce que le prompt n'est lié à aucun compte.
- **Transparence** : l'utilisateur doit savoir qu'il parle à une IA et voir le statut de vérification (`VERIFIEE`, `A_VERIFIER`, `NON_VERIFIEE`).
- **Sécurité de la clé** : clé Gemini via variable d'environnement, jamais dans le code.
- **Coût et environnement** : chaque conseil = 2 appels de modèle ; le cache, le rate limiter et le modèle « lite » limitent la consommation.
