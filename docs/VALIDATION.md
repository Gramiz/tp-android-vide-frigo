# Validation du TP

Vérifications réalisées le 9 novembre 2026 sur l'émulateur Medium Phone API 36.1, avec l'API Node locale reliée par ADB. Aucun test sur téléphone physique n'est revendiqué.

## Contrôles automatisés exécutés

| Contrôle | Résultat |
|---|---|
| `./gradlew assembleDebug` | Réussi |
| `./gradlew testDebugUnitTest` | 2 tests réussis : saisies et calcul des jours civils |
| `./gradlew lintDebug` | Réussi, aucune erreur ; avertissements de versions et de compatibilité documentés par le rapport Lint |
| Suite instrumentée complète via `adb shell am instrument -w …AndroidJUnitRunner` sur la version Firebase | 5 tests réussis : migration v1→v2, persistance, outbox après perte de confirmation, absence de permissions publicitaires et collecte/suivi automatique désactivés par défaut |
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
| Photo synchronisée | Aliment « Pommes » reçu par le serveur avec photo JPEG et version 1 |
| Routage HTTPS | Lien implicite `https://videfrigo-2dbca.web.app/foods/<UUID>` ouvrant le bon détail, sans cibler le package |
| Mise à jour de l'APK | Installation avec `adb install -r` de la nouvelle version ; les deux aliments et la photo restent présents |
| Rappel local | Autorisation accordée, rappels activés ; notification « À consommer bientôt » affichée avec les deux aliments à vérifier |
| Routage après mort du processus | Intent explicite avec les paramètres du PendingIntent de notification, après `am kill` : bon détail ouvert |
| FCM réel | Message technique envoyé via l'API officielle FCM HTTP v1 vers cette installation d'émulateur après `am kill` ; notification reçue et clic ouvrant le frigo. Le message ne contient aucun nom ni identifiant d'aliment |
| Analytics en direct | `first_food_added`, `reminder_opened`, `widget_food_opened` visibles dans Firebase DebugView ; paramètre d'écran `food_detail` vérifié dans la console |
| Consentement | Mesure désactivée avant accord ; après retrait, le SDK confirme `analytics_storage=denied` et `app measurement enabled=false`. Consentement laissé désactivé en fin de test |
| App Link | JSON HTTPS 200 publié sur Firebase Hosting ; `pm get-app-links` indique `videfrigo-2dbca.web.app: verified` |

## Configuration et préparation de la démo

Firebase Analytics, FCM et Firebase Hosting utilisent le projet `videfrigo-2dbca`. L'application Android y est enregistrée et son fichier `google-services.json` est présent localement, hors Git. Les quatre écrans Compose ont des noms stables (`fridge_list`, `food_form`, `food_detail`, `settings`) et le suivi automatique est désactivé. Les permissions publicitaires sont absentes du manifest final.

La [capture DebugView](captures/analytics-debugview.png) montre les trois événements et le nom d'écran transmis. La [capture du détail](captures/app-link-detail.png) accompagne le test du lien HTTPS implicite. Le mode de diagnostic Firebase et les logs verbeux ont été désactivés après les essais.

Pour la présentation, refaire l'envoi du message de test depuis la console Firebase avec le jeton de l'appareil utilisé, puis répéter le parcours du README. Les essais ont été faits sur émulateur ; il reste à répéter la démo sur le support retenu et à savoir expliquer les choix du code.

La signature release détaillée est un bonus du sujet : une clé release personnelle et une API HTTPS sont nécessaires seulement pour cette livraison supplémentaire. L'APK fourni pour la démo est une debug signée, avec API locale reliée par ADB ; il n'est pas présenté comme une release. Aucun envoi sur le Play Store n'a été effectué ou requis pour ces vérifications.

## Difficulté rencontrée

Pendant le test photo, l'émulateur en manque d'espace a purgé le dossier de cache pendant que la caméra était ouverte : le fournisseur de fichiers ne retrouvait plus la destination. La capture temporaire a été déplacée dans un dossier privé durable `files/captures/`, supprimé après import ou annulation. Le JPEG final est réduit hors du thread principal avant sauvegarde.

## Architecture à présenter

Room est la source de vérité de l'interface. Le Repository écrit l'aliment et l'opération dans une transaction. WorkManager envoie sous contrainte réseau avec reprise ; les identifiants d'opération rendent le rejeu idempotent. Un conflit exige un choix explicite, sans supprimer silencieusement l'opération. Le widget et les rappels relisent la base locale.
