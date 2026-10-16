# Correctif & Restauration Intégrale de l'Interface EXTRACT

Rétablissement immédiat et fiable de l'interface complète d'EXTRACT telle qu'elle était avant la régression de layout, tout en conservant 100% des nouvelles fonctionnalités (décompilation DEX, décodage AXML des overlays, graphe relationnel de chaîne FOD, dissection des .so et évaluation des risques de bootloop).

---

## Cause du Problème Identifiée sur la Capture

L'écran noir provient du composant `DraggableDrawerScaffold` :
1. Une conversion erronée de pixels en densité (`totalHeightPx.dp` au lieu de calculs relatifs en `LocalDensity`) a appliqué un padding de plus de 1500dp, repoussant l'accueil de la page complètement hors de l'écran visible.
2. Le tiroir recouvrait la zone principale avec un fond opaque sans calculer correctement les hauteurs disponibles.

---

## Solution & Plan de Restauration

### 1. Rétablissement de la Structure Éprouvée d'EXTRACT
- **Suppression du composant `DraggableDrawerScaffold` instable** qui provoquait le masquage de l'écran.
- **Rétablissement de l'accueil complet visible immédiatement** :
  * Carte d'identité de l'appareil hôte (SoC, version Android, SDK, badge ROOT / Sandbox).
  * Sélecteur de projet GSI dans `BUILD/UNPACK/` avec menu déroulant et rafraîchissement.
  * Bouton principal d'action : "Lancer Extraction, Dissection & FOD".
  * Jauge de progression animée et journal d'étape en direct.

### 2. Agrandissement Fluide du Tableau de Bord (Sans Écran Noir)
- Pour permettre d'agrandir et d'explorer tout le contenu du tableau de bord comme demandé :
  * Ajout d'un bouton d'agrandissement fluide en haut du tableau de bord.
  * En mode étendu, les cartes supérieures s'estompent avec animation pour donner 100% de la hauteur de l'écran aux onglets et graphes, sans aucun calcul hasardeux de pixels.
  * Un simple clic ou geste vers le bas rétablit la vue partagée normale.

### 3. Conservation de 100% des Nouveautés Techniques
- **Onglet "Chaîne FOD & Graphe"** : Graphe relationnel des 7 maillons (Noyau $\leftrightarrow$ HAL $\leftrightarrow$ Extensions $\leftrightarrow$ Binder $\leftrightarrow$ Framework $\leftrightarrow$ SystemUI $\leftrightarrow$ Overlays) avec comparaison miroir Hôte vs GSI.
- **Onglet "Désassemblage DEX / XML"** : Analyse bytecode de SystemUI et services.jar (classes UDFPS, callbacks, HBM) et paramètres réels décodés des overlays RRO (`config_hasFingerprintOnDisplay`, coordonnées X/Y, rayon).
- **Onglet "FOD 6 Couches"** : Diagnostic factuel avec causes d'échec et directives de patch.
- **Onglet "Dissection .SO"** : Recherche de symboles, signatures C++ démanglées et descripteurs Binder.
- **Onglet "Rapprochement Bootloop"** : Bannières de risque et shims préconisés.
- **Onglet "Base JSON Factuelle"** : Consultation directe de l'ensemble des JSONs produits.
- **Multitâche `TaskManager`** : Maintien du suivi en arrière-plan avec pilule sur le bureau et bague sur la barre latérale.
