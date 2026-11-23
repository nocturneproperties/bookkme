package com.bookk.server

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

internal class UnreleasedMigrationsTest {

    private val projectDirectory = File(".").canonicalFile
    private val migrationRoots = listOf("core", "library", "service")
    private val migrationPath = "src/main/resources/db/migration"

    @Test
    fun `should keep at most one unreleased migration per schema`() = runUnitTest {
        given()
        val release = latestRelease()
        assumeTrue(release != null, "No monolith/<version> tag in this checkout, nothing to compare against")

        whenn()
        val unreleased = schemaDirectories().associate { directory ->
            relativePathOf(directory) to scriptsIn(directory) - releasedScriptsIn(requireNotNull(release), directory)
        }

        then()
        val schemasWithSeveral = unreleased.filterValues { it.size > 1 }
        assertTrue(
            schemasWithSeveral.isEmpty(),
            "Each schema gets one migration per release: append to the unreleased script instead of adding " +
                "another, merge these and re-apply them on dev (docs/agents/migrations-and-diagrams.md). " +
                "Unreleased since $release: $schemasWithSeveral",
        )
    }

    private fun latestRelease(): String? {
        return git("tag", "--list", "monolith/*", "--sort=-v:refname")?.lineSequence()?.firstOrNull { it.isNotBlank() }
    }

    private fun schemaDirectories(): List<File> {
        return migrationRoots.map { File(projectDirectory, it) }
            .flatMap { root -> root.walkTopDown().onEnter { it.name != "build" }.toList() }
            .filter { it.isDirectory && it.parentFile.invariantPath().endsWith(migrationPath) }
    }

    private fun scriptsIn(directory: File): Set<String> {
        return directory.listFiles { file -> file.extension == "sql" }.orEmpty().map { it.name }.toSet()
    }

    private fun releasedScriptsIn(release: String, directory: File): Set<String> {
        val listing = git("ls-tree", "--name-only", "$release:${relativePathOf(directory)}")
        return listing?.lines()?.filter { it.endsWith(".sql") }?.toSet().orEmpty()
    }

    private fun relativePathOf(directory: File): String {
        return directory.relativeTo(projectDirectory).invariantPath()
    }

    private fun File.invariantPath(): String {
        return path.replace(File.separatorChar, '/')
    }

    private fun git(vararg arguments: String): String? {
        val process = ProcessBuilder(listOf("git") + arguments)
            .directory(projectDirectory)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        return output.takeIf { process.waitFor() == 0 }
    }
}
