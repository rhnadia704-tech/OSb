package com.example.core.storage

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfManualGenerator {

    suspend fun generateManualPdf(context: Context): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()

        val pageWidth = 595 // A4 standard width in points
        val pageHeight = 842 // A4 standard height in points

        val titlePaint = Paint().apply {
            color = Color.rgb(11, 87, 208) // OSb Blue
            textSize = 22f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subTitlePaint = Paint().apply {
            color = Color.rgb(33, 37, 41)
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val sectionPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            textSize = 10f
            isAntiAlias = true
        }

        val codePaint = Paint().apply {
            color = Color.rgb(15, 118, 110)
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }

        val pageNumberPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 9f
            isAntiAlias = true
        }

        // ================= PAGE 1 : Page de Garde & Philosophie =================
        val pageInfo1 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page1 = pdfDocument.startPage(pageInfo1)
        val canvas1 = page1.canvas

        // Decorative top bar
        val barPaint = Paint().apply { color = Color.rgb(11, 87, 208) }
        canvas1.drawRect(40f, 40f, pageWidth - 40f, 46f, barPaint)

        canvas1.drawText("OSbuilder — SYSTÈME MODULAIRE DE PORTAGE ROM", 40f, 85f, titlePaint)
        canvas1.drawText("Manuel Officiel d'Architecture & Guide d'Utilisation", 40f, 108f, subTitlePaint)
        canvas1.drawText("Version du Système: 1.0 (Mobile Mini-OS) | Racine: /sdcard/BUILD/", 40f, 126f, bodyPaint)

        var y = 160f
        canvas1.drawText("1. INTRODUCTION & PRINCIPE DU MINI-OS", 40f, y, sectionPaint)
        y += 20f
        val introText = listOf(
            "OSbuilder est à la fois une application autonome et un mini-système d'exploitation fonctionnant",
            "à l'intérieur d'Android. Il a été conçu spécifiquement pour accomplir des opérations de portage de haut niveau",
            "(portage GSI, conversion de partitions, analyse odex/vdex, déballage et réassemblage symétrique).",
            "",
            "La force d'OSbuilder réside dans son architecture modulaire à cœur invariable :",
            "• Le socle fondamental de l'OS (Core Engine) reste strictement identique au fil des évolutions.",
            "• Toutes les futures fonctionnalités prennent la forme de modules dynamiques déposés dans /sdcard/BUILD/Modules/",
            "• L'application découvre et initialise automatiquement ces modules dès le démarrage ou la mise à jour,",
            "  enrichissant les fonctionnalités de l'OS sans modification de son code binaire.",
            "",
            "L'espace de travail est unifié sous la racine /sdcard/BUILD/ avec accès complet au stockage interne et",
            "délégation transparente aux privilèges Super-Utilisateur (Root su) lorsque disponibles."
        )
        for (line in introText) {
            canvas1.drawText(line, 40f, y, bodyPaint)
            y += 15f
        }

        y += 15f
        canvas1.drawText("2. ARBORESCENCE DU RÉPERTOIRE /sdcard/BUILD/", 40f, y, sectionPaint)
        y += 20f
        val dirTree = listOf(
            "/sdcard/BUILD/",
            "├── Data/         # Hub central : analyses, manifestes, rapports structurés et lisibles",
            "│   ├── projects/ # Dossier par projet (manifest.json, structure.json, report.txt)",
            "│   ├── reports/  # Historique de tous les traitements et logs exportés",
            "│   └── telemetry/# Statistiques et état du système",
            "├── UNPACK/       # Partitions et ROMs décompressées prêtes pour modification",
            "├── PACKED/       # Images finales reconstruites (repack fidèle au format source)",
            "├── Modules/      # Nouveaux modules dynamiques découverts à chaud",
            "├── Apps/         # Applications Web importées (.html ou archives .zip extraites)",
            "├── Scripts/      # Scripts shell (.sh) exécutables directement dans la console",
            "└── AI/           # Modèles locaux GGUF (ex: Ministral-3-3B-Reasoning-2512-Q4_K_M.gguf)"
        )
        for (line in dirTree) {
            canvas1.drawText(line, 40f, y, codePaint)
            y += 14f
        }

        canvas1.drawText("Page 1 / 3 — OSbuilder Documentation Officielle", 40f, pageHeight - 30f, pageNumberPaint)
        pdfDocument.finishPage(page1)

        // ================= PAGE 2 : Moteur Unpack/Repack & Outils =================
        val pageInfo2 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
        val page2 = pdfDocument.startPage(pageInfo2)
        val canvas2 = page2.canvas

        canvas2.drawRect(40f, 40f, pageWidth - 40f, 46f, barPaint)
        canvas2.drawText("OSbuilder — MOTEUR UNPACK & REPACK EXHAUSTIF", 40f, 85f, titlePaint)
        canvas2.drawText("Chaîne d'outils bas niveau & Reconstitution symétrique", 40f, 108f, subTitlePaint)

        y = 150f
        canvas2.drawText("3. FORMATS PRIS EN CHARGE & ANALYSE D'EN-TÊTES", 40f, y, sectionPaint)
        y += 20f
        val toolsDesc = listOf(
            "Le moteur Unpack/Repack d'OSbuilder intègre l'ensemble des analyseurs bas niveau sans exception :",
            "",
            "1. Android Sparse Image (simg2img / img2simg) :",
            "   Détection du magic number 0xED26FF3A. Analyse les blocs de 4096 octets et convertit",
            "   les chunks (RAW, FILL, DONT_CARE) vers une image raw décompressée.",
            "",
            "2. Dynamic Partitions (super.img) :",
            "   Parse la table géométrique LP Metadata (0x616C7030 / '0pla') et sépare les partitions logiques",
            "   (system, vendor, product, system_ext, odm) pour modification unitaire.",
            "",
            "3. Android OTA Payload (payload.bin) :",
            "   Dépile le manifeste de delta update Android (CrAU) et extrait chaque partition binaire.",
            "",
            "4. Android Boot & Recovery (unpackbootimg / mkbootimg) :",
            "   Scinde les en-têtes v0, v1, v2, v3 et v4 pour isoler le kernel, ramdisk.cpio, dtb et la ligne",
            "   de commande cmdline, puis permet une reconstruction symétrique au bit près.",
            "",
            "5. ROM compressée & archives flashables (.new.dat.br / .transfer.list) :",
            "   Décompresse les flux Brotli et convertit les listes de transfert sparse en image brute ext4/erofs.",
            "",
            "6. Systèmes de fichiers EROFS et EXT4 :",
            "   Inspection intégrale de la structure des fichiers, permissions Linux et contextes SELinux."
        )
        for (line in toolsDesc) {
            canvas2.drawText(line, 40f, y, bodyPaint)
            y += 14.5f
        }

        y += 15f
        canvas2.drawText("4. LE MANIFESTE DE VERROUILLAGE (REPACK AUTOMATIQUE)", 40f, y, sectionPaint)
        y += 20f
        val repackDesc = listOf(
            "Lors de chaque opération d'Unpack, un fichier 'manifest.json' est généré dans Data/projects/<projet>/.",
            "Ce fichier consigne : taille exacte des blocs, format binaire, drapeaux sparse et hash SHA-256.",
            "Lors du Repack, OSbuilder lit ce manifeste pour recréer l'image finale dans PACKED/ sous le format",
            "exactement identique à l'original (aucun paramètre manuel nécessaire)."
        )
        for (line in repackDesc) {
            canvas2.drawText(line, 40f, y, bodyPaint)
            y += 15f
        }

        canvas2.drawText("Page 2 / 3 — OSbuilder Documentation Officielle", 40f, pageHeight - 30f, pageNumberPaint)
        pdfDocument.finishPage(page2)

        // ================= PAGE 3 : Gestionnaire de Fichiers, IA & Scripts =================
        val pageInfo3 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 3).create()
        val page3 = pdfDocument.startPage(pageInfo3)
        val canvas3 = page3.canvas

        canvas3.drawRect(40f, 40f, pageWidth - 40f, 46f, barPaint)
        canvas3.drawText("OSbuilder — FICHIERS, SCRIPTS, WEBAPPS & IA", 40f, 85f, titlePaint)
        canvas3.drawText("Extensions, Terminal système & Cerveau d'analyse", 40f, 108f, subTitlePaint)

        y = 150f
        canvas3.drawText("5. GESTIONNAIRE DE FICHIERS AVANCÉ (MT MANAGER & ZARCHIVER)", 40f, y, sectionPaint)
        y += 20f
        val fmDesc = listOf(
            "• Double volet synchronisé (Volet A & Volet B) pour glisser ou copier rapidement entre répertoires.",
            "• Navigation in-archive sans extraction : ouverture directe dans les archives .zip, .apk, .tar, .jar.",
            "• Éditeur de code intégré avec enregistrement instantané des scripts .sh, fichiers .prop, .json, .xml.",
            "• Calculatrice d'intégrité intégrée : calcul simultané des hashs MD5, SHA-1 et SHA-256."
        )
        for (line in fmDesc) {
            canvas3.drawText(line, 40f, y, bodyPaint)
            y += 15f
        }

        y += 15f
        canvas3.drawText("6. EXPANSION PAR SCRIPTS SHELL & WEBAPPS D'AI STUDIO", 40f, y, sectionPaint)
        y += 20f
        val extDesc = listOf(
            "• Scripts (.sh) : Déposez vos scripts dans /sdcard/BUILD/Scripts/. Ils sont exécutables directement",
            "  depuis la console via 'run <nom_du_script.sh>' ou depuis les Paramètres.",
            "• WebApps : Glissez une archive .zip ou un fichier .html dans /sdcard/BUILD/Apps/. L'application détecte",
            "  automatiquement les dépendances (.js, .css, images) et enregistre la WebApp dans le lanceur."
        )
        for (line in extDesc) {
            canvas3.drawText(line, 40f, y, bodyPaint)
            y += 15f
        }

        y += 15f
        canvas3.drawText("7. INTÉGRATION DU CERVEAU IA (GGUF & GEMINI)", 40f, y, sectionPaint)
        y += 20f
        val aiDesc = listOf(
            "Le dossier /sdcard/BUILD/AI/ héberge les modèles locaux d'intelligence artificielle.",
            "Le modèle cible configuré est : Ministral-3-3B-Reasoning-2512-Q4_K_M.gguf.",
            "Le cerveau assiste l'utilisateur dans l'analyse des bootloops, le contrôle de compatibilité GSI,",
            "et l'audit des fichiers odex/vdex/fsvmeta couplé au mode High Thinking de Gemini."
        )
        for (line in aiDesc) {
            canvas3.drawText(line, 40f, y, bodyPaint)
            y += 15f
        }

        canvas3.drawText("Page 3 / 3 — OSbuilder Documentation Officielle", 40f, pageHeight - 30f, pageNumberPaint)
        pdfDocument.finishPage(page3)

        // Save PDF to /sdcard/BUILD/Data/OSbuilder_Manuel_Officiel.pdf
        val dataDir = WorkspaceManager.getDataDir(context)
        val outFile = File(dataDir, "OSbuilder_Manuel_Officiel.pdf")
        val fos = FileOutputStream(outFile)
        pdfDocument.writeTo(fos)
        fos.close()
        pdfDocument.close()

        outFile
    }
}
