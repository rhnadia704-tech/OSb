# OSbuilder — Moteur Android ROM Kitchen Universel & Dynamique Sans Format Ni Arborescence Figée

Ce plan révise l'architecture pour répondre à l'exigence fondamentale : **aucune structure, arborescence, nom de dossier ou liste de partitions n'est prédéterminée ou codée en dur**. 

L'outil devient un **moteur d'inspection et d'extraction dynamique universel**, capable d'analyser n'importe quel fichier de ROM ou GSI (des cas les plus standards aux plus exotiques et complexes), d'en cartographier l'arborescence réelle exacte au niveau des blocs et inodes, et de la reconstituer au repack avec une fidélité de 100%.

---

## 1. Principes Directeurs de l'Architecture Universelle

### A. Zéro Arborescence Préconçue (Dynamic Tree Traversal)
- **Le problème précédent** : Pré-générer des dossiers conventionnels (`app/`, `priv-app/`, `framework/`, `lib/`, etc.) ou forcer un dossier unique `system/`.
- **La nouvelle approche dynamique** :
  - Le moteur ne présuppose **aucun nom de dossier ni hiérarchie**.
  - Il parcourt directement la table des répertoires de l'image (entrées de répertoires EXT4 `ext4_dir_entry_2`, EROFS directory blocks, ou entrées d'archives).
  - Chaque nœud (dossier, sous-dossier, fichier binaire, lien symbolique, socket, fifo, device node) est découvert dynamiquement et reproduit fidèlement dans l'arborescence du projet, quel que soit le niveau d'imbrication ou les noms de répertoires personnalisés par les constructeurs (OEMs).

### B. Découverte Dynamique de Partitions Multiples (Partition Auto-Discovery)
Une ROM ou un GSI complexe peut être :
1. **Une GSI / Partition Unique** : EXT4 ou EROFS brut ou Sparse, avec ou sans System-as-Root (`system_root/system` ou racine directe `/`).
2. **Une Image Dynamique `super.img`** : Contenant des dizaines de partitions logiques dynamiques nommées librement par les constructeurs (`system`, `vendor`, `product`, `system_ext`, `odm`, `cust`, `prism`, `optics`, `mi_ext`, `carrier`, `oem`, `factory`, etc.).
3. **Une Archive OTA `payload.bin` (Android A/B)** : Définissant dynamiquement la liste de ses partitions dans son manifeste Protobuf/Delta (`boot`, `init_boot`, `vendor_boot`, `vbmeta`, `system`, `vendor`, `product`, etc.).
4. **Une Archive Recovery Flashable `.zip`** : Contenant des paires dynamiques `*.new.dat.br` / `*.transfer.list` pour chaque partition présente.
5. **Une Archive Fastboot / TGZ / TAR** : Contenant un ensemble d'images brutes ou sparse.

**Comportement de l'outil** :
- Le moteur cartographie dynamiquement **toutes les partitions découvertes** dans le conteneur.
- Il crée un conteneur de projet avec un sous-dossier par partition réelle découverte : `BUILD/UNPACK/<projet>/partitions/<nom_partition>/` (ou racine directe pour une GSI single-image).
- Chaque partition possède son profil d'intégrité dédié dans `BUILD/Data/projects/<projet>/partitions/<nom_partition>/`.

### C. Gestion Stratégique et Universelle des Liens Symboliques (Symlinks)
- **Aucune hypothèse sur les liens** : Découverte dynamique de tous les types de liens symboliques (`S_IFLNK`), sans liste blanche :
  - Liens relatifs simples (`toybox`, `../../bin/app_process64`)
  - Liens absolus (`/system/bin/sh`, `/vendor/lib64/lib...`)
  - Liens inter-partitions complexes (`/system/vendor -> /vendor`, `/system/product -> /product`)
- **Stockage sans perte** :
  - Sauvegarde exhaustive dans `symlinks.json` et `symlinks.txt` (format Android Kitchen standard) pour chaque partition.
  - Sauvegarde sur disque résiliente : tentative de lien POSIX natif + fichier descripteur `.symlink` transparent avec la cible si le système de fichiers hôte Android (FUSE) bloque la création directe.
- **Reconstitution fidèle au Repack** :
  - Restauration exacte des fast symlinks inline EXT4 (dans `i_data` si $\le 60$ octets) ou extents de données si plus long.
  - Réapplication exacte des contextes SELinux (`file_contexts`) et droits (`fs_config`).

### D. Repacker Intelligent Universel 1:1 (Sans Limite de Taille)
- Élimination totale de la limitation à 32 Mo : lecture de la taille originale exacte depuis `original_info.json` (ex: 3.12 Go, 4.5 Go, etc.).
- **Fidélité 1:1** :
  - Si aucune modification n'a été effectuée : régénération d'une image bit-à-bit conforme (même géométrie, même superbloc, mêmes UUIDs, même hachage).
  - Si des fichiers ont été modifiés/ajoutés/supprimés : recalcul dynamique de la table d'allocation et réinjection chirurgicale en respectant l'arborescence personnalisée et la taille globale de la partition.
- Support du réassemblage en **Image RAW** ou **Sparse Fastboot** au choix de l'utilisateur.

---

## 2. Organisation Dynamique des Données

```
📁 /storage/emulated/0/BUILD/
├── 📁 Data/
│     └── 📁 projects/<nom_projet>/
│           ├── project_manifest.json    (Format conteneur, liste dynamique des partitions)
│           ├── original_info.json       (Taille globale, hash SHA-256, format source)
│           ├── report.txt               (Audit complet et log d'analyse)
│           └── 📁 partitions/           (Une entrée par partition découverte)
│                 └── 📁 <nom_partition>/ (ex: system, vendor, product, cust, etc.)
│                       ├── partition_info.json  (Taille exacte, blocs, block_size, fs_type, UUID)
│                       ├── symlinks.json        (Tous les symlinks découverts dans cette partition)
│                       ├── symlinks.txt         (Format kitchen standard: symlink <target> <path>)
│                       ├── fs_config            (UID, GID, permissions, capabilities réelles)
│                       ├── file_contexts        (Contextes SELinux exacts découverts)
│                       └── structure.json       (Arborescence complète et hashes de validation)
│
├── 📁 UNPACK/<nom_projet>/
│     └── 📁 partitions/                 (Arborescence extraite 100% dynamique)
│           └── 📁 <nom_partition>/      (Exemple: system/ ou vendor/ ou racine GSI)
│                 └── ...                (Tous les fichiers et dossiers réels découverts)
│
└── 📁 PACKED/
      └── <nom_projet>_repacked.img      (Image finale ou archive complète réassemblée)
```

---

## 3. Plan d'Implémentation Technique

### Étape 1 : Moteur de Détection Multi-Conteneurs Universel (`UniversalContainerDetector.kt`)
- Créer `com.example.core.tools.kitchen.UniversalContainerDetector` :
  - Analyse de l'en-tête du fichier (Magic bytes) :
    - `0xED26FF3A` : Android Sparse Image
    - `0xEF53` à l'offset 1080 (0x438) : Système de fichiers EXT4 RAW
    - `0xE0F5E1E2` : Système de fichiers EROFS
    - `0xF2F52010` : Système de fichiers F2FS
    - `0x616C0744` : Image dynamique `super.img` (Android Dynamic Partitions / LpMetadata)
    - `0x43724155` (`CrAU`) : Archive Android OTA `payload.bin`
    - `0x504B0304` (`PK..`) : Archive ZIP flashable Recovery
    - `0x75737461` (`ustar`) : Archive TAR / TGZ Fastboot
  - Extraction de la liste dynamique de partitions contenues sans a priori.

### Étape 2 : Parseur Dynamique de Système de Fichiers (`UniversalFsReader.kt`)
- Créer `com.example.core.tools.kitchen.UniversalFsReader` :
  - **Parcours dynamique d'inodes et répertoires EXT4** :
    - Lecture du superbloc et des descripteurs de groupes de blocs (`ext4_group_desc`).
    - Parcours récursif à partir de l'inode racine (`EXT4_ROOT_INO = 2`).
    - Pour chaque entrée de répertoire (`ext4_dir_entry_2`) :
      - Détection dynamique du type : `EXT4_FT_REG_FILE` (fichier), `EXT4_FT_DIR` (dossier), `EXT4_FT_SYMLINK` (lien symbolique), `EXT4_FT_CHRDEV`/`EXT4_FT_BLKDEV`/`EXT4_FT_FIFO`/`EXT4_FT_SOCK`.
      - Aucune règle de filtrage ou de limitation par nom : tout nœud présent dans le filesystem est lu.
  - **Extraction des données binaires réelles** :
    - Parcours des extents d'arbre (`ext4_extent_header`, `ext4_extent_idx`, `ext4_extent`) pour extraire les blocs de données exacts de chaque fichier.
  - **Décompression par blocs Sparse (`simg2img`)** :
    - Gestion par flux évitant le dépassement de mémoire sur les gros fichiers de 3 à 8 Go.

### Étape 3 : Moteur Universel de Symlinks (`SymlinkEngine.kt`)
- Créer `com.example.core.tools.kitchen.SymlinkEngine` :
  - Extraction des Fast Symlinks EXT4 (cibles $\le 60$ octets stockées dans `i_block`/`i_data`) et des symlinks longs.
  - Enregistrement immédiat dans `symlinks.json` et `symlinks.txt` de la partition concernée.
  - Gestion sécurisée sur le stockage local :
    - Tentative de création du symlink POSIX (`Files.createSymbolicLink`).
    - Si l'environnement de stockage (FUSE) le refuse, création automatique d'un descripteur `.symlink` lisible contenant la chaîne cible.
  - Vérificateur d'intégrité : validation des cibles relatives et absolues.

### Étape 4 : Gestionnaire Central de Métadonnées (`KitchenMetadataManager.kt`)
- Enregistrement dynamique pour chaque partition :
  - `partition_info.json` : taille exacte en octets, nombre total de blocs, taille de bloc, type de système de fichiers, UUID, label de volume.
  - `fs_config` : cartographie de chaque chemin avec UID, GID, mode octal et capabilities.
  - `file_contexts` : contextes SELinux exacts associés à chaque nœud.
  - `structure.json` : empreinte de chaque fichier extrait.

### Étape 5 : Repacker Intelligent Dynamique Haute Fidélité (`UniversalRepacker.kt`)
- Créer `com.example.core.tools.kitchen.UniversalRepacker` :
  - Prise en charge de n'importe quel projet déballé sans prérequis de nommage.
  - **Mode 1:1 sans modification** :
    - Reconstitution rigoureuse avec la taille exacte originale (ex: 3.12 Go), le même superbloc, les mêmes métadonnées et la même disposition.
  - **Mode avec modifications** :
    - Calcul du différentiel des fichiers modifiés dans l'arborescence dynamique.
    - Réinjection avec recalcul des extents, application des permissions depuis `fs_config` et `file_contexts`.
    - Restauration rigoureuse de tous les symlinks répertoriés dans `symlinks.json`.
    - Maintien strict de la géométrie de partition originale.
  - Support du format de sortie RAW ou Sparse.

### Étape 6 : Intégration dans `ImageToolchain.kt`, `TaskManager.kt` et `UnpackRepackScreen.kt`
- Connexion du nouveau pipeline universel à `ImageToolchain.kt`.
- Affichage en temps réel dans `ProcessTerminal` :
  - Nom du conteneur et partitions détectées dynamiquement.
  - Progression en direct (fichiers extraits, symlinks enregistrés, vitesse en Mo/s).
- Interface `UnpackRepackScreen` :
  - Liste dynamique des partitions découvertes avec détails (taille, nombre de fichiers, nombre de symlinks).
  - Inspecteur de symlinks permettant de visualiser tous les liens répertoriés.
  - Sélecteur de mode de réassemblage (RAW 1:1 fidèle ou Sparse Fastboot).

---

## 4. Vérification et Validation

1. **Test GSI / ROM Simple ou Complexe (ex: 3.12 Go)** :
   - Détection automatique du format sans aucune saisie préalable.
   - Extraction de la totalité de l'arborescence réelle sans omission.
   - Tous les liens symboliques (binaires `sh`, liens `vendor`, bibliothèques) répertoriés dans `symlinks.json`/`symlinks.txt`.
2. **Test du Réassemblage** :
   - Génération d'une image dont la taille est exactement conforme (3.12 Go, et non 32 Mo).
   - Intégrité vérifiée et compatibilité Fastboot préservée.
3. **Compilation du Projet** :
   - Exécution de `compile_applet` sans aucune régression.
