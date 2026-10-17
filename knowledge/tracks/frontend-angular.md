---
id: frontend-angular
title: Questions techniques Angular et TypeScript
track: frontend-angular
doc_type: question_bank
lang: fr
version: 1
---

# Questions techniques Angular et TypeScript

## TypeScript

### Types, interfaces et unions
- **Catégorie** : technique
- **Niveau** : junior
- **Tags** : typescript, types, interface
- **Intention** : maîtriser le typage de base.
- **Points attendus** : interface ou type pour une forme de données ; union et littéraux pour un ensemble fermé ; éviter any.
- **Réponse modèle (plan)** : définir un statut en union de littéraux et une interface de DTO.
- **Pièges** : any partout ; cast sans vérification.

### Génériques
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : typescript, generiques
- **Intention** : écrire du code réutilisable et typé.
- **Points attendus** : paramètre de type ; contrainte extends ; inférence.
- **Réponse modèle (plan)** : écrire une fonction générique qui renvoie le type de son entrée.
- **Pièges** : génériques inutiles qui compliquent la lecture.

### Narrowing et unions discriminées
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : typescript, narrowing, union
- **Intention** : traiter chaque cas d'une union sans erreur.
- **Points attendus** : propriété discriminante ; contrôle exhaustif avec never ; typeof et in.
- **Réponse modèle (plan)** : modéliser un état loading/ready/error et le traiter dans un switch.
- **Pièges** : oublier un cas sans erreur de compilation.

## Composants et templates

### Composants standalone
- **Catégorie** : technique
- **Niveau** : junior
- **Tags** : angular, composants, standalone
- **Intention** : connaître l'architecture moderne d'Angular.
- **Points attendus** : standalone: true ; imports explicites ; lazy loading par route.
- **Réponse modèle (plan)** : expliquer l'absence de NgModule et l'import des dépendances.
- **Pièges** : importer tout un module partagé dans chaque composant.

### Communication parent enfant
- **Catégorie** : technique
- **Niveau** : junior
- **Tags** : angular, input, output, composants
- **Intention** : faire circuler les données entre composants.
- **Points attendus** : input pour descendre les données ; output avec EventEmitter ou output() pour remonter les événements.
- **Réponse modèle (plan)** : montrer un composant liste qui émet la sélection.
- **Pièges** : modifier un input directement depuis l'enfant.

### Contrôle de flux dans les templates
- **Catégorie** : technique
- **Niveau** : junior
- **Tags** : angular, template, control flow
- **Intention** : écrire des templates lisibles.
- **Points attendus** : @if, @for avec track, @switch ; track pour la performance des listes.
- **Réponse modèle (plan)** : afficher une liste avec état vide et une condition.
- **Pièges** : @for sans track sur de grandes listes.

## Signaux et détection de changements

### Signals : signal, computed et effect
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : angular, signals, reactivite
- **Intention** : comprendre la réactivité par signaux.
- **Points attendus** : signal pour l'état ; computed pour les dérivés, en lecture seule ; effect pour les effets de bord.
- **Réponse modèle (plan)** : écrire un compteur avec un computed du total.
- **Pièges** : écrire un signal dans un computed ; effect qui modifie l'état qu'il lit.

### OnPush et détection de changements
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : angular, onpush, performance
- **Intention** : réduire les cycles de rendu.
- **Points attendus** : ChangeDetectionStrategy.OnPush ; changements par référence ; async pipe.
- **Réponse modèle (plan)** : expliquer quand OnPush se déclenche et comment l'utiliser avec un observable.
- **Pièges** : muter un objet sans changer sa référence.

## RxJS

### Observables et opérateurs de flux
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : rxjs, observable, operateurs
- **Intention** : composer des flux asynchrones.
- **Points attendus** : map, filter, switchMap, mergeMap ; abonnement et désabonnement ; flux froid et chaud.
- **Réponse modèle (plan)** : chaîner une recherche avec debounceTime et switchMap.
- **Pièges** : subscribe imbriqués ; fuite d'abonnement.

### switchMap, mergeMap et concatMap
- **Catégorie** : technique
- **Niveau** : senior
- **Tags** : rxjs, switchmap, mergemap, concatmap
- **Intention** : choisir la stratégie d'aplatissement adaptée.
- **Points attendus** : switchMap annule la précédente ; mergeMap en parallèle ; concatMap en file ordonnée.
- **Réponse modèle (plan)** : donner un cas pour chaque opérateur.
- **Pièges** : switchMap pour une écriture qui ne doit pas être annulée.

### Gestion des abonnements
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : rxjs, abonnement, memoire
- **Intention** : éviter les fuites mémoire.
- **Points attendus** : async pipe ; takeUntilDestroyed ; unsubscribe à la destruction.
- **Réponse modèle (plan)** : montrer takeUntilDestroyed dans un composant.
- **Pièges** : abonnement dans un service singleton sans fin.

## Routage et guards

### Routage et lazy loading
- **Catégorie** : technique
- **Niveau** : junior
- **Tags** : angular, routing, lazy loading
- **Intention** : structurer la navigation.
- **Points attendus** : routes enfants ; loadComponent pour le chargement différé ; paramètres d'URL.
- **Réponse modèle (plan)** : déclarer une route détail avec un paramètre id.
- **Pièges** : charger tout le bundle au démarrage.

### Guards de route
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : angular, guards, securite, routing
- **Intention** : protéger les pages selon l'authentification et le rôle.
- **Points attendus** : canActivate ; redirection vers la connexion ; le guard n'est pas une sécurité : le serveur doit contrôler.
- **Réponse modèle (plan)** : expliquer le guard côté client et la vérification serveur obligatoire.
- **Pièges** : croire que le guard protège les données.

### Intercepteurs HTTP
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : angular, interceptors, http, jwt
- **Intention** : traiter les requêtes et réponses de façon transverse.
- **Points attendus** : ajout de l'en-tête Authorization ; gestion centralisée des 401 ; clone de la requête.
- **Réponse modèle (plan)** : décrire un intercepteur qui ajoute le jeton et déconnecte sur 401.
- **Pièges** : stocker le jeton dans un endroit accessible au moindre script.

## Formulaires

### Formulaires réactifs
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : angular, forms, reactive forms, validation
- **Intention** : valider les saisies de façon fiable.
- **Points attendus** : FormGroup et FormControl typés ; validators ; état touched et erreurs.
- **Réponse modèle (plan)** : créer un formulaire avec validation de longueur et message d'erreur.
- **Pièges** : validation uniquement côté client.

## Performance et qualité

### Performance de chargement
- **Catégorie** : technique
- **Niveau** : senior
- **Tags** : angular, performance, bundle, lazy loading
- **Intention** : réduire le temps de premier affichage.
- **Points attendus** : lazy loading ; réduction du bundle ; images optimisées ; mise en cache HTTP.
- **Réponse modèle (plan)** : proposer des mesures et les outils pour les vérifier.
- **Pièges** : optimiser sans mesurer.

### Tests de composants
- **Catégorie** : technique
- **Niveau** : confirme
- **Tags** : angular, tests, testing
- **Intention** : tester le comportement visible d'un composant.
- **Points attendus** : TestBed ; fixture et détection de changements ; simuler les services.
- **Réponse modèle (plan)** : tester qu'une liste affiche les éléments reçus et émet une sélection.
- **Pièges** : tester les détails d'implémentation plutôt que le rendu.

### Architecture par fonctionnalité
- **Catégorie** : technique
- **Niveau** : senior
- **Tags** : angular, architecture, services
- **Intention** : organiser une grande application.
- **Points attendus** : dossier par fonctionnalité ; services de données séparés des composants ; modèles partagés.
- **Réponse modèle (plan)** : proposer une arborescence par domaine et expliquer la séparation des responsabilités.
- **Pièges** : services fourre-tout ; logique métier dans les templates.

### Accessibilité de base
- **Catégorie** : technique
- **Niveau** : junior
- **Tags** : accessibilite, html, aria
- **Intention** : rendre l'interface utilisable par tous.
- **Points attendus** : éléments sémantiques ; labels associés ; contraste ; navigation au clavier.
- **Réponse modèle (plan)** : citer les points vérifiés sur un formulaire et un bouton.
- **Pièges** : div cliquable sans rôle ni clavier.
