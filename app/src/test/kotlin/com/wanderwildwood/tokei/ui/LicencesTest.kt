package com.wanderwildwood.tokei.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicencesTest {
    @Test fun everyLibraryHasAnHttpsLinkAndALicence() {
        assertTrue(LIBRARIES.isNotEmpty())
        LIBRARIES.forEach {
            assertTrue(it.name, it.link.startsWith("https://"))
            assertTrue(it.name, it.licence.isNotBlank())
        }
    }
    @Test fun sourceLinkIsThisRepo() {
        assertTrue(SOURCE_URL.startsWith("https://github.com/wanderwildwood/"))
    }
    @Test fun paragraphsKeepEveryWord() {
        assertEquals(listOf("a b", "c"), licenceParagraphs("a\nb\n\n\nc\n"))
    }
}
