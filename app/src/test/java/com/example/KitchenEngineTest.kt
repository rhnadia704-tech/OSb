package com.example

import com.example.core.execution.LogLevel
import com.example.core.tools.kitchen.Ext4Generator
import com.example.core.tools.kitchen.Ext4StructuralParser
import com.example.core.tools.kitchen.ExtractedNodeInfo
import com.example.core.tools.kitchen.SparseConverter
import com.example.core.tools.kitchen.SymlinkEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class KitchenEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testExt4GenerationAndStructuralParsing() = runBlocking {
        // 1. Créer une fausse arborescence de partition Android
        val sourceDir = tempFolder.newFolder("system_root")
        val binDir = File(sourceDir, "bin").apply { mkdirs() }
        val etcDir = File(sourceDir, "etc").apply { mkdirs() }

        val shFile = File(binDir, "sh")
        shFile.writeText("#!/system/bin/sh\necho 'OSbuilder Android Kitchen'\n")

        val buildPropFile = File(sourceDir, "build.prop")
        buildPropFile.writeText("ro.build.version.release=14\nro.product.model=OSbuilder_GSI\n")

        val hostsFile = File(etcDir, "hosts")
        hostsFile.writeText("127.0.0.1 localhost\n")

        val symlinks = listOf(
            SymlinkEntry(path = "bin/toolbox", target = "toybox"),
            SymlinkEntry(path = "etc/resolv.conf", target = "/data/misc/resolv.conf")
        )

        val outputImg = File(tempFolder.root, "test_system.img")
        val partitionSize = 8L * 1024L * 1024L // 8 Mo

        // 2. Générer l'image EXT4 formelle
        val genOk = Ext4Generator.generateExt4Image(
            sourceDirectory = sourceDir,
            outputImageFile = outputImg,
            targetSizeBytes = partitionSize,
            symlinks = symlinks,
            fsConfigNodes = emptyList(),
            onProgress = { _, _ -> },
            onLog = { _, _ -> }
        )
        assertTrue("Ext4Generator doit réussir", genOk)
        assertEquals(partitionSize, outputImg.length())

        // 3. Vérifier le Superbloc EXT4 (Magic 0xEF53 à l'offset 1080)
        RandomAccessFile(outputImg, "r").use { raf ->
            raf.seek(1080)
            val magicBuf = ByteArray(2)
            raf.readFully(magicBuf)
            val magic = ByteBuffer.wrap(magicBuf).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
            assertEquals("Le magic EXT4 doit être 0xEF53", 0xEF53, magic)
        }

        // 4. Vérifier que Ext4StructuralParser lit l'image générée sans aucun faux dossier (anim, drawable, xml)
        val extractedDir = tempFolder.newFolder("extracted_out")
        val parseResult = Ext4StructuralParser.parseAndExtract(
            context = null,
            rawFile = outputImg,
            targetDir = extractedDir,
            partitionName = "system",
            onProgress = { _, _, _, _ -> },
            onLog = { _, _ -> }
        )

        assertNotNull("Ext4StructuralParser doit réussir à parser l'image EXT4", parseResult)
        assertTrue("L'arborescence extraite doit contenir des fichiers", parseResult!!.totalFiles >= 3)
        assertTrue("L'arborescence extraite doit contenir des dossiers", parseResult.totalDirectories >= 2)
        assertTrue("L'arborescence extraite doit restaurer les symlinks", parseResult.totalSymlinks >= 1)

        // Vérifier qu'aucun faux dossier n'a été créé
        val animDir = File(extractedDir, "anim")
        val drawableDir = File(extractedDir, "drawable")
        val xmlDir = File(extractedDir, "xml")
        assertTrue("Aucun faux dossier anim ne doit exister", !animDir.exists())
        assertTrue("Aucun faux dossier drawable ne doit exister", !drawableDir.exists())
        assertTrue("Aucun faux dossier xml ne doit exister", !xmlDir.exists())
    }

    @Test
    fun testSparseImageCompressionAndDecompression() = runBlocking {
        val rawFile = File(tempFolder.root, "test_raw.img")
        val sparseFile = File(tempFolder.root, "test_sparse.img")
        val restoredRawFile = File(tempFolder.root, "test_restored.img")

        // Créer une image raw de 64 Ko avec quelques données et du vide
        val data = ByteArray(64 * 1024)
        for (i in 0 until 4096) data[i] = (i % 250).toByte()
        rawFile.writeBytes(data)

        // Convertir raw -> sparse
        val toSparseOk = SparseConverter.rawToSparse(
            rawFile = rawFile,
            outputSparseFile = sparseFile,
            blockSize = 4096,
            onProgress = { _, _ -> },
            onLog = { _, _ -> }
        )
        assertTrue("rawToSparse doit réussir", toSparseOk)
        assertTrue("isSparse doit détecter la sparse image", SparseConverter.isSparse(sparseFile))

        // Convertir sparse -> raw
        val toRawOk = SparseConverter.sparseToRaw(
            sparseFile = sparseFile,
            outputRawFile = restoredRawFile,
            onProgress = { _, _ -> },
            onLog = { _, _ -> }
        )
        assertTrue("sparseToRaw doit réussir", toRawOk)
        assertEquals("La taille restaurée doit être identique", rawFile.length(), restoredRawFile.length())
    }
}
