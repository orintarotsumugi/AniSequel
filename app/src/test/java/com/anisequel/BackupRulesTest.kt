package com.anisequel

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup rules have to be *valid*, not merely present.
 *
 * ## What this caught
 *
 * Both rule files excluded the widget store with
 *
 * ```xml
 * <exclude domain="shared_pref" path="anisequel_widget_cache.xml" />
 * ```
 *
 * `shared_pref` is not a domain. The platform's domains are `root`, `file`,
 * `database`, `sharedpref`, `external` and their `device_*` counterparts, and it
 * spells this one **without** an underscore. An unrecognised domain is not a
 * warning at runtime - the whole `<exclude>` is discarded - so the file that
 * exists to keep the signed-in handle, the viewer's counts and the titles they
 * are watching off a backup server was itself excluded from working. Every file
 * in the repository read as though the rule were in place.
 *
 * `backup_rules.xml` (Android 11 and below in shape) and
 * `data_extraction_rules.xml` (Android 12+) are two separate documents with the
 * same contract, which is why the check runs over both: the typo was in both,
 * and fixing one would have left the other silent.
 *
 * ## Why this is a source-level test
 *
 * The rules are read by `BackupManagerService`, not by anything this app can
 * call, so no unit test can observe the exclusion happening. What it *can* pin
 * is the pair of facts the file was written for: every domain is a legal one,
 * and the two paths the KDoc names are actually excluded.
 */
class BackupRulesTest {

    private val root: File = findProjectRoot()

    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }

    private val rulesFiles = listOf(
        "app/src/main/res/xml/backup_rules.xml",
        "app/src/main/res/xml/data_extraction_rules.xml"
    )

    /** The domains the platform accepts; see the `include`/`exclude` docs. */
    private val legalDomains = setOf(
        "root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref", "device_external"
    )

    private fun sourceOf(path: String): String {
        val file = File(root, path)
        assertTrue(
            "$path is missing; backup and device-transfer rules are both required " +
                "by the manifest, and a guard that silently passes on a missing file " +
                "is the failure it exists to prevent",
            file.isFile
        )
        return file.readText()
    }

    /** Every `domain="…"` attribute value in one rules document. */
    private fun domainsIn(source: String): List<String> =
        Regex("""domain\s*=\s*"([^"]*)"""")
            .findAll(source)
            .map { it.groupValues[1] }
            .toList()

    @Test
    fun `every backup rule names a domain the platform accepts`() {
        rulesFiles.forEach { path ->
            val domains = domainsIn(sourceOf(path))
            assertTrue("$path declares no domains at all", domains.isNotEmpty())
            domains.forEach { domain ->
                assertTrue(
                    "$path uses domain=\"$domain\", which the platform does not recognise - " +
                        "the whole element is discarded, so the exclusion it appears to " +
                        "declare does not happen. Legal domains are $legalDomains.",
                    domain in legalDomains
                )
            }
        }
    }

    /**
     * The two things the rules exist to keep off a backup: the DataStore file the
     * OAuth token lives in, and the widget store's cached handle and counts.
     *
     * The counts are per document shape rather than "at least one":
     * `backup_rules.xml` has a single `full-backup-content` section, while
     * `data_extraction_rules.xml` states its rules twice - once for cloud backup and
     * once for device-to-device transfer - and a rule that covers only one of those
     * is the same leak through the other door.
     */
    @Test
    fun `the session token and the widget store are both excluded`() {
        val expectations = listOf(
            Triple("app/src/main/res/xml/backup_rules.xml", "datastore/", 1),
            Triple("app/src/main/res/xml/backup_rules.xml", "anisequel_widget_cache.xml", 1),
            Triple("app/src/main/res/xml/data_extraction_rules.xml", "datastore/", 2),
            Triple("app/src/main/res/xml/data_extraction_rules.xml", "anisequel_widget_cache.xml", 2)
        )
        expectations.forEach { (path, excluded, expected) ->
            val source = sourceOf(path)
            assertEquals(
                "$path must declare `$excluded` $expected time(s); a missing or " +
                    "extra rule reads as protection that is not there",
                expected,
                Regex("""path\s*=\s*"${Regex.escape(excluded)}"""").findAll(source).count()
            )
        }
    }
}
