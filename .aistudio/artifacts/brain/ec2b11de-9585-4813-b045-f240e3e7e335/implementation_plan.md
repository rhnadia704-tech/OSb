# Module EXTRACT & Dissection Relationnelle des .SO, GSI et FOD (Fingerprint On Display)

Système d'analyse en profondeur, d'extraction enrichie, de décomposition des bibliothèques `.so` et **dissection chirurgicale relationnelle du FOD (Fingerprint On Display) en 6 couches**, couplé à une indexation factuelle du GSI dans `BUILD/Data/IMGXTRACT/<GsiName>/` et une matrice de rapprochement croisé pour garantir un portage sans crash ni bootloop.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Décisions architecturales confirmées suite à vos réponses :**
> 1. **Base de Données Relationnelle Complète & Structurée** :
>    - Tous les éléments extraits et analysés (matériel, noyau, HALs, `.so`, Binder, overlays, services) sont interconnectés par des identifiants uniques (`entity_id`, `layer_id`, `dependencies`, `parent_node`).
>    - Cette base relationnelle fournit aux futurs outils de portage une vision holistique sans besoin de reconfiguration manuelle.
>
> 2. **Investigation Multicouche Exhaustive du FOD (Empreinte sous Écran)** :
>    - **Couche 1 (Noyau & Matériel)** : Pilotes noyau (`/dev/goodix_fp`, etc.), nœuds `sysfs` d'affichage/toucher (`/sys/class/drm/*/fod_ui`, notification tactile), permissions et contextes SELinux $\rightarrow$ `fod_layer_kernel_hardware.json`.
>    - **Couche 2 (HALs & Daemons)** : Daemons déclarés dans `init.*.rc`, HALs biométriques (`android.hardware.biometrics.fingerprint*`), extensions propriétaires constructeur (Xiaomi, Samsung, Oplus, Goodix), et extraction physique des fichiers dans `BUILD/EXTRACT/<DeviceName>/fod/` $\rightarrow$ `fod_layer_hal_daemons.json`.
>    - **Couche 3 (Sécurité & TEE)** : Canaux TrustZone/TEE (`/dev/qseecom`, `/dev/tee0`), fichiers de persistance et calibration (`/mnt/vendor/persist/fingerprint/`) $\rightarrow$ `fod_layer_security_tee.json`.
>    - **Couche 4 (Géométrie & HBM)** : Coordonnées physiques exactes du capteur (X, Y, rayon), protocole d'illumination High Brightness Mode (faisceau vert/blanc/cyan) $\rightarrow$ `fod_layer_geometry_hbm.json`.
>    - **Couche 5 (Framework & GSI Treble)** : Support FOD dans SystemUI et biometrics framework du GSI décompressé, overlays RRO (`config_hasFingerprintOnDisplay`, `config_fodCircleX/Y/Size`), scripts et propriétés PHH (`persist.sys.phh.fod.*`) $\rightarrow$ `fod_layer_framework_gsi.json`.
>    - **Couche 6 (Diagnostic Factuel de Rupture)** : Confrontation directe Vendor hôte vs GSI. Pourquoi le FOD marchera ou échouera (décalage de coordonnées, protocole HBM non relayé, blocage SELinux, version Binder incompatible) avec directives de patch précises $\rightarrow$ `fod_relational_verdict.json`.
>
> 3. **Décomposition & Analyse des `.so`** :
>    - Tables de symboles dynamiques (`.dynsym`, `.symtab`, `.dynstr`) : distinction des symboles exportés (`GLOBAL`/`WEAK`) et importés non résolus (`UND`).
>    - Démangling C++ (Itanium ABI) : traduction en clair de toutes les signatures de fonctions et classes AOSP (`_ZN...`).
>    - Cartographie des interfaces Binder AIDL et HIDL.
>    - Rôle fonctionnel (FOD, Audio, Caméra, HWC, RIL, DRM) et arborescence cible documentée.
>
> 4. **Structure Factuelle dans `BUILD/Data/IMGXTRACT/<GsiName>/`** :
>    - Stockage 100% factuel sans aucune invention ni hallucination : `gsi_manifest.json`, `gsi_exported_symbols.json`, `gsi_binder_services.json`, `gsi_fod_profile.json`.
>
> 5. **Matrice de Rapprochement Croisé & Prévention des Bootloops** :
>    - Identification unitaire des symboles manquants et classification des risques (`CRITICAL_BOOTLOOP`, `FEATURE_DEGRADED`, `SAFE`).
>    - Génération de `vendor_gsi_cross_matrix.json` et `bootloop_risk_assessment.json`.

---

## 1. Overview & Core Concept

### Ce que fait ce module enrichi
- **Dissection du FOD en 6 Couches Relationnelles** :
  - Extraction de chaque fichier physique, binaire, script `.rc`, règle sepolicy, pilote et nœud sysfs lié au capteur sous l'écran.
  - Détermination scientifique et factuelle de la compatibilité du GSI avec le vendor d'origine, en explicitant les causes d'échec exactes.
- **Dissection des Bibliothèques `.so`** :
  - Analyse des symboles exportés/importés, démangling C++, détection des relocalisations et des interfaces Binder.
- **Stockage Factuel dans `IMGXTRACT` & Matrice de Boot** :
  - Génération d'une base relationnelle complète prête pour les futurs outils de portage.

---

## 2. User Experience & Visual Design

### Nouveaux Écrans & Interactions
1. **Sélecteur GSI & Lancement de l'Analyse Avancée** :
   - Sélection du projet GSI dans `BUILD/UNPACK/`.
   - Bouton : **"Extraction Live & Dissection Avancée (.SO, GSI & FOD)"**.
   - Jauge de progression multi-étapes avec logs en direct.
2. **Onglet Dédié "Dissection FOD (Empreinte sous Écran)"** :
   - **Vue en 6 Couches** : Matériel/Noyau, HALs/Daemons, TEE/Calibration, Géométrie HBM, Framework GSI, et Verdict Relationnel.
   - **Diagnostic de Rupture** : Explications détaillées sur les causes d'échec du FOD sur le GSI sélectionné et directives de correction.
3. **Onglet "Dissection .SO & Symboles"** :
   - Moteur de recherche et filtrage par sous-système (FOD/Biométrie, Audio, Caméra, Affichage, RIL, DRM).
   - Visualisation des signatures C++ démanglées et des interfaces Binder.
4. **Onglet "Rapprochement GSI & Risques Bootloop"** :
   - Matrice d'évaluation des risques de bootloop (`bootloop_risk_assessment.json`).
5. **Onglet "Base JSON & Données Factuelles"** :
   - Consultation et copie des fichiers JSON générés dans `BUILD/EXTRACT/` et `BUILD/Data/IMGXTRACT/`.
   - Bouton direct pour ouvrir les dossiers dans le Gestionnaire de Fichiers.

---

## 3. Architecture Technique & Composants

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                   ExtractScreen UI                                      │
│  - HostSpecsCard          - GsiSelectorDropdown         - BootloopRiskBanner            │
│  - Tab: FOD 6 Couches     - Tab: .SO Dissection         - Tab: Cross-Matrix GSI         │
│  - Tab: Arborescence      - Tab: Base JSON Factuelle                                    │
└────────────────────────────────────────┬────────────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                           Moteur de Dissection & Analyse                                │
│                                                                                         │
│  ┌─────────────────────────┐  ┌──────────────────────────┐  ┌────────────────────────┐  │
│  │ FodArchitectureDissector│  │ ElfSymbolDissector       │  │ GsiDeepAnalyzer        │  │
│  │  - 6 Couches FOD        │  │  - Table .dynsym/.symtab │  │  - Décomp. dans        │  │
│  │  - Extraction physique  │  │  - Relocs .rela.plt      │  │    BUILD/Data/IMGXTRACT │  │
│  │  - Diagnostic rupture   │  │  - CppDemangler          │  │  - Indexation symboles │  │
│  │  - Relations & IDs      │  │  - Binder rodata scan    │  │  - Profil FOD GSI      │  │
│  └────────────┬────────────┘  └────────────┬─────────────┘  └───────────┬────────────┘  │
│               │                            │                            │               │
│               └────────────────────────────┼────────────────────────────┘               │
│                                            ▼                                            │
│                       ┌────────────────────────────────────────┐                        │
│                       │ VendorGsiCrossMatcher                  │                        │
│                       │  - Croisement symboles vendor vs GSI   │                        │
│                       │  - Évaluation des risques de bootloop  │                        │
│                       │  - Diagnostic & Causes d'échec FOD     │                        │
│                       └────────────────────┬───────────────────┘                        │
└────────────────────────────────────────────┼────────────────────────────────────────────┘
                                             │
                                             ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                               Stockage des Données Produites                            │
│                                                                                         │
│  /sdcard/BUILD/EXTRACT/<DeviceName>/          /sdcard/BUILD/Data/IMGXTRACT/<GsiName>/   │
│   ├── hardware_database.json                   ├── gsi_manifest.json                    │
│   ├── boot_security_trust_chain.json           ├── gsi_exported_symbols.json            │
│   ├── filesystem_tree.json                     ├── gsi_binder_services.json             │
│   ├── detailed_so_dissection.json              ├── gsi_fod_profile.json                 │
│   ├── fod/ (fichiers physiques extraits)       └── gsi_system_libs_index.json           │
│   ├── fod_relational_database.json                                                      │
│   ├── fod_compatibility_verdict.json                                                    │
│   ├── vendor_gsi_cross_matrix.json                                                      │
│   └── bootloop_risk_assessment.json                                                     │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Plan de Développement des Fichiers

1. **`com.example.core.extract.CppDemangler.kt`** :
   - Démangleur C++ natif pour décoder les symboles manglés AOSP (`_ZN...`).

2. **`com.example.core.extract.ElfSymbolDissector.kt`** :
   - Parseur de symboles dynamiques (`.dynsym`, `.dynstr`), relocalisations et détection des tokens Binder AIDL/HIDL.
   - Classification par rôle fonctionnel et attribution d'emplacement cible.

3. **`com.example.core.extract.FodArchitectureDissector.kt`** :
   - Dissecteur spécialisé pour le capteur d'empreinte sous écran (FOD).
   - Scanne les 6 couches (noyau, HALs, TEE, géométrie HBM, framework GSI, diagnostic de rupture).
   - Copie physique des fichiers clés dans `BUILD/EXTRACT/<DeviceName>/fod/`.
   - Produit `fod_relational_database.json` et `fod_compatibility_verdict.json`.

4. **`com.example.core.extract.GsiDeepAnalyzer.kt`** :
   - Dissection et indexation factuelle du GSI vers `BUILD/Data/IMGXTRACT/<GsiName>/`.
   - Extraction des symboles fournis par `/system/lib/` et `/system/lib64/`.
   - Extraction du profil FOD du GSI (`gsi_fod_profile.json`).

5. **`com.example.core.extract.VendorGsiCrossMatcher.kt`** :
   - Rapprochement croisé des symboles et évaluation des risques de bootloop (`bootloop_risk_assessment.json`).
   - Rapprochement factuel des exigences FOD du vendor avec le GSI.

6. **Mise à jour de `ExtractModels.kt` & `LiveHostHarvester.kt`** :
   - Ajout des modèles de données FOD relationnels.
   - Intégration de la décomposition FOD et des `.so` dans la chaîne de traitement.

7. **Mise à jour de `ExtractScreen.kt`** :
   - Nouvel onglet **"FOD (Empreinte sous Écran)"** présentant les 6 couches et le diagnostic de rupture.
   - Onglets de dissection des `.so`, rapprochement croisé et exploration de `Data/IMGXTRACT/`.
