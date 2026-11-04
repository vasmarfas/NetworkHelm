package com.vasmarfas.networkhelm.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReleasesTest {

    private fun entry(tag: String, draft: Boolean = false, prerelease: Boolean = false, url: String = "https://github.com/example/app/releases/tag/$tag") =
        """{"tag_name":"$tag","html_url":"$url","draft":$draft,"prerelease":$prerelease,"name":"x","assets":[]}"""

    private fun listing(vararg entries: String) = entries.joinToString(prefix = "[", postfix = "]")

    @Test
    fun aNewerStableReleaseIsOffered() {
        val text = listing(entry("v1.2.0"), entry("v1.1.0"), entry("v1.0.0"))
        val release = assertNotNull(newerRelease(text, "1.1.0"))
        assertEquals("1.2.0", release.version)
        assertEquals("https://github.com/example/app/releases/tag/v1.2.0", release.url)
    }

    @Test
    fun theRunningVersionOrAnOlderOneOffersNothing() {
        val text = listing(entry("v1.0.0"), entry("v0.9.4"))
        assertNull(newerRelease(text, "1.0.0"))
        assertNull(newerRelease(text, "1.0.1"))
    }

    @Test
    fun aReleaseCandidateIsOlderThanItsRelease() {
        val text = listing(entry("v1.0.0"), entry("v1.0.0-rc2", prerelease = true))
        assertEquals("1.0.0", newerRelease(text, "1.0.0-rc1")?.version)
        assertEquals("1.0.0", newerRelease(text, "1.0.0-rc2")?.version)
    }

    @Test
    fun candidatesAreComparedByTheirNumber() {
        val text = listing(entry("v1.0.0-rc10", prerelease = true), entry("v1.0.0-rc9", prerelease = true))
        assertEquals("1.0.0-rc10", newerRelease(text, "1.0.0-rc2")?.version)
        assertNull(newerRelease(text, "1.0.0-rc10"))
    }

    @Test
    fun aStableBuildIsNotOfferedPrereleasesOrDrafts() {
        val text = listing(entry("v1.1.0-rc1", prerelease = true), entry("v1.2.0", draft = true), entry("v1.0.0"))
        assertNull(newerRelease(text, "1.0.0"))
        assertEquals("1.1.0-rc1", newerRelease(text, "1.0.0-rc3")?.version)
    }

    @Test
    fun aLinkOutsideGitHubIsDropped() {
        val text = listing(entry("v2.0.0", url = "https://example.com/get"), entry("v1.5.0"))
        assertEquals("1.5.0", newerRelease(text, "1.0.0")?.version)
    }

    @Test
    fun anAnswerThatIsNotTheListingOffersNothing() {
        assertNull(newerRelease("""{"message":"API rate limit exceeded"}""", "1.0.0"))
        assertNull(newerRelease("", "1.0.0"))
        assertNull(newerRelease("<html>", "1.0.0"))
        assertNull(newerRelease(listing(entry("nightly")), "1.0.0"))
    }

    @Test
    fun aVersionTheAppCannotReadOffersNothing() {
        assertNull(newerRelease(listing(entry("v1.0.0")), "dev"))
    }
}
