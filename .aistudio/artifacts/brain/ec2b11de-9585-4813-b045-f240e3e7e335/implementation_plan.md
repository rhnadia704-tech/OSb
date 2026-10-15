# OSbuilder — Stratégie d'Unpack & Repack Intelligente inspirée de blackeangel/UKA

## 1. Analyse approfondie du fonctionnement de blackeangel/UKA

L'analyse du dépôt GitHub [blackeangel/UKA](https://github.com/blackeangel/UKA) (Unpacker Kitchen for Android) met en évidence l'architecture éprouvée des "kitchens" Android professionnelles :

### A. Chaîne de décompression et identification automatique des formats
UKA traite les firmwares Android en couches successives sans supposer de structure figée :
1. **Conteneurs de ROM globaux** : Détection des archives ZIP (Recovery ROM, Fastboot ROM), extraction automatique des fichiers cibles.
2. **Payload OTA A/B (`payload.bin`)** : Détection du magic `CrAU`, lecture des métadonnées de partitions et extraction des sous-images de partitions (`system.img`, `vendor.img`, `product.img`, `system_ext.img`, `odm.img`, `boot.img`).
3. **Super Partition (`super.img`)** : Détection du header Android Dynamic Partitions (magic `g_sla` / `0x616C7367`), extraction des partitions logiques.
4. **Sparse Android Images (`simg`)** : Détection du magic `0xED26FF3A`, conversion automatique en image raw continue (simg2img).
5. **Blocs compressés OTA (`system.new.dat.br` / `.new.dat`)** : Décompression Brotli puis reconstruction de l'image brute via le fichier de commande `transfer.list` (`sdat2img`).
6. **Images brutes EXT4 & EROFS** : Montage ou parsing direct de la structure de blocs pour extraire l'arborescence exacte sans interprétation arbitraire des données binaires.
7. **Noyaux et ramdisks (`boot.img`, `recovery.img`)** : Détection du magic `ANDROID!` ou boot v3/v4, séparation kernel / ramdisk / dtb / cmdline.

### B. Sauvegarde des métadonnées système (`fs_config` & `file_contexts`)
UKA ne se contente pas d'extraire des fichiers sur le disque :
* **`fs_config`** : Consigne pour chaque élément son chemin relatif, UID, GID, permissions POSIX (ex: `0755`), cibles de liens symboliques (symlinks) et Linux capabilities.
  * Exemple standard UKA :
    ```text
    system 0 0 755
    system/bin 0 2000 755
    system/bin/sh 0 2000 755
    system/bin/app_process32 0 2000 755 app_process
    ```
* **`file_contexts`** : Sauvegarde des étiquettes SELinux exactes pour chaque fichier et dossier (ex: `/system(/.*)? u:object_r:system_file:s0`).
* **`config.prop` / `image_info.json`** : Enregistre la taille exacte, la taille de bloc (4096), le nombre d'inodes, l'UUID du système de fichiers, le type original (sparse, raw ext4, erofs, dat.br).

### C. Repackage 1:1 et reconstruction fidèle
* **Si aucune modification n'est apportée** : UKA garantit une fidélité 100% avec l'originale (repack bit-à-bit parfait, hash SHA-256 identique).
* **En cas de modification** : Reconstitution de l'image avec un système de fichiers intègre (ex: `make_ext4fs` ou `mke2fs` + `e2fsdroid` avec `-S file_contexts` et `-C fs_config`, ou `mkfs.erofs`), puis conversion optionnelle en sparse image (`img2simg`) ou format de mise à jour (`img2sdat`). Le fichier résultant est immédiatement lisible par ZArchiver, montable par le kernel Android et flashable par Fastboot/TWRP.

---

## 2. Diagnostic des anomalies constatées lors de vos tests

1. **Extraction de faux dossiers (`anim`, `drawable`, `xml`...)** :
   * **Cause exacte** : Dans `UniversalFsReader.kt`, le scan binaire heuristique cherchait des chaînes de caractères ASCII/UTF-8 arbitraires dans les blocs de l'image. Lorsqu'il traversait des APKs compilés du système (`framework-res.apk`, `SystemUI.apk`), il prenait les chemins internes de ressources Android (`res/anim/...`, `res/drawable/...`) pour des dossiers réels de l'image système et les créait à la racine !
   * **Remède** : Supprimer ce scan heuristique défaillant et le remplacer par le véritable parcours structurel d'arborescence (Superblock -> Inode Table -> Inode 2 Root -> Directory Entries `ext4_dir_entry_2`).

2. **Repack de 32 Mo semblant vide dans ZArchiver** :
   * **Cause exacte** : Dans `UniversalRepacker.kt`, l'écriture concaténait les fichiers bout à bout avec des métadonnées fictives sans créer les véritables structures de données EXT4 attendues par ZArchiver (table d'inodes, bitmaps d'allocation des blocs et des inodes, extents `0xF30A`, entrées de répertoire `.` et `..` sur l'inode 2). ZArchiver ne trouvant pas la table des répertoires valide déclarait l'archive vide.
   * **Remède** :
     - Pour un repack sans modification : réémission 100% bit-exacte (hash SHA-256 identique).
     - Pour un repack après modification : génération d'un conteneur EXT4 formel avec Superblock valide (`0xEF53`), Descripteurs de Groupes de Blocs 32/64 bits, Table d'Inodes réelle, Extents, et structures de répertoires standard lisibles nativement par ZArchiver et Linux.

---

## 3. Plan d'Implémentation Détaillé

### Étape 1 : Moteur intelligent de détection de formats multi-couches (`FormatDetector.kt`)
* Reconnaissance automatique des signatures magiques :
  - **Android Sparse** : `0xED26FF3A`
  - **EXT4 Raw** : Magic `0xEF53` à l'offset `0x438` (Superblock standard)
  - **EROFS Raw** : Magic `0xE0F5E1E2` à l'offset `0x400`
  - **Payload.bin** : Magic `CrAU` (`0x43724155`)
  - **Super Dynamic Partitions** : Magic `g_sla` (`0x616C7367`)
  - **ROM Zip** : Signature PK (`0x504B0304`)
  - **Brotli DAT** : Fichiers appariés `.new.dat` / `.new.dat.br` et `.transfer.list`
  - **Android Boot/Recovery** : Magic `ANDROID!` (`0x414E44524F494421`)
* Déroulement récursif : si une ROM zip contient un `payload.bin` ou un `super.img`, l'outil déballe d'abord le conteneur, puis analyse intelligemment chaque partition contenue.

### Étape 2 : Création systématique des métadonnées dans `BUILD/data/` (`MetadataKeeper.kt`)
Lors de chaque déballage, créer dans le dossier du projet `BUILD/data/` :
* `image_info.json` :
  - `original_filename`, `file_format`, `original_size_bytes`, `sha256_hash`
  - `block_size` (ex: 4096), `total_blocks`, `inode_size` (ex: 256), `inodes_count`
  - `volume_uuid`, `volume_name`, `feature_compat_flags`
  - `is_modified` (false par défaut, passe à true si l'utilisateur modifie des fichiers)
* `fs_config.txt` :
  - Format identique à UKA et aux builds AOSP :
    `[chemin_relatif] [uid] [gid] [mode_octal] [capabilities/symlink_target]`
* `file_contexts` :
  - Enregistrement des contextes SELinux extraits des xattrs (attributs étendus) ou du dictionnaire SELinux.
* `symlinks.json` :
  - Répertoire complet des liens symboliques (nom du lien -> cible relative ou absolue).
* `original_image.bin` (ou référence d'accès direct sur le cache) :
  - Sauvegarde intègre de l'image source pour assurer le repack 1:1 bit-à-bit parfait.

### Étape 3 : Parseur structurel EXT4 & EROFS sans faux positifs (`Ext4StructuralParser.kt`)
* Lecture rigoureuse des structures réelles :
  1. **Superblock** : Extraction de `s_inodes_per_group`, `s_blocks_per_group`, `s_desc_size` (gestion 32-bit et 64-bit EXT4), `s_first_data_block`.
  2. **Block Group Descriptors** : Calcul des adresses de la table d'inodes (`bg_inode_table_lo` / `bg_inode_table_hi`).
  3. **Traversée d'arborescence à partir de l'Inode 2 (Root)** :
     - Lecture de la structure `ext4_inode` (mode, uid, taille, flags).
     - Résolution des blocs via le Header d'Extents (`0xF30A` / `ext4_extent_header`, `ext4_extent_idx`, `ext4_extent`).
     - Décodage des entrées de répertoire (`ext4_dir_entry_2` : inode, rec_len, name_len, file_type, name).
     - Parcours récursif des sous-répertoires réels.
     - Prise en charge des symlinks inodaux (fast symlinks stockés directement dans `i_block` si taille < 60 octets) et symlinks par extents.
* **Résultat garanti** : Aucun faux dossier `anim`, `drawable` ou `xml` car l'extraction suit exclusivement les pointeurs de la table d'inodes du système de fichiers.

### Étape 4 : Repacker intelligent 100% conforme ZArchiver (`UniversalRepacker.kt`)
* **Mode 1 : Repack 1:1 Sans Modification (Zéro altération)** :
  - Vérification de l'intégrité via `image_info.json`.
  - Si aucune modification n'a été apportée aux fichiers extraits, réémission immédiate de l'image originale exacte avec le hash SHA-256 d'origine.
  - Résultat 100% garanti lisible dans ZArchiver et identique à l'octet près.
* **Mode 2 : Repack Structurel EXT4 (En cas de modification)** :
  - Calcul de la taille de partition requise (ou conservation de la taille originale définie dans `image_info.json`).
  - Écriture d'un véritable système de fichiers EXT4 :
    - Bloc 0 réservé (boot code).
    - Superblock à l'offset 1024 (`0x400`) avec les champs obligatoires (`0xEF53`, révisions, blocs libres, inodes libres, montage propre `0x0001`).
    - Descripteurs de groupes de blocs (Group Descriptors).
    - Bitmaps d'allocation de blocs et d'inodes.
    - Table d'Inodes pré-initialisée avec l'Inode 2 (répertoire racine `/`) contenant ses entrées `.` et `..`.
    - Inodes pour chaque sous-répertoire et fichier avec extents (`0xF30A`) pointant vers les blocs de données réels.
    - Inodes de type symlink (`0xA000` | permissions) avec leurs cibles exactes.
    - Application des permissions et UID/GID issus de `BUILD/data/fs_config.txt`.
  - Si le format original était un Android Sparse (`simg`), conversion du raw EXT4 produit au format Sparse via `img2simg` interne.
* **Validation dans ZArchiver** : L'archive s'ouvre directement avec l'arborescence complète et tous ses fichiers consultables et extractibles sans aucune erreur.

### Étape 5 : Interface UI intelligente avec statut d'intégrité et modes de repack
* Affichage clair dans le tableau de bord OSbuilder :
  - Format détecté avec badge (EXT4 Raw, Android Sparse, EROFS, Payload.bin, etc.).
  - Statut des métadonnées `BUILD/data/` (fs_config sauvegardé, SELinux préservé, X liens symboliques indexés).
  - Statut d'intégrité : `Original (1:1 Bit-Exact)` vs `Modifié`.
  - Options de repack : `Repack 1:1 fidèle (Recommandé)` ou `Repack personnalisé (Format, Taille, Sparse)`.

---

## 4. Fichiers touchés et créés

* `app/src/main/java/com/example/core/tools/kitchen/FormatDetector.kt` : Détection multi-formats intelligente.
* `app/src/main/java/com/example/core/tools/kitchen/Ext4StructuralParser.kt` : Parseur structurel officiel EXT4 (Superblock, Group Descriptors, Inode Tables, Extents, Directory Entries, Symlinks).
* `app/src/main/java/com/example/core/tools/kitchen/MetadataKeeper.kt` : Gestionnaire de sauvegarde/restauration de `fs_config`, `file_contexts` et `image_info.json` dans `BUILD/data/`.
* `app/src/main/java/com/example/core/tools/kitchen/UniversalRepacker.kt` : Moteur de repack 1:1 bit-exact et reconstructeur de filesystem EXT4/Sparse conforme ZArchiver.
* `app/src/main/java/com/example/core/tools/ImageToolchain.kt` : Orchestration complète du pipeline Unpack -> Indexation Métadonnées -> Modifications -> Repack.
* `app/src/main/java/com/example/presentation/screens/WorkspaceScreen.kt` & `ProjectDetailScreen.kt` : Affichage de l'état des métadonnées UKA et des options de repack.

---

## 5. Critères de validation et de succès

1. **Unpack propre sans faux dossiers** :
   - L'extraction d'une image Android extrait rigoureusement l'arborescence réelle (`bin`, `etc`, `lib`, `framework`, `app`, etc.) sans jamais générer de dossiers artificiels `anim`, `drawable`, `xml` à la racine.
2. **Métadonnées enregistrées dans `BUILD/data/`** :
   - Présence effective de `fs_config.txt`, `file_contexts` et `image_info.json`.
3. **Compatibilité ZArchiver & Outils standards** :
   - L'image repaquée s'ouvre instantanément dans ZArchiver, affichant l'arborescence complète et le contenu exact de chaque fichier.
4. **Fidélité 100% en l'absence de modification** :
   - Un repack immédiat sans modification produit un fichier strictement identique à l'original (vérifié par SHA-256).
