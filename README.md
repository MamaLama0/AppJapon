# Carnet Japon — app Android de suivi d'itinéraire

App Android (Kotlin) pour suivre le carnet de route **Kyoto historique & Alpes japonaises** (19 → 27 avril 2027).

## Fonctions
- **Grande carte du Japon** vectorielle (MapLibre + tuiles OpenFreeMap / données OpenStreetMap, **sans clé API**),
  noms en français ou en lettres latines avec le japonais dessous. Les lieux apparaissent en zoomant :
  grandes zones (Kyoto / Alpes / Tokyo) → régions → lieux principaux → lieux secondaires.
- **Cartes hors-ligne** (menu ⋮ → Cartes hors-ligne) : ≈ 120 Mo couvrant rues et sentiers autour de chaque étape,
  routes de chaque journée et vue d'ensemble. À télécharger en Wi-Fi quelques jours avant le départ.
- **68 lieux** avec fiche détaillée : histoire, nom japonais, conseils pratiques, étapes du carnet qui y passent,
  bouton « Y aller » (au choix : Vela Maps, Organic Maps, Google Maps…).
- **Sélection du jour** (J1 → J9) : tracé du jour, marqueurs numérotés, liste des étapes avec horaires,
  billets, étapes facultatives (surlignées dans la synthèse).
- **Localisation en temps réel** : position sur la carte, mode « suivre », partie du tracé déjà parcourue en gris,
  distance le long du tracé, alerte « hors tracé », prochaine étape avec distance / direction / temps à pied,
  avance ou retard sur l'horaire le jour J.
- **Coche automatique** des étapes à l'arrivée sur place (désactivable) + bandeau « Vous êtes à … ».
- **Autour de moi** : lieux du carnet à moins de 3 km, triés par distance.
- **Import de vos propres tracés** (GPX, KML ou KMZ — ex. export Google My Maps) pour chaque jour, via le menu ⋮.

Les jours 2, 3 et 4 intègrent la *Synthèse Kyoto* (Shōren-in, Hirano-jinja, départ 15h00 vers Nakatsugawa…).

## Données
Tout le contenu (lieux, histoires, journées) est dans `app/src/main/assets/trip.json`.
Coordonnées issues d'OpenStreetMap/Nominatim ; les lieux marqués `"approx": true` sont à position approximative.
Le style de carte (`app/src/main/assets/map_style.json`) est le style « Liberty » d'OpenFreeMap avec des étiquettes localisées.

## Construire
```
./gradlew assembleRelease   # APK : app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest # tests + captures d'écran dans app/build/screenshots
```
L'APK est aussi produit par GitHub Actions (artefact « carnet-japon-apk »).
