# Publication App Link à autoriser

Domaine : `https://vide-frigo-mathieu-tp.netlify.app`.

Le fichier [assetlinks.json](assetlinks.json) est le tableau complet préparé : les associations TWA et Carnet déjà publiées sont conservées, et l'association `fr.mathieu.videfrigo` est ajoutée avec l'empreinte réelle de l'APK debug.

La procédure préparée crée d'abord un déploiement Netlify de prévisualisation avec les 18 fichiers existants. **Seul `/.well-known/assetlinks.json` change** ; les 17 autres fichiers restent référencés par leur SHA existant. Les fonctions `foods`, `push` et `reminders` sont également conservées par leur empreinte existante, ainsi que la planification `0 8 * * *`.

Avant toute publication, comparer le brouillon au déploiement actuel : un seul fichier changé, mêmes fonctions et même planification. Vérifier ensuite la réponse HTTPS du fichier et la présence des trois associations. Publier seulement si ces contrôles passent, puis demander à Android une nouvelle vérification et contrôler l'état `verified`.

La revue automatique d'autorisation a refusé la création du brouillon, faute d'accord explicite sur ce déploiement externe et son domaine. Aucun déploiement n'a été créé par cette tentative. Le fichier est prêt ; la publication reste en attente d'autorisation.
