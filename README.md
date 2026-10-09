# Vola Racing – Training (mode solo, v0.1.0)

Application Android de chronométrage d'entraînement (VTT, ski) : parcours A→B sur carte,
chrono automatique par franchissement de portes, enregistrement GPS + IMU + baromètre,
comparaison de passages, export CSV/GPX. Tout reste sur le téléphone.

## Compiler l'APK (sans rien installer) : GitHub Actions
1. Créez un dépôt GitHub, puis « Add file > Upload files » et déposez TOUT le contenu de ce dossier
   (y compris le dossier caché `.github`). Si le glisser-déposer ignore `.github`, créez le fichier
   `.github/workflows/build.yml` à la main (« Create new file ») et collez-y son contenu.
2. Onglet **Actions** > « Build APK » > **Run workflow** (se lance aussi à chaque envoi).
3. Au bout de ~5 min, ouvrez l'exécution et téléchargez l'artefact **app-debug** (ZIP contenant `app-debug.apk`).
4. Si l'exécution est rouge : ouvrez l'étape en échec et copiez l'erreur.

## Compiler en local
Android Studio (Koala ou plus récent), JDK 17 : ouvrir le dossier, laisser Gradle synchroniser.
Le wrapper `gradlew` n'est pas fourni : `gradle wrapper --gradle-version 8.9` (ou Android Studio le crée).
Ensuite `./gradlew test` puis `./gradlew assembleDebug`.

## Installer l'APK
Copiez `app-debug.apk` sur le téléphone, ouvrez-le, autorisez l'installation depuis cette source.
Sur Android 14, désactivez au besoin Play Protect pour les APK de test.

## Utilisation
1. Accueil : choisir VTT ou Ski, « Nouveau parcours ».
2. Parcours : toucher la carte pour poser A puis B (retoucher pour déplacer), régler la largeur de porte, enregistrer.
3. « Démarrer » : accorder la localisation précise. Le chrono part au franchissement de A, s'arrête à B
   (sens A→B uniquement). Voix et vibration au départ et à l'arrivée. « Marquer » pose un repère.
4. Comparer en 3 touchers : Accueil > Passages (1) > passage (2) > autre passage (3).
5. Export : bouton « Exporter » (Téléchargements/VolaRacing) ou « Partager » (ZIP CSV + GPX).

Fichiers bruts : `Android/data/fr.volaracing.training/files/sessions/<id>/` (gps.csv, imu.csv, marks.csv).
`imu.csv` : type `acc` (m/s², gravité incluse), `gyr` (rad/s), `baro` (hPa dans la colonne x).

## Précision
L'instant de franchissement est interpolé linéairement entre deux fixes GPS (jamais le point le plus proche).
Les tests simulent 100 km/h à 10 Hz, 1 Hz et 0,5 Hz : écart < 0,05 s sur trajectoire rectiligne.
Sur trajectoire courbe ou GPS bruité, l'erreur réelle dépend de la qualité du signal (typiquement 1 Hz sur téléphone).

## Limites connues
- Non compilé ni testé par l'auteur (pas de réseau ni de compilateur dans son environnement) : la première exécution
  GitHub Actions est le premier vrai test. Le test « 30 minutes écran verrouillé » est à faire sur téléphone.
- Version MapLibre `11.0.1` à vérifier à la compilation ; si elle n'est pas résolue ou si l'API diffère, ajuster `app/build.gradle.kts`.
- Autonomie : désactiver l'optimisation de batterie pour l'app si l'enregistrement est coupé (constructeurs agressifs).
- Pas de carte hors ligne (bouton « bientôt »). Pas de position de l'utilisateur affichée sur la carte (TODO).
- Écart cumulé : aligné par distance parcourue sur chaque trace ; approximatif si les trajectoires diffèrent.
- Police condensée : police système en attendant la charte Vola (TODO dans `Theme.kt`). Icône : icône système provisoire.
- Un seul ViewModel pour toute l'app (suffisant pour le MVP).
- Vitesse GPS : si le fournisseur ne la donne pas, elle vaut 0 (TODO : la déduire des positions).

## Décisions à prendre
1. Tuiles OSM : `tile.openstreetmap.org` est toléré pour un APK de test perso, pas pour une diffusion. Choisir un fournisseur ou un serveur de tuiles.
2. Précision GPS insuffisante : le passage est conservé et marqué « qualité faible » (au lieu d'être ignoré). À confirmer.
3. Profils par défaut (hypothèses) : VTT porte 20 m / 8 km/h ; Ski porte 30 m / 15 km/h ; fenêtre 1 s ; seuil 25 m.
4. Force G : accélération brute avec gravité (1 G au repos). Alternative : accélération linéaire filtrée.
5. Charte Vola : couleurs, police, icône.
6. Redémarrage du chrono si on repasse A pendant une course : actuellement le chrono repart de zéro à ce nouveau franchissement. À valider.
