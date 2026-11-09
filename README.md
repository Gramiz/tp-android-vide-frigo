# Vide-frigo Android

TP Android : gestion des aliments et de leurs dates limites, en Kotlin et Jetpack Compose. Vide-frigo sert d'idée fonctionnelle ; cette application est native et indépendante de Carnet et de la TWA.

## Construire et lancer

Prérequis : Android Studio compatible AGP 9.4.1, JDK 21, SDK Android 37, téléphone ou émulateur Android 8/API 26 minimum. Pour FCM, utiliser un appareil avec les services Google Play.

```bash
cp local.properties.example local.properties
# Adapter sdk.dir dans ce fichier.
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Ouvrir ce dossier, contenant `settings.gradle.kts`, dans Android Studio. Le package est `fr.mathieu.videfrigo`.

### API locale de démonstration

Node.js 22+ suffit, sans installation npm supplémentaire :

```bash
cd server
npm start
```

Dans un autre terminal, avec le téléphone/émulateur connecté :

```bash
adb reverse tcp:8787 tcp:8787
adb shell am start -n fr.mathieu.videfrigo/.MainActivity
```

Par défaut, `SYNC_BASE_URL=http://127.0.0.1:8787/`. Le tunnel ADB doit être rétabli après reconnexion du téléphone. Le trafic HTTP est autorisé uniquement vers les adresses locales dans la variante debug. Une release doit utiliser une API HTTPS publique ; l'API de la PWA d'origine a un protocole différent et ne peut pas être substituée directement à celle fournie ici.

Le serveur conserve les données dans `server/.data/`, hors Git. Arrêter et relancer Node conserve les aliments. L'API est destinée au TP et à une seule instance Node ; pour l'héberger, conserver un volume persistant et placer un reverse proxy HTTPS devant elle. Elle ne comporte pas de compte utilisateur : le jeton aléatoire de l'installation délimite son frigo. Une nouvelle installation ne récupère pas automatiquement les données de la PWA ou d'un autre appareil.

Sans serveur joignable, le frigo local fonctionne et les changements restent explicitement en attente.

### Firebase et liens HTTPS

Placer le fichier de son projet personnel dans `app/google-services.json`, puis recompiler. Sans ce fichier, le projet compile et le frigo fonctionne, mais FCM et les analytics sont signalés comme non configurés. Ce mode permet le développement, **il ne valide pas ces exigences du TP**.

La procédure complète, les trois événements et l'association de domaine figurent dans [docs/FIREBASE-ET-LIENS.md](docs/FIREBASE-ET-LIENS.md).

Le projet Firebase utilisé est `videfrigo-2dbca`, avec Analytics et FCM. L'application Android `fr.mathieu.videfrigo` y est enregistrée. Le fichier personnel a été récupéré localement et reste hors Git. Le propriétaire du projet peut le télécharger à nouveau :

```bash
firebase apps:sdkconfig ANDROID 1:392297106216:android:35dce8171cab4c06bdde1d --project videfrigo-2dbca --out app/google-services.json
```

L'association App Link est publiée sur Firebase Hosting : `https://videfrigo-2dbca.web.app/.well-known/assetlinks.json`. Android confirme `verified`. Voir [la procédure Hosting](docs/APP-LINK-DEPLOIEMENT.md). L'API de synchronisation reste le serveur Node local décrit plus haut ; Firebase Hosting sert l'association de domaine.

## Architecture et choix à expliquer

```text
Compose → FridgeViewModel → FridgeRepository
                              ├─ Room : aliments et outbox
                              ├─ Retrofit : API de synchronisation
                              └─ WorkManager : envoi sous contrainte réseau
DataStore : préférences, consentement, jetons et dernier envoi
Widget et rappels : lecture de Room, sans dépendre du réseau
FCM → notification → MainActivity → détail de l'aliment
```

| Choix | Pourquoi |
|---|---|
| Room comme source de vérité de l'interface | Le frigo s'ouvre et reste modifiable en mode avion |
| Transaction aliment + outbox | Une action locale ne peut pas être enregistrée sans sa demande d'envoi |
| Opérations UUID et version serveur | Le rejeu après timeout ne duplique pas les modifications ; les conflits restent visibles |
| Suppression conservée comme tombstone | Une ancienne copie ne ressuscite pas un aliment supprimé |
| Travail unique WorkManager avec réseau | Android choisit le moment d'envoi ; une modification arrivée pendant l'envoi reste planifiée |
| DataStore | Les préférences et consentements sont durables et distincts des données structurées |
| SavedStateHandle | La navigation et le petit brouillon sont restaurés après rotation ou mort du processus |
| Photo dans les fichiers privés | Room garde son chemin ; la capture temporaire est compressée hors du thread principal |
| Permission au clic | La caméra n'est demandée que pour la photo facultative ; refus et réglages restent gérés |

Le ViewModel survit à une rotation ; il ne survit pas à lui seul à la mort du processus. Les données métier sont dans Room et le petit état de formulaire dans SavedStateHandle. Une fermeture forcée/effacement des données n'est pas une restauration d'état normale.

La base est en version 2. `MIGRATION_1_2` ajoute l'outbox au schéma local v1 sans effacer les aliments. Le schéma Room exporté est dans `app/schemas/` ; un test instrumenté crée une base v1 puis vérifie la migration réelle. Aucun `fallbackToDestructiveMigration`.

## Fonction native : widget

**À consommer bientôt** affiche les trois dates les plus proches, le nombre de dates dépassées et un bouton d'ajout. Un clic ouvre le bon aliment. Le widget Android est hébergé par le lanceur, hors de l'application ; il ne s'agit pas d'un écran web. Il est alimenté par Room et actualisé après les changements et périodiquement. Android peut différer les mises à jour.

Réglages → **Ajouter le widget**, ou appui long sur l'écran d'accueil → Widgets → Vide-frigo. Implémentation avec `AppWidgetProvider` et `RemoteViews`, sans bibliothèque supplémentaire.

Les rappels locaux sont vérifiés périodiquement après 8 h, au plus une fois par jour si des aliments sont à vérifier. Ils ne sont pas des alarmes exactes. Refuser les notifications laisse toutes les fonctions du frigo accessibles.

## Tests

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
# Avec un émulateur ou téléphone lancé :
./gradlew connectedDebugAndroidTest
cd server && npm test
```

Les tests instrumentés vérifient la migration sans perte, la persistance Room, la séquence ajout/modification/suppression avec perte simulée de confirmation réseau et les protections du manifest analytics. Les tests API vérifient persistance, isolation, rejeu idempotent, conflits concurrents, tombstones et validation des entrées.

Les manipulations et le statut des exigences sont consignés dans [docs/VALIDATION.md](docs/VALIDATION.md). Les trois événements ont été observés dans DebugView, un vrai message FCM a été reçu app en arrière-plan et le domaine Firebase est vérifié par Android.

## Démonstration

1. Ajouter un aliment et sa date en mode avion ; montrer le statut « en attente ».
2. Relancer l'app, tourner l'appareil : données et brouillon conservés.
3. Réactiver le réseau : observer l'envoi et la disparition du statut en attente.
4. Montrer la photo, le partage et la gestion d'une permission refusée.
5. Montrer le widget et cliquer sur l'aliment pour ouvrir son détail.
6. Recevoir un message FCM en arrière-plan et ouvrir le bon écran.
7. Montrer les trois événements dans DebugView et l'App Link `verified`.
8. Expliquer Room, l'outbox, WorkManager et une difficulté résolue.

## Signature et mise à jour

La debug utilise la clé locale générée par Android. Pour signer une release avec sa propre clé :

```bash
mkdir -p .secrets
keytool -genkeypair -keystore .secrets/release.jks -alias videfrigo -keyalg RSA -keysize 2048 -validity 10000
```

Renseigner les propriétés `RELEASE_*` et l'URL HTTPS dans `local.properties`, puis lancer `./gradlew assembleRelease`. Garder la clé et les mots de passe dans un emplacement privé sauvegardé. Une nouvelle livraison utilise la même clé et un `versionCode` supérieur. Les données survivent à une mise à jour, mais une désinstallation les efface. Une debug et une release signées différemment ne se mettent pas à jour l'une par-dessus l'autre.

Le dépôt ne contient ni clé de signature, ni mots de passe, ni jetons, ni base de données de frigo, ni `google-services.json`. Les identifiants publics de projet et la configuration Hosting sont versionnés pour permettre de comprendre et reproduire le déploiement.
