# OSbuilder — Extraction Profonde EXT4/EROFS & Gestionnaire de Tâches d'Arrière-Plan Persistant

Ce plan résout les limites constatées lors du déballage de l'image GSI (3.12 Go) en déployant un véritable extracteur de système de fichiers EXT4/EROFS (arborescence complète, dossiers système, analyse des odex/vdex et contextes SELinux), un gestionnaire d'opérations d'arrière-plan autonome (`TaskManager`) indépendant du cycle de vie des écrans Compose, un suivi visuel en temps réel (pastille dynamique dans la barre latérale Ubuntu Touch et barre flottante sur le bureau), et une journalisation continue dans la console.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Décisions validées & solutions techniques :
> - **Extraction Profonde EXT4 & EROFS (Arborescence Complète)** :
>   - Remplacement de la simple copie binaire par un moteur d'extraction récursif de système de fichiers.
>   - Déballage dans `/sdcard/BUILD/UNPACK/<nom>/system/` de l'ensemble des répertoires vitaux : `app/`, `priv-app/`, `framework/`, `bin/`, `etc/`, `build.prop`, `fonts/`, `lib/`, `lib64/`.
>   - **Analyseur automatique odex / vdex** : Détection et indexation des binaires précompilés Android pour le portage de ROM.
>   - Génération d'un inventaire complet `structure.json` et mise à jour du manifeste `manifest.json`.
> - **Gestionnaire de Tâches d'Arrière-Plan Persistant (`TaskManager`)** :
>   - Les opérations de déballage et de réassemblage sont désormais pilotées par un objet singleton au niveau applicatif (`TaskManager`), alimenté par des `StateFlow` réactifs.
>   - Minimiser une fonctionnalité ou naviguer entre le Bureau, la Console et le Gestionnaire de fichiers **n'interrompt jamais la tâche en cours**.
>   - En revenant dans Unpack-Repack, l'interface retrouve instantanément son état exact, son pourcentage et son historique.
> - **Visualisation Flottante Minimisée & Pastille Latérale** :
>   - **Pastille dynamique dans la barre latérale Ubuntu Touch** : L'icône Tâche et le bouton de l'outil affichent un anneau de progression animé et le pourcentage en direct.
>   - **Mini-carte de progression flottante sur le Bureau** : Lorsque la tâche tourne en tâche de fond, un widget moderne flottant au bas de l'écran affiche le débit (Mo/s), l'étape en cours, une barre de progression fluide et un bouton pour rouvrir l'outil d'un simple toucher.
> - **Journalisation Continue & Détaillée dans le Terminal** :
>   - Le terminal reçoit désormais un flux continu et transparent (nom de chaque dossier et fichier décompressé, blocs lus, débit en Mo/s, détection d'en-têtes et avertissements), éliminant les temps morts et les sensations de blocage.

---

## 1. Moteur d'Extraction Profonde EXT4 & EROFS

- **Analyseur de superbloc et descripteurs de blocs** :
  - Détection fine de l'en-tête EXT4 (`0xEF53`) ou EROFS (`0xE0F5E1E2`).
  - Moteur hybride : Si root `su` est disponible, tentative de montage loop sécurisé ou extraction `debugfs/7z` ; sinon, parseur de structures d'inodes et répertoires Android.
  - Décomposition des partitions système et GSI :
    - `build.prop` (extraction et affichage des propriétés : `ro.build.version.release`, `ro.product.model`, `ro.system.build.version.incremental`, architecture `arm64-v8a`)
    - Arborescences `system/app/`, `system/priv-app/`, `system/framework/`
    - Recensement et typage des fichiers `.odex`, `.vdex`, `.art`, et vérification des signatures `fsvmeta`.
- **Rapports enrichis dans `Data/projects/<projet>/`** :
  - `manifest.json` : Caractéristiques du système de fichiers, statut de l'analyse.
  - `structure.json` : Arbre JSON complet de l'image déballée.
  - `report.txt` : Compte-rendu lisible avec résumé du portage, version d'Android et packages système découverts.

---

## 2. Architecture du Gestionnaire de Tâches `TaskManager`

```
┌────────────────────────────────────────────────────────────────────────┐
│                        OSbuilder UI Layer                              │
│  ┌───────────────────────┐  ┌────────────────────────────────────────┐ │
│  │   Ubuntu Touch Rail   │  │   Desktop Floating Task Pill           │ │
│  │  - Animated Ring (% ) │  │   [ 42% Unpack: system/app/... (28Mo/s)]│ │
│  └───────────────────────┘  └────────────────────────────────────────┘ │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ StateFlow Observation
┌────────────────────────────────────▼───────────────────────────────────┐
│                      Application TaskManager (Singleton)               │
│  - activeTask: StateFlow<ActiveTaskState?>                             │
│  - taskHistory: List<CompletedTask>                                    │
│  - cancelTask(), pauseTask(), resumeTask()                             │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ Execution Scope (Non-cancellable IO)
┌────────────────────────────────────▼───────────────────────────────────┐
│              ImageToolchain (Deep Ext4/Erofs Extractor)                │
│  - Streamed extraction -> ProcessTerminal (Real-time logs)             │
│  - Progress updates -> TaskManager StateFlow                           │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Plan de Modifications

1. **`com.example.core.execution.TaskManager.kt`** :
   - Création du singleton de gestion des tâches d'arrière-plan avec `ActiveTaskState`, progression de 0 à 100%, débit calculé et titre d'étape.
2. **`com.example.core.tools.ImageToolchain.kt`** :
   - Implémentation du déballage récursif réel pour les images `RAW_EXT4` et `EROFS` (extraction de l'arborescence, `build.prop`, `app/`, `priv-app/`, détection odex/vdex).
   - Flux de logs continus vers `ProcessTerminal` pour chaque paquet/dossier traité.
3. **`com.example.ui.screens.UnpackRepackScreen.kt`** :
   - Connexion de l'UI directement au `TaskManager` (l'état persiste lors des navigations).
4. **`com.example.ui.OSShell.kt`** :
   - Ajout de la carte de progression flottante sur le Bureau quand une tâche tourne en tâche de fond.
   - Restauration automatique de l'outil lors du clic sur la carte flottante.
5. **`com.example.ui.components.UbuntuTouchRail.kt`** :
   - Pastille dynamique avec indicateur de progression circulaire sur le bouton Tâche.
6. **Compilation & Validation** :
   - Vérification avec `compile_applet`.
