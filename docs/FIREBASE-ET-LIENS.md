# Configuration Firebase et App Links

## Firebase personnel

Configuration de ce rendu : projet `videfrigo-2dbca`, application Android `fr.mathieu.videfrigo`, propriété Google Analytics déjà associée. Le `google-services.json` correspondant est présent localement et ignoré par Git.

1. Dans Firebase Console, créer ou ouvrir son projet, activer Google Analytics et enregistrer une application Android avec le package **fr.mathieu.videfrigo**.
2. Télécharger `google-services.json` et le placer dans `app/google-services.json`. Ce fichier est ignoré par Git ; ne pas utiliser celui de Carnet, dont le package est différent.
3. Recompiler avec `./gradlew assembleDebug` et installer la nouvelle version **sans désinstaller**.
4. Ouvrir Réglages dans Vide-frigo. Autoriser les analytics volontairement et autoriser les notifications. Le refus des analytics n'empêche aucune autre fonction.
5. Copier le jeton avec **Copier mon jeton FCM**. Dans Firebase Console → Messaging, envoyer un message de test à ce jeton.

Pour tester l'ouverture du détail, ajouter ces données personnalisées au message :

```text
foodId = UUID d'un aliment présent sur le téléphone
source = reminder
```

L'UUID figure dans le lien obtenu en partageant un aliment depuis sa fiche.
Un message sans `foodId` ouvre le frigo. Un identifiant absent du stockage affiche un écran expliquant son indisponibilité.

Tester avec l'app au premier plan puis en arrière-plan, processus éventuellement détruit. **Ne pas utiliser `am force-stop` pour simuler une simple fermeture** : Android bloque alors la réception jusqu'à la prochaine ouverture. Un message `notification` reçu en arrière-plan est affiché par FCM ; ses données sont traitées par `MainActivity` au clic. Au premier plan ou pour un message de données, `FridgeMessagingService` construit la notification.

Les rappels de dates sont locaux et lus dans Room : ils restent utiles hors ligne. FCM couvre les notifications distantes demandées par le TP, testables depuis la console. L'API de synchronisation n'envoie pas automatiquement de push FCM.

## Analytics : les trois événements

```bash
adb shell setprop debug.firebase.analytics.app fr.mathieu.videfrigo
```

Dans Firebase Console → Analytics → DebugView, vérifier :

| Événement | Manipulation |
|---|---|
| `first_food_added` | Activer le consentement, puis créer un premier aliment après activation de Firebase |
| `reminder_opened` | Cliquer sur un rappel ou sur la notification FCM de test |
| `widget_food_opened` | Cliquer sur un aliment présent dans le widget |

Le premier événement n'est envoyé qu'une fois par installation après consentement. Aucun événement d'avant le consentement n'est rejoué. Pour recommencer ce test, utiliser une installation de test distincte ; effacer les données supprimerait aussi le frigo.

Les écrans Compose sont suivis manuellement avec `screen_view`, en plus des trois événements métier :

| Écran | `screen_name` |
|---|---|
| Liste du frigo | `fridge_list` |
| Ajout/modification | `food_form` |
| Détail d'un aliment | `food_detail` |
| Réglages | `settings` |

Le suivi automatique des Activity est désactivé pour éviter les doublons. Une rotation ne recompte pas l'écran conservé par le ViewModel. Après acceptation du consentement, l'écran actuellement affiché peut être mesuré ; les visites précédentes ne sont pas rejouées. Aucun identifiant d'aliment n'est envoyé dans `screen_view`.

Avant de consentir, vérifier dans DebugView qu'aucun événement n'est envoyé. Après acceptation, parcourir les quatre écrans et vérifier leurs noms. Retirer ensuite le consentement et vérifier l'arrêt de la collecte pour les nouvelles actions.

```bash
adb shell setprop debug.firebase.analytics.app .none.
```

La collecte est désactivée par défaut dans le manifest. Le consentement est mémorisé dans DataStore et peut être retiré. Les trois événements ne contiennent ni photo, ni nom, ni identifiant d'aliment, ni jeton d'installation. Les identifiants techniques éventuellement utilisés par le SDK relèvent de Firebase Analytics.
Les permissions publicitaires `AD_ID` et `ACCESS_ADSERVICES_*` sont explicitement retirées du manifest fusionné, et leur absence est vérifiée dans l'APK par un test instrumenté.

## Association du domaine

Le manifest utilise `PWA_HOST` de `local.properties`. Le domaine choisi est **videfrigo-2dbca.web.app**, hébergé par Firebase Hosting. L'association a été publiée et Android a confirmé `verified` le 9 novembre 2026. La variable conserve son nom du cours ; elle désigne ici le domaine des liens Android, distinct du site Netlify de référence.

1. Obtenir l'empreinte de l'APK réellement installé :

```bash
./gradlew signingReport
# ou : apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk
```

2. Ajouter cette entrée au fichier `https://<PWA_HOST>/.well-known/assetlinks.json`, en conservant les autres entrées du domaine :

```json
{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "fr.mathieu.videfrigo",
    "sha256_cert_fingerprints": ["EMPREINTE_SHA256_REELLE"]
  }
}
```

3. Le fichier complet est un tableau JSON, servi en HTTPS sans redirection. Déclarer les empreintes debug et release si ces deux signatures sont utilisées.
   `assetlinks-entry.json`, dans ce dossier, contient l'entrée exacte pour l'APK debug construit sur cette machine. Le tableau complet publié est dans `hosting/public/.well-known/assetlinks.json`.
4. Refaire la vérification :

```bash
adb shell pm verify-app-links --re-verify fr.mathieu.videfrigo
adb shell pm get-app-links fr.mathieu.videfrigo
adb shell am start -a android.intent.action.VIEW -d 'https://<PWA_HOST>/foods/<UUID>'
```

L'état attendu est **verified**. Un lancement avec `-n fr.mathieu.videfrigo/.MainActivity` permet de tester le routage, mais **ne prouve pas** la vérification du domaine.
