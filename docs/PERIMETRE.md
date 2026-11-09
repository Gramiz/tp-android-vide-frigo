# Vide-frigo — périmètre du TP

Application Android native, Kotlin et Compose, minSdk 26. TPN sert uniquement de référence fonctionnelle. Aucun écran Carnet ni TWA n'est repris.

Liste triée par date, ajout/modification/suppression, photo facultative, détail, partage et réception de texte, réglages. Fonctionnement local d'abord avec Room et file d'opérations transactionnelle ; synchronisation réseau via un Repository et WorkManager. Préférences DataStore. Notifications locales de rappel et réception FCM. App Links vers le détail. Widget « À consommer bientôt ». Trois événements analytics soumis au consentement.

Interface en français, contrôles Material 3 natifs, vert et fond clair inspirés de Vide-frigo, thème sombre système. Pas de compte, recettes, scanner ou autre fonctionnalité hors sujet. Aucun contenu de frigo envoyé aux analytics.

Configuration externe : Firebase personnel, publication HTTPS de l'API et association du domaine. Ces validations seront distinguées des tests locaux réellement exécutés.
