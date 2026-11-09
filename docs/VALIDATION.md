# Validation du TP

Vérifications réalisées le 9 novembre 2026 sur l'émulateur Medium Phone API 36.1, avec l'API Node locale reliée par ADB. Aucun test sur téléphone physique n'est revendiqué.

## Contrôles automatisés exécutés

| Contrôle | Résultat |
|---|---|
| `./gradlew assembleDebug` | Réussi |
| `./gradlew testDebugUnitTest` | 2 tests réussis : saisies et calcul des jours civils |
| `./gradlew lintDebug` | Réussi, aucune erreur ; avertissements de versions et de compatibilité documentés par le rapport Lint |
| `./gradlew connectedDebugAndroidTest` | 3 tests réussis : migration v1→v2, persistance et outbox après perte de confirmation |
| `cd server && npm test` | 5 tests réussis : persistance/isolation, rejeu, conflit concurrent, suppression, validation |

Le test de migration crée le schéma local v1, y insère un aliment, puis ouvre cette même base avec Room v2 et la migration. Il vérifie l'aliment conservé et la table d'outbox utilisable.

## Manipulations sur l'émulateur

| Exigence | Observation |
|---|---|
| Ajout en mode avion | Aliment présent dans Room, version 0 ; opération durable dans l'outbox ; indication « en attente » |
| Retour du réseau | Aliment reçu automatiquement par l'API, version 1 ; aucune action de renvoi manuel nécessaire |
| Rotation | Brouillon « Yaourts » conservé |
| Mort du processus | Mise en arrière-plan puis `am kill`, PID passé de 6527 à 7461 ; formulaire et brouillon restaurés |
| Permission caméra | Premier refus accepté ; explication avant seconde demande ; après second refus, proposition des réglages Android, réellement ouverts |
| Widget natif | Ajout sur le lanceur, aliment lu depuis Room, clic ouvrant le détail correspondant |
| Réception de partage | Intent `ACTION_SEND text/plain` : le texte partagé préremplit le formulaire |
| Photo | Capture réelle dans la caméra de l'émulateur, retour réussi et JPEG compressé présent dans les fichiers privés après correction |

## Vérifications externes restant à terminer

- **FCM** : ajouter le `google-services.json` personnel, installer la version Firebase, puis recevoir un message de test app en arrière-plan et vérifier son clic.
- **Analytics** : après configuration Firebase et consentement, observer les trois événements dans DebugView. Le code de collecte est présent, mais un événement visible sur un vrai projet n'est pas encore vérifié.
- **App Link verified** : publier l'entrée de signature fournie sur le domaine HTTPS, puis contrôler `pm get-app-links`. La déclaration du manifest et le routage ne suffisent pas à valider cette étape.
- **Release** : la clé release personnelle et une API HTTPS doivent être configurées avant une livraison release. La debug de démonstration est signée avec la clé debug locale ; elle n'est pas présentée comme une release.

La checklist du TP n'est donc pas annoncée intégralement validée tant que les étapes externes ne sont pas exécutées.

## Difficulté rencontrée

Pendant le test photo, l'émulateur en manque d'espace a purgé le dossier de cache pendant que la caméra était ouverte : le fournisseur de fichiers ne retrouvait plus la destination. La capture temporaire a été déplacée dans un dossier privé durable `files/captures/`, supprimé après import ou annulation. Le JPEG final est réduit hors du thread principal avant sauvegarde.

## Architecture à présenter

Room est la source de vérité de l'interface. Le Repository écrit l'aliment et l'opération dans une transaction. WorkManager envoie sous contrainte réseau avec reprise ; les identifiants d'opération rendent le rejeu idempotent. Un conflit exige un choix explicite, sans supprimer silencieusement l'opération. Le widget et les rappels relisent la base locale.
