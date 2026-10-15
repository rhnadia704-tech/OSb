# Module EXTRACT & Diagnostic GSI Live

Système d'extraction en direct du matériel, des HALs, des vendor blobs et des configurations du téléphone hôte, couplé à un moteur d'inspection et de comparaison intelligente avec les projets GSI décompressés dans `BUILD/UNPACK/` pour auditer l'efficacité des patchs PHH/Treble et préparer un portage from scratch 100% compatible.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Décisions architecturales confirmées suite à vos instructions :**
> - **Source GSI à inspecter** : Sélection directe parmi les projets déjà décompressés dans `BUILD/UNPACK/` via une liste déroulante dynamique. Aucune re-décompression requise.
> - **Audit d'efficacité des patchs PHH / Treble** : Analyse approfondie des mécanismes du GSI (scripts `phh-on-boot.sh`, `rw-system.sh`, `init.phh.rc`, `phh-sepolicy`, overlays treble) et diagnostic précis de leurs angles morts vis-à-vis du vendor spécifique de l'appareil actuel (audio proprietary, VoLTE/IMS, camera provider, services init propriétaires MTK/Qualcomm/Exynos/Tensor).
> - **Base de données de référence du téléphone hôte (Interopérabilité future)** :
>   1. `filesystem_tree.json` : Arborescence intégrale de toutes les partitions (`/system`, `/vendor`, `/product`, `/odm`, `/apex`, `/system_ext`) avec permissions Linux, contextes SELinux, liens symboliques et empreintes.
>   2. `critical_dependency_graph.json` : Graphe complet de dépendances (parsing ELF `DT_NEEDED` des `.so`, liaisons `linker`, VNDK namespaces et dépendances de services `init.*.rc`).
>   3. `boot_security_trust_chain.json` : Mécanisme de signature, chaîne de confiance et sécurité de boot (AVB 2.0 / dm-verity, clés release-keys, drapeaux `verifiedbootstate`, `vbmeta`, Keymint/Keystore2/Gatekeeper/TEE).
>   4. `hardware_database.json` : Base de données matérielle détaillée (SoC, GPU, Audio, Caméra, Capteurs, RIL/Modem, Biométrie, Écran/DSI cutouts).
>   5. `porting_blueprint.json` & `gsi_diff_matrix.json` : Directives de portage et d'injection directement exploitables par les prochains outils de build sans ré-explication.

---

## 1. Overview & Core Concept

### What It Does
Le module **EXTRACT** accomplit deux missions fondamentales :
1. **Extraction Live & Base de Données Téléphone** :
   - Récolte tous les fichiers physiques sans exception des partitions accessibles (`/vendor`, `/odm`, `/system/vendor`, `/apex`, `/system/etc`, configurations de démarrage, binaires et `.so`).
   - Construit une base de connaissances exhaustive sur le téléphone actuel stockée dans `BUILD/EXTRACT/projects/<DeviceName>/` avec arborescence complète, graphe de dépendances et chaîne de confiance.
2. **Diagnostic Comparatif GSI Unpacké & Évaluation des Patchs PHH** :
   - Se connecte directement au dossier décompressé dans `BUILD/UNPACK/` choisi par l'utilisateur.
   - Inspecte l'implémentation interne du GSI (scripts d'initialisation PHH, politiques SELinux Treble, daemons de compatibilité).
   - Confronte les exigences du vendor hôte avec les capacités réelles du GSI unpacké afin de signaler avec certitude si les patchs PHH intégrés suffisent ou s'il faut générer des adaptateurs/shims sur-mesure.

### Target Audience & Persona
Développeurs de systèmes d'exploitation mobiles et créateurs de ROMs customs qui ont besoin d'une vue chirurgicale de leur matériel et d'une automatisation totale de l'intégration Vendor-GSI.

### Key Value
Fournit un socle de données pérenne et standardisé. Les outils futurs (Builder, Patcher, Flasher) pourront lire directement les fichiers JSON générés sans nécessiter de configuration manuelle.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Lancement & Synthèse Hôte** :
   - Clic sur l'icône **Extract** sur le bureau ou dans le lanceur.
   - Une carte d'accueil affiche l'identité de l'appareil (Modèle, Chipset/Plateforme, Version Android, SPL, Statut Root/SELinux, ABI).

2. **Sélection du Projet GSI Décompressé (`BUILD/UNPACK/`)** :
   - Un menu déroulant (`ExposedDropdownMenuBox`) liste instantanément tous les sous-dossiers trouvés dans `BUILD/UNPACK/` (ex: `PixelExperience_arm64`, `LineageOS_21_td`, etc.).
   - Possibilité de rafraîchir la liste ou d'ouvrir le dossier sélectionné dans le gestionnaire de fichiers.

3. **Lancement de l'Extraction & Analyse Croisée** :
   - Bouton d'action principal : **"Lancer l'Extraction Complète & Diagnostic GSI"**.
   - Affichage d'un panneau de statut dynamique avec indicateur d'avancement et terminal en direct :
     * **Étape 1** : Cartographie arborescente du téléphone hôte & capture des métadonnées (`filesystem_tree.json`).
     * **Étape 2** : Analyse de la chaîne de confiance et de signature de boot (`boot_security_trust_chain.json`).
     * **Étape 3** : Extraction physique des HALs, scripts `.rc`, binaires et blobs `.so` dans `BUILD/EXTRACT/<Device>/`.
     * **Étape 4** : Construction du graphe de dépendances ELF et VNDK (`critical_dependency_graph.json`).
     * **Étape 5** : Inspection du GSI unpacké et audit des patchs PHH (`phh-on-boot`, `rw-system`, `phh-sepolicy`).
     * **Étape 6** : Diagnostic de compatibilité vendor et génération du `porting_blueprint.json`.

4. **Consultation des Résultats & Navigation par Onglets** :
   - **Onglet "Diagnostic GSI & Patchs PHH"** :
     * Résumé de l'efficacité des patchs PHH vis-à-vis du vendor (Validés, Inefficaces, Manquants).
     * Alertes rouges/oranges sur les ruptures de compatibilité (ex: Audio HAL 7.1 non pris en charge par le GSI, Binder version mismatch, camera provider bloqué).
   - **Onglet "Chaîne de Confiance & Sécurité"** :
     * État AVB, signature, drapeaux de boot, configuration dm-verity et recommandations pour maintenir le boot.
   - **Onglet "Base de Données & Arborescence"** :
     * Explorateur interactif du `filesystem_tree.json` et des dépendances critiques.
   - **Bouton d'Action** : "Ouvrir l'extraction dans Files" ou "Voir les JSONs générés".

### Visual Identity & Theme
- **Ambiance** : Interface d'ingénierie OS Ubuntu Touch, verre teinté (`Color(0xF00B132B)`), bordures subtiles (`#334155`).
- **Codes Couleurs** :
  * Vert émeraude (`#10B981`) : Patchs PHH fonctionnels, signatures intègres.
  * Orange ambre (`#F59E0B`) : Patchs PHH partiels nécessitant des shims ou overlays.
  * Rouge corail (`#EF4444`) : Patchs inefficaces / incompatibilité critique de vendor_init.
  * Cyan néon (`#06B6D4`) : Fichiers extraits et graphe de dépendances.

---

## 3. Key Product Decisions & Trade-Offs

### Décision 1 : Consommation Directe des Projets Décompressés dans `BUILD/UNPACK/`
- **Approche** : Au lieu de requérir une image brute `.img`, l'analyseur cible directement les dossiers existants dans `BUILD/UNPACK/`. Il inspecte les fichiers déjà extraits (`system/build.prop`, `system/bin/phh-su`, `system/etc/init/`, `system/etc/vintf/`).
- **Avantage** : Zéro attente de décompression, interopérabilité directe et fluide avec le module `UNPACK_REPACK`.

### Décision 2 : Moteur d'Évaluation Critique des Patchs PHH Treble
- **Approche** : Analyse statique des scripts init PHH (`init.phh.rc`, `rw-system.sh`) et des règles sepolicy injectées dans le GSI. Le moteur compare ces règles avec les services et HALs déclarés dans le vendor hôte :
  * Si le vendor utilise un socket ou service propriétaire non couvert par `phh-sepolicy`, une alerte spécifique est émise.
  * Si le vendor nécessite des extensions Binder (ex: MTK audio / Qualcomm display), le moteur vérifie si les patchs GSI les prennent en charge.
- **Avantage** : Évite les bootloops mystérieux en explicitant avant même le flash pourquoi un patch PHH générique échouera sur ce téléphone particulier.

### Décision 3 : Fichiers JSON Universels Définis pour le Pipeline OSBuilder
- **Approche** : Standardisation stricte de 5 fichiers JSON sous `BUILD/EXTRACT/<DeviceName>/` :
  1. `filesystem_tree.json` (Structure complète avec permissions et contextes SELinux)
  2. `critical_dependency_graph.json` (Arbre des dépendances binaires ELF et services)
  3. `boot_security_trust_chain.json` (Chaîne de confiance, AVB, clés et conditions de boot)
  4. `hardware_database.json` (Périphériques, SoC, capteurs, modules)
  5. `porting_blueprint.json` (Directives d'injection claires pour les modules futurs)

---

## 4. Technical Architecture & Data Strategy

### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                                   OSShell UI                                     │
│                                                                                  │
│  ┌──────────────────────┐  ┌──────────────────────────────────────────────────┐  │
│  │ ModuleRegistry       │  │ ExtractScreen (M3 Jetpack Compose)               │  │
│  │  - id: "extract"     │  │  - DeviceOverviewCard (Specs & Root)             │  │
│  │  - title: "Extract"  │  │  - UnpackedGsiSelector (BUILD/UNPACK/ projects)  │  │
│  │  - icon: "developer" │  │  - PhhPatchAuditView (Effectiveness verdict)     │  │
│  │                      │  │  - DatabaseExplorer (JSON trees & Security chain)│  │
│  └───────────┬──────────┘  └──────────────────────────┬───────────────────────┘  │
└──────────────┼────────────────────────────────────────┼──────────────────────────┘
               │                                        │
               ▼                                        ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                             Live Extraction Engine                               │
│                                                                                  │
│  ┌───────────────────────────┐  ┌───────────────────────┐  ┌──────────────────┐  │
│  │ LiveHostHarvester         │  │ BinaryDependencyGraph │  │ BootSecurityChain│  │
│  │  - Partitions walker      │  │  - ELF DT_NEEDED scan │  │  - AVB / vbmeta  │  │
│  │  - Root & Direct copy     │  │  - Linker namespaces  │  │  - Signatures    │  │
│  │  - HALs, .rc, .so, blobs  │  │  - Init service trees │  │  - SELinux rules │  │
│  └───────────┬───────────────┘  └───────────┬───────────┘  └────────┬─────────┘  │
│              │                              │                       │            │
│              └───────────────────────┬──────┴───────────────────────┘            │
│                                      ▼                                           │
│  ┌────────────────────────────────────────────────────────────────────────────┐  │
│  │ PhhPatchAuditor & GSI Comparator                                           │  │
│  │  - Scans BUILD/UNPACK/<Project>/ for PHH scripts & patches                 │  │
│  │  - Evaluates PHH coverage against host vendor requirements                 │  │
│  │  - Generates actionable GSI porting diff and recommendations               │  │
│  └───────────────────────────────────┬────────────────────────────────────────┘  │
└──────────────────────────────────────┼───────────────────────────────────────────┘
                                       │
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                   BUILD/EXTRACT/<DeviceName>/ Database Hub                       │
│                                                                                  │
│  ├── filesystem_tree.json             (Arborescence complète & contextes SELinux)│
│  ├── critical_dependency_graph.json   (Arbre de dépendances ELF, so, linker, rc) │
│  ├── boot_security_trust_chain.json   (Chaîne de confiance, AVB, clés, dm-verity)│
│  ├── hardware_database.json           (SoC, GPU, Audio, RIL, Caméras, Écran)     │
│  ├── porting_blueprint.json           (Directives pour futurs outils)            │
│  ├── hals/                            (Fichiers et binaires de HALs extraits)    │
│  ├── configs/                         (init.*.rc, audio_policy, media_codecs)    │
│  ├── overlays/                        (RRO apks et configs d'adaptation)         │
│  └── blobs/                           (lib, lib64, firmware, modules noyau)      │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State

- **`PhhPatchAuditResult`** :
  * `hasPhhTreble`: Booléen indiquant la présence de TrebleDroid / PHH dans le GSI décompressé.
  * `detectedPatches`: Liste des patchs trouvés (`rw-system`, `phh-su`, `sepolicy-inject`, `treble-overlay-*`, etc.).
  * `unsupportedVendorFeatures`: Liste des spécificités du vendor hôte non gérées par les patchs PHH (ex: HALs caméra propriétaires, daemon audio MTK, socket RIL non déclaré).
  * `verdict`: Enum (`FULLY_COMPATIBLE`, `NEEDS_ADDITIONAL_PATCHES`, `CRITICAL_INCOMPATIBILITY`).
  * `remediationPlan`: Liste des actions correctives (shims à greffer, règles sepolicy à ajouter dans `vendor_init`).

- **`BootSecurityTrustChain`** :
  * `avbVersion`: Version d'Android Verified Boot.
  * `verifiedBootState`: État du bootloader (`green`, `yellow`, `orange`).
  * `dmVerityMode`: Statut de la vérification d'intégrité des blocs.
  * `signKeysType`: Type de signature système (`release-keys`, `test-keys`).
  * `selinuxStatus`: Mode actif (`Enforcing`, `Permissive`).
  * `keymasterSecurityLevel`: Niveau d'isolation matérielle (TEE, StrongBox).

- **`FilesystemTreeNode`** :
  * Chemin relatif, nom, taille, permissions POSIX (ex: `0755`), propriétaire/groupe (`root:shell`), contexte SELinux (`u:object_r:vendor_file:s0`), type (`FILE`, `DIR`, `SYMLINK`), cible de symlink.

- **`CriticalDependencyNode`** :
  * Nom du binaire ou de la librairie, chemin, architecture (ELF 32/64 bits), liste `needed` (`DT_NEEDED`), namespace linker associé (`default`, `sphal`, `vndk`), services init consommateurs.

### Plan d'Implémentation des Fichiers

1. **`com.example.extract.LiveHostHarvester.kt`** :
   - Détection des privilèges Root / Shizuku / su.
   - Moissonnage des propriétés système (`Build`, `getprop`, `ro.product.*`, `ro.vndk.*`).
   - Parcours complet et extraction des fichiers de partitions disponibles vers `BUILD/EXTRACT/`.
   - Création de `filesystem_tree.json` et `hardware_database.json`.

2. **`com.example.extract.BinaryDependencyGraphBuilder.kt`** :
   - Analyseur d'en-têtes ELF pour extraire les dépendances `DT_NEEDED` sans outils externes.
   - Analyse des fichiers `init.*.rc` pour lier les services aux exécutables et librairies associées.
   - Exportation de `critical_dependency_graph.json`.

3. **`com.example.extract.BootSecurityChainAuditor.kt`** :
   - Audit de la chaîne de confiance de boot (props AVB, vbmeta, dm-verity, clés, SELinux).
   - Exportation de `boot_security_trust_chain.json`.

4. **`com.example.extract.PhhPatchAuditor.kt`** :
   - Scan des dossiers du projet dans `BUILD/UNPACK/`.
   - Identification des scripts PHH (`system/bin/phh-*`, `system/etc/init/init.phh.*.rc`, `phh-sepolicy`).
   - Comparaison croisée avec les spécificités du vendor extrait pour déceler les patchs inefficaces.
   - Exportation de `porting_blueprint.json` et `gsi_diff_matrix.json`.

5. **`com.example.ui.extract.ExtractScreen.kt`** :
   - Interface M3 immersive intégrant :
     * Sélection du projet décompressé dans `BUILD/UNPACK/` via menu déroulant.
     * Bouton d'action et barre de progression interactive multi-étapes.
     * Onglets de diagnostic (Patchs PHH, Chaîne de sécurité, Arborescence, JSONs générés).
     * Boutons pour naviguer directement dans le Gestionnaire de Fichiers vers les extraits.

6. **Intégration `ModuleRegistry.kt` & `OSShell.kt`** :
   - Enregistrement du module `extract` ("Extract & GSI Diagnostic").
   - Icône sur le bureau et intégration dans la barre latérale Ubuntu Touch et le gestionnaire de fenêtres.
