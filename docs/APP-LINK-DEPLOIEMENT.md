# App Link sur Firebase Hosting

Projet Firebase : `videfrigo-2dbca`. Domaine : `https://videfrigo-2dbca.web.app`.

Le fichier publié et versionné est [hosting/public/.well-known/assetlinks.json](../hosting/public/.well-known/assetlinks.json). Il associe le domaine à `fr.mathieu.videfrigo` et à l'empreinte SHA-256 réelle de l'APK debug construit sur cette machine.

Le site Firebase ne contenait aucune release avant cette publication. La configuration Hosting ne publie que ce fichier. Elle sert le JSON avec le bon type MIME et autorise explicitement `.well-known`, que les réglages Firebase par défaut pourraient ignorer. L'association automatique Firebase est désactivée car le fichier est géré manuellement.

Sans l'application installée, les liens d'accueil et `/foods/**` redirigent le navigateur vers la PWA de référence. L'adresse Netlify de cette PWA n'est pas modifiée ; les App Links de cette application Android utilisent le domaine Firebase.

```bash
firebase deploy --only hosting --project videfrigo-2dbca
adb shell pm verify-app-links --re-verify fr.mathieu.videfrigo
adb shell pm get-app-links fr.mathieu.videfrigo
```

Validation effectuée le 9 novembre 2026 : réponse HTTPS 200, JSON identique au fichier versionné et état Android **verified** pour `videfrigo-2dbca.web.app`. Un lien HTTPS implicite `/foods/<UUID>` ouvre le bon détail, sans cibler le package avec ADB.

Une signature release différente nécessite d'ajouter son empreinte au même fichier, puis de republier. Aucun déploiement Netlify n'a été effectué.
