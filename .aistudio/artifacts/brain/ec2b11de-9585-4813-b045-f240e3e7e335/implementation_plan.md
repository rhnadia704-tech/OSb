# OSbuilder — Gestionnaire de Fichiers Intelligent & Bureau Plein Écran

Ce plan intègre la suppression de la barre d'état supérieure pour une immersion plein écran totale, l'indicateur bidirectionnel (languette inversée) pour déployer et replier la barre latérale, et la refonte du gestionnaire de fichiers en un panneau unique intelligent avec détection automatique des actions métier selon le type de fichier.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Évolutions majeures confirmées :
> - **Suppression de la barre d'état supérieure** : L'écran devient un véritable affichage plein écran pur avec le fond d'écran immersif et le grand widget horloge/date sans bandeaux parasites.
> - **Languette tactile bidirectionnelle** :
>   - Quand la barre latérale est repliée : la languette sur le bord gauche affiche `>` pour l'ouvrir d'un geste.
>   - Quand la barre latérale est déployée : la languette se place sur le flanc droit de la barre et s'inverse avec `<` pour la refermer instantanément d'un simple clic ou glissement.
> - **Gestionnaire de fichiers mono-panneau intelligent (Smart Contextual Actions)** :
>   - Interface simplifiée, ultra-fluide à un seul volet avec barre de chemin/dossier parent.
>   - Déclenchement au clic / appui long d'un panneau d'actions contextuelles sur-mesure :
>     - **Actions standard** : Renommer, Modifier/Éditer, Supprimer, Copier, Couper, Coller (si élément en mémoire), Propriétés & Hashs.
>     - **Actions intelligentes automatiques selon le type** :
>       - Fichier `.img`, `.bin`, `.new.dat.br`, `.zip` de ROM : Action prioritaire **"Déballer avec Unpack-Repack"** (ouvre directement l'outil avec ce fichier pré-chargé).
>       - Dossier situé dans `/sdcard/BUILD/UNPACK/` : Action **"Repacker cette image"** (ouvre Repack avec ce projet pré-sélectionné).
>       - Fichier `.sh` : Action **"Exécuter dans la Console"**.
>       - Fichier `.html` ou `.zip` d'application : Action **"Installer comme WebApp dans Apps/"**.
>       - Fichier `.gguf` : Action **"Enregistrer comme Cerveau IA"**.

---

## 1. Bureau Plein Écran Épuré

- Suppression de la barre d'état haute de 44.dp.
- Le bureau occupe 100% de la surface d'affichage avec le fond d'écran dynamique choisi dans les Paramètres.
- Grand widget heure et date centré.
- Grille d'icônes applications pures.

---

## 2. Barre Latérale Rétractable avec Languette Réversible

- Languette tactile ancrée sur la bordure de la barre latérale.
- État fermé : pastille discrète avec chevron droit `>`.
- État ouvert : pastille solidaire de la barre avec chevron gauche `<` pour rétracter la barre en douceur.

---

## 3. Gestionnaire de Fichiers Intelligent Mono-Panneau

- Vue épurée à une seule liste haute performance.
- Presse-papier intégré pour les opérations Copier / Couper / Coller dans n'importe quel dossier de destination.
- Menu d'actions contextuelles intelligent en feuille modale :
  - Identifie la nature du fichier et propose la fonctionnalité exacte d'OSbuilder (Unpack, Repack, Console, Install App, Enregistrer IA).
  - Navigation fluide dans les sous-dossiers.

---

## 4. Plan de Modifications

1. **`OSShell.kt`** :
   - Suppression du composant de barre d'état supérieure.
   - Intégration de la languette tactile réversible (`>` pour ouvrir, `<` pour fermer).
   - Liaison directe avec les actions intelligentes du gestionnaire de fichiers pour lancer Unpack avec le fichier sélectionné ou Repack avec le dossier sélectionné.
2. **`FileManagerScreen.kt`** :
   - Refonte en mono-panneau fluide.
   - Ajout du presse-papier (Copier/Couper/Coller).
   - Intégration de la boîte de dialogue intelligente proposant les actions spécifiques selon l'extension (`.img` -> Déballer, `.sh` -> Exécuter, etc.).
3. **Compilation & Validation** :
   - Vérification avec `compile_applet`.
