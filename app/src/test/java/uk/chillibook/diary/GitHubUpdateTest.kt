package uk.chillibook.diary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class GitHubUpdateTest {
    @Test fun compareNumericReleaseTags() {
        assertTrue(GitHubUpdate.newer("1.10", "1.9.2"))
        assertTrue(GitHubUpdate.newer("1.2.1", "1.2"))
        assertFalse(GitHubUpdate.newer("1.2.0", "1.2"))
        assertFalse(GitHubUpdate.newer("1.2", "2.0"))
    }

    @Test fun rejectDifferentOrMissingReleaseDigest() {
        val hash = MessageDigest.getInstance("SHA-256").digest("abc".toByteArray())
        val digest = "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertTrue(GitHubUpdate.matchesDigest(hash, digest))
        assertFalse(GitHubUpdate.matchesDigest(hash, digest.dropLast(1) + "0"))
        assertFalse(GitHubUpdate.matchesDigest(hash, ""))
    }
}
