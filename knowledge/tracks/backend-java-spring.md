---
id: backend-java-spring
title: Questions techniques Java et Spring Boot
track: backend-java-spring
doc_type: question_bank
lang: fr
version: 1
---

# Questions techniques Java et Spring Boot

## Java core

### Différence entre == et equals
- **Catégorie** : technique
- **Niveau** : junior
- **Tags** : java, equals, objets
- **Intention** : vérifier la compréhension de l'égalité de référence et de valeur.
- **Points attendus** : == compare les références pour les objets ; equals compare le contenu si la classe le redéfinit ; piège des Strings et de l'intern.
- **Réponse modèle (plan)** : définir les deux notions, donner un exemple avec String, expliquer quand redéfinir equals.
- **Pièges** : confondre égalité de valeur et d'identité ; oublier hashCode.

### HashMap, TreeMap et LinkedHashMap
- **Catégorie** : technique
- **Niveau** : junior
- **Tags** : java, collections, hashmap
- **Intention** : choisir la bonne structure de données.
- **Points attendus** : HashMap sans ordre O(1) moyen ; TreeMap trié par clé O(log n) ; LinkedHashMap conserve l'ordre d'insertion.
- **Réponse modèle (plan)** : comparer ordre, complexité et cas d'usage (cache, index trié, ordre stable).
- **Pièges** : supposer un ordre avec HashMap ; clés mutables.

### Contrat equals et hashCode
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : java, equals, hashcode
- **Intention** : évaluer la compréhension des contrats Java.
- **Points attendus** : deux objets égaux doivent avoir le même hashCode ; réflexivité, symétrie, transitivité ; champs utilisés cohérents.
- **Réponse modèle (plan)** : rappeler le contrat, citer le risque avec HashSet, proposer Objects.hash ou un record.
- **Pièges** : redéfinir equals sans hashCode ; utiliser un champ mutable.

### Immutabilité
- **Catégorie** : technique
- **Niveau** : junior
- **Tags** : java, immutabilite, record
- **Intention** : comprendre les avantages des objets immuables en contexte concurrent.
- **Points attendus** : champs final, pas de setter, copie défensive des collections ; String et record immuables.
- **Réponse modèle (plan)** : définir l'immutabilité, la mettre en pratique avec un record, expliquer le bénéfice en multi-thread.
- **Pièges** : croire qu'un champ final suffit si la collection interne reste modifiable.

### Exceptions checked et unchecked
- **Catégorie** : technique
- **Niveau** : junior
- **Tags** : java, exceptions
- **Intention** : savoir quand lever et gérer une exception.
- **Points attendus** : checked obligatoires à déclarer ; unchecked pour les erreurs de programmation ; ne pas avaler les exceptions.
- **Réponse modèle (plan)** : distinguer les deux, donner un exemple de traduction d'exception métier.
- **Pièges** : catch vide ; exceptions génériques Exception partout.

### Stream API : map et flatMap
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : java, stream, lambda
- **Intention** : maîtriser la transformation de flux.
- **Points attendus** : map transforme chaque élément ; flatMap aplatit des flux imbriqués ; évaluation paresseuse.
- **Réponse modèle (plan)** : exemple avec une liste de listes, puis expliquer l'évaluation paresseuse.
- **Pièges** : effets de bord dans map ; réutiliser un Stream déjà consommé.

### Optional : bon usage
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : java, optional
- **Intention** : repérer les usages idiomatiques et les anti-patterns.
- **Points attendus** : utiliser Optional comme type de retour ; éviter get() sans contrôle ; préférer orElseThrow.
- **Réponse modèle (plan)** : montrer map/orElseThrow, citer l'anti-pattern Optional en paramètre ou en champ.
- **Pièges** : if (opt.isPresent()) get() ; Optional pour les champs d'entité.

### Concurrence : ExecutorService et CompletableFuture
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : java, concurrence, thread
- **Intention** : évaluer la gestion des tâches asynchrones.
- **Points attendus** : pool de threads borné ; CompletableFuture pour chaîner des tâches ; gestion des exceptions avec exceptionally.
- **Réponse modèle (plan)** : comparer, préciser le dimensionnement du pool et la gestion d'erreurs.
- **Pièges** : créer un thread par requête ; bloquer dans un pool partagé.

### Garbage collector
- **Catégorie** : technique
- **Niveau** : senior
- **Tags** : java, jvm, gc
- **Intention** : comprendre la mémoire de la JVM et diagnostiquer une fuite.
- **Points attendus** : générations young/old ; G1 par défaut ; fuite = référence conservée ; outils heap dump et profilers.
- **Réponse modèle (plan)** : décrire les générations, expliquer une fuite typique (cache sans éviction), citer les outils.
- **Pièges** : appeler System.gc() comme solution.

### Records et classes scellées
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : java, record, sealed
- **Intention** : connaître les apports de Java récent.
- **Points attendus** : record pour des données immuables avec equals généré ; sealed pour contrôler la hiérarchie ; usage avec switch.
- **Réponse modèle (plan)** : donner un exemple de DTO en record et une hiérarchie scellée.
- **Pièges** : utiliser un record pour une entité JPA mutable.

## Spring

### Injection par constructeur ou par champ
- **Catégorie** : technique
- **Niveau** : junior
- **Tags** : spring, injection, dependance
- **Intention** : connaître les bonnes pratiques d'injection.
- **Points attendus** : injection par constructeur, champs final, testabilité ; @Autowired sur champ déconseillé.
- **Réponse modèle (plan)** : expliquer l'immutabilité des dépendances et les tests sans contexte Spring.
- **Pièges** : dépendance circulaire masquée par le champ injecté.

### Scopes et cycle de vie des beans
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : spring, bean, scope
- **Intention** : comprendre l'instanciation et la durée de vie des beans.
- **Points attendus** : singleton par défaut ; prototype ; request et session en web ; @PostConstruct et @PreDestroy.
- **Réponse modèle (plan)** : décrire les scopes, citer un cas où un singleton ne doit pas garder d'état.
- **Pièges** : stocker des données utilisateur dans un singleton.

### @Transactional et proxies
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : spring, transactional, proxy, jpa
- **Intention** : comprendre les limites de l'AOP Spring.
- **Points attendus** : le proxy intercepte les appels externes ; l'auto-invocation contourne la transaction ; readOnly pour les lectures.
- **Réponse modèle (plan)** : expliquer le proxy, donner l'exemple d'auto-invocation et sa correction.
- **Pièges** : @Transactional sur une méthode privée ; transaction ouverte pendant un appel réseau.

### Auto-configuration de Spring Boot
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : spring boot, autoconfiguration
- **Intention** : comprendre comment Boot configure l'application.
- **Points attendus** : conditions @ConditionalOn* sur le classpath et les propriétés ; surcharge par un bean propre ; spring-boot-starter.
- **Réponse modèle (plan)** : décrire les conditions, montrer comment désactiver ou surcharger une auto-configuration.
- **Pièges** : ajouter des dépendances sans savoir ce qu'elles activent.

### Configuration externalisée et profils
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : spring boot, configuration, profils, secrets
- **Intention** : vérifier la gestion des configurations par environnement.
- **Points attendus** : application.yml ; placeholders ${VAR:defaut} ; @ConfigurationProperties typé ; secrets hors du dépôt.
- **Réponse modèle (plan)** : montrer des variables d'environnement pour les secrets et des profils pour les environnements.
- **Pièges** : secrets codés en dur ; valeurs par défaut sensibles.

### Exécution asynchrone avec @Async
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : spring, async, executor
- **Intention** : maîtriser le traitement asynchrone dans Spring.
- **Points attendus** : @EnableAsync ; exécuteur dédié et borné ; même limite du proxy que @Transactional.
- **Réponse modèle (plan)** : expliquer le proxy, le choix d'un exécuteur borné et la gestion des erreurs.
- **Pièges** : exécuteur illimité ; appel async dans la même classe.

### Événements applicatifs et AFTER_COMMIT
- **Catégorie** : technique
- **Niveau** : senior
- **Tags** : spring, evenements, transactions
- **Intention** : concevoir un traitement déclenché après une transaction validée.
- **Points attendus** : ApplicationEventPublisher ; @TransactionalEventListener(AFTER_COMMIT) pour ne pas traiter une donnée non validée ; limites en cas de crash.
- **Réponse modèle (plan)** : décrire le flux, expliquer pourquoi AFTER_COMMIT, citer la reprise au démarrage.
- **Pièges** : traiter l'événement avant le commit ; événement perdu sans trace.

### Gestion globale des erreurs
- **Catégorie** : technique
- **Niveau** : junior
- **Tags** : spring, rest, exceptions
- **Intention** : concevoir une réponse d'erreur cohérente.
- **Points attendus** : @RestControllerAdvice ; code métier stable ; ne pas exposer les traces internes.
- **Réponse modèle (plan)** : montrer un format d'erreur uniforme avec code, statut et message.
- **Pièges** : renvoyer 200 avec un message d'erreur ; afficher la stack trace.

## JPA et SQL

### Lazy, Eager et problème N+1
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : jpa, hibernate, n+1, lazy
- **Intention** : détecter et corriger les requêtes en cascade.
- **Points attendus** : chargement LAZY par défaut recommandé ; N+1 = une requête par élément ; fetch join ou @EntityGraph.
- **Réponse modèle (plan)** : expliquer le N+1 sur une liste, corriger avec un fetch join.
- **Pièges** : EAGER partout ; LazyInitializationException hors transaction.

### Index SQL
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : sql, index, mysql
- **Intention** : savoir quand indexer et ce que cela coûte.
- **Points attendus** : index sur clés de filtre et de jointure ; coût en écriture ; index composite dans l'ordre des filtres.
- **Réponse modèle (plan)** : donner un exemple de requête filtrée et l'index correspondant, puis le coût.
- **Pièges** : indexer chaque colonne ; ignorer EXPLAIN.

### Transactions ACID et isolation
- **Catégorie** : technique
- **Niveau** : senior
- **Tags** : sql, transactions, isolation
- **Intention** : raisonner sur la cohérence des données.
- **Points attendus** : atomicité, cohérence, isolation, durabilité ; lectures sales, non répétables, fantômes ; niveaux d'isolation.
- **Réponse modèle (plan)** : définir ACID, illustrer une anomalie et le niveau qui l'évite.
- **Pièges** : confondre isolation et verrouillage applicatif.

### Cascade et orphanRemoval
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : jpa, cascade, orphanremoval
- **Intention** : gérer correctement les relations parent-enfant.
- **Points attendus** : cascade ALL sur la relation propriétaire ; orphanRemoval supprime l'enfant retiré de la collection ; suppression en cascade côté base.
- **Réponse modèle (plan)** : décrire la relation, expliquer la suppression d'une réponse et de sa rétroaction.
- **Pièges** : supprimer par requête SQL sans cohérence JPA.

### Migrations de schéma avec Flyway
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : flyway, migration, sql
- **Intention** : faire évoluer une base sans perte de données.
- **Points attendus** : scripts versionnés immuables ; ddl-auto none ou validate ; retour arrière par nouvelle migration.
- **Réponse modèle (plan)** : expliquer le versionnement, le risque de modifier un script déjà appliqué.
- **Pièges** : ddl-auto update en production.

### Pagination efficace
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : jpa, pagination, sql
- **Intention** : éviter les pages coûteuses sur de grandes tables.
- **Points attendus** : Pageable avec tri stable ; OFFSET coûteux en profondeur ; pagination par curseur.
- **Réponse modèle (plan)** : expliquer l'OFFSET, proposer un curseur sur un identifiant trié.
- **Pièges** : tri non déterministe ; charger toute la table.

## REST

### Codes HTTP 200, 201, 202, 204
- **Catégorie** : technique
- **Niveau** : junior
- **Tags** : rest, http
- **Intention** : choisir le bon statut de réponse.
- **Points attendus** : 201 pour une création avec Location ; 202 pour un traitement asynchrone accepté ; 204 sans contenu.
- **Réponse modèle (plan)** : associer chaque code à un cas concret.
- **Pièges** : 200 pour toutes les réponses.

### Idempotence de PUT et POST
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : rest, http, idempotence
- **Intention** : concevoir des opérations sûres à répéter.
- **Points attendus** : GET, PUT et DELETE idempotents ; POST non idempotent sauf clé d'idempotence.
- **Réponse modèle (plan)** : donner l'exemple d'un PUT qui remplace une ressource et d'un paiement protégé par une clé.
- **Pièges** : répéter un POST de création sans protection.

### Conception d'une API de ressources imbriquées
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : rest, api, conception
- **Intention** : structurer des URL lisibles et cohérentes.
- **Points attendus** : noms au pluriel ; imbrication limitée à deux niveaux ; actions spécifiques en sous-ressource.
- **Réponse modèle (plan)** : proposer des routes pour une session et ses réponses.
- **Pièges** : verbes dans les URL ; imbrication profonde.

### Validation des entrées
- **Catégorie** : technique
- **Niveau** : junior
- **Tags** : validation, rest, spring
- **Intention** : garantir la qualité des données d'entrée.
- **Points attendus** : Bean Validation avec @NotBlank, @Size ; validation côté serveur même si le client valide ; erreur 400 détaillée.
- **Réponse modèle (plan)** : montrer une annotation sur un DTO et le format d'erreur.
- **Pièges** : faire confiance au frontend seul.

## Sécurité

### Fonctionnement d'un JWT
- **Catégorie** : technique
- **Niveau** : junior
- **Tags** : jwt, securite, authentification
- **Intention** : comprendre l'authentification sans session.
- **Points attendus** : en-tête, charge utile, signature HMAC ou RSA ; vérifier la signature et l'expiration ; ne pas mettre de secret dans la charge.
- **Réponse modèle (plan)** : décrire la structure, la vérification côté passerelle.
- **Pièges** : croire qu'un JWT est chiffré ; accepter alg none.

### Rotation des secrets
- **Catégorie** : technique
- **Niveau** : senior
- **Tags** : securite, secrets, jwt
- **Intention** : gérer un secret partagé entre services.
- **Points attendus** : stockage hors dépôt ; variables d'environnement ou coffre ; rotation planifiée et invalidation des anciens jetons.
- **Réponse modèle (plan)** : décrire le stockage, la procédure de rotation et son impact sur les sessions.
- **Pièges** : secret dans Git ; même secret par défaut dans le code.

### Éviter l'accès aux données d'un autre utilisateur (IDOR)
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : securite, autorisation, idor
- **Intention** : vérifier la sécurité des accès aux ressources.
- **Points attendus** : filtrer toujours par identifiant de l'utilisateur authentifié ; répondre 404 pour une ressource d'un autre ; ne jamais faire confiance à un identifiant reçu.
- **Réponse modèle (plan)** : montrer une requête findByIdAndUserId et pourquoi renvoyer 404.
- **Pièges** : findById seul ; confiance dans un en-tête fourni par le client.

### CORS
- **Catégorie** : technique
- **Niveau** : junior
- **Tags** : cors, securite, spring
- **Intention** : comprendre les requêtes inter-origines.
- **Points attendus** : origine explicite autorisée ; pré-vol OPTIONS ; éviter * avec les identifiants.
- **Réponse modèle (plan)** : expliquer le pré-vol et la configuration à un seul point d'entrée.
- **Pièges** : allowedOrigins * avec allowCredentials.

## Microservices

### Découverte de services avec Eureka
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : eureka, microservices, service discovery
- **Intention** : comprendre comment les services se trouvent.
- **Points attendus** : enregistrement au démarrage ; heartbeat ; résolution par nom logique.
- **Réponse modèle (plan)** : décrire l'enregistrement, la résolution et la sécurisation du registre.
- **Pièges** : adresses IP figées ; registre exposé sans authentification.

### Passerelle API et routage
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : spring cloud gateway, api gateway, microservices
- **Intention** : concevoir le point d'entrée unique.
- **Points attendus** : routes par prédicat Path ; filtres d'authentification ; CORS centralisé.
- **Réponse modèle (plan)** : présenter les routes, le filtre JWT et le rôle de la passerelle.
- **Pièges** : passerelle qui fait de la logique métier.

### Répartition de charge côté client
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : loadbalancer, microservices
- **Intention** : comprendre le choix d'instance.
- **Points attendus** : Spring Cloud LoadBalancer ; cache de la liste d'instances ; retrait des instances en échec.
- **Réponse modèle (plan)** : expliquer le choix d'instance et l'impact d'un cache trop long.
- **Pièges** : cache de liste vide qui provoque des 503.

### Communication synchrone ou asynchrone
- **Catégorie** : technique
- **Niveau** : senior
- **Tags** : microservices, kafka, messaging
- **Intention** : choisir le mode de communication entre services.
- **Points attendus** : REST pour une réponse immédiate ; messages pour découpler et absorber la charge ; idempotence des consommateurs.
- **Réponse modèle (plan)** : comparer les deux, donner un cas pour chacun.
- **Pièges** : chaîne d'appels synchrones longue.

### Résilience : timeouts, retry, circuit breaker
- **Catégorie** : technique
- **Niveau** : senior
- **Tags** : resilience, circuit breaker, microservices
- **Intention** : protéger un service contre les pannes en cascade.
- **Points attendus** : timeouts explicites ; retry borné avec backoff ; circuit breaker qui ouvre après des échecs répétés.
- **Réponse modèle (plan)** : expliquer les trois mécanismes et leur ordre d'application.
- **Pièges** : retry sans limite qui amplifie la charge.

## Docker

### Dockerfile multi-étapes
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : docker, dockerfile
- **Intention** : produire des images légères et reproductibles.
- **Points attendus** : étape de build Maven puis image JRE ; cache des dépendances ; artefact seul copié.
- **Réponse modèle (plan)** : décrire les deux étapes et le cache de dépendances.
- **Pièges** : image de build contenant les sources et les outils.

### Docker Compose : réseaux, volumes et healthchecks
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : docker, docker compose, healthcheck
- **Intention** : orchestrer plusieurs services en local.
- **Points attendus** : réseau partagé ; volumes pour la persistance ; depends_on avec condition service_healthy.
- **Réponse modèle (plan)** : décrire un service de base de données avec healthcheck et son dépendant.
- **Pièges** : depends_on sans attendre la disponibilité réelle.

### Utilisateur non privilégié dans un conteneur
- **Catégorie** : technique
- **Niveau** : junior
- **Tags** : docker, securite
- **Intention** : réduire la surface d'attaque.
- **Points attendus** : USER non root ; image minimale ; secrets via variables d'environnement.
- **Réponse modèle (plan)** : expliquer le risque du root et la création d'un utilisateur système.
- **Pièges** : secrets copiés dans l'image.

## Tests

### Tests unitaires et tests d'intégration
- **Catégorie** : technique
- **Niveau** : junior
- **Tags** : junit, tests
- **Intention** : savoir quoi tester à quel niveau.
- **Points attendus** : unitaire pour la logique pure ; intégration pour la base et les controllers ; pyramide des tests.
- **Réponse modèle (plan)** : décrire la pyramide et un exemple pour chaque niveau.
- **Pièges** : tout tester par des tests end-to-end lents.

### Mocks avec Mockito
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : mockito, tests, junit
- **Intention** : isoler une classe de ses dépendances.
- **Points attendus** : mock des collaborateurs ; vérifier les interactions avec parcimonie ; ne pas mocker ce qu'on teste.
- **Réponse modèle (plan)** : montrer un test de service avec un repository simulé.
- **Pièges** : trop de verify qui fige l'implémentation.

### Tests d'intégration avec Testcontainers
- **Catégorie** : technique
- **Niveau** : senior
- **Tags** : testcontainers, tests, docker, mysql
- **Intention** : tester contre une vraie base.
- **Points attendus** : conteneur éphémère par test ; base identique à la production ; migrations Flyway jouées.
- **Réponse modèle (plan)** : décrire le conteneur MySQL de test et ce qu'il valide.
- **Pièges** : base H2 qui diverge de MySQL.
