package uk.chillibook.diary

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.URL
import java.security.DigestOutputStream
import java.security.MessageDigest
import android.system.Os
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.net.ssl.HttpsURLConnection

internal data class UpdateRelease(val version: String, val size: Long, val digest: String)

class UpdateFileProvider : FileProvider()

internal object GitHubUpdate {
    private const val MAX_APK = 128L * 1024 * 1024
    private val downloadLock = Mutex()
    private val digestPattern = Regex("sha256:[0-9a-f]{64}")
    private val versionPattern = Regex("""[0-9]{1,9}\.[0-9]{1,9}(\.[0-9]{1,9})?""")
    private val downloadHosts = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")

    internal fun newer(candidate: String, installed: String): Boolean {
        fun parts(version: String): List<Int> {
            require(version.matches(versionPattern))
            return version.split('.').map(String::toInt)
        }
        val a = parts(candidate)
        val b = parts(installed)
        for (i in 0..2) {
            val cmp = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (cmp != 0) return cmp > 0
        }
        return false
    }

    suspend fun check(): UpdateRelease? = withContext(Dispatchers.IO) {
        val repo = BuildConfig.UPDATE_REPOSITORY
        require(repo.matches(Regex("""[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+""")))
        val bytes = ByteArrayOutputStream()
        fetch("https://api.github.com/repos/$repo/releases/latest", setOf("api.github.com"), 1024L * 1024, bytes)
        val release = JSONObject(bytes.toString("UTF-8"))
        require(!release.getBoolean("draft") && !release.getBoolean("prerelease"))
        val tag = release.getString("tag_name")
        require(tag.startsWith("v"))
        val version = tag.substring(1)
        if (!newer(version, BuildConfig.VERSION_NAME)) return@withContext null
        val assets = release.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .single { it.getString("name") == "ChilliBook-$version.apk" }
        val size = apk.getLong("size")
        require(apk.getString("state") == "uploaded" && size in 1..MAX_APK)
        val digest = apk.getString("digest")
        require(digest.matches(digestPattern))
        UpdateRelease(version, size, digest)
    }

    suspend fun download(context: Context, release: UpdateRelease): Intent = withContext(Dispatchers.IO) {
        downloadLock.withLock { prepareInstaller(context, release) }
    }

    private suspend fun prepareInstaller(context: Context, release: UpdateRelease): Intent {
        require(release.size in 1..MAX_APK && newer(release.version, BuildConfig.VERSION_NAME))
        require(release.digest.matches(digestPattern))
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(dir, "candidate.apk")
        val ready = File(dir, "${release.digest.removePrefix("sha256:")}.apk")
        try {
            val url = "https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/download/v${release.version}/ChilliBook-${release.version}.apk"
            val hash = MessageDigest.getInstance("SHA-256")
            val count = DigestOutputStream(apk.outputStream(), hash).use { fetch(url, downloadHosts, release.size, it) }
            require(count == release.size && matchesDigest(hash.digest(), release.digest))
            verify(context, apk, release.version)
            Os.rename(apk.absolutePath, ready.absolutePath)
            dir.listFiles()?.filter { it != ready && it.extension == "apk" }?.forEach { it.delete() }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", ready)
            return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } finally {
            apk.delete()
        }
    }

    internal fun matchesDigest(bytes: ByteArray, digest: String): Boolean =
        "sha256:" + bytes.joinToString("") { "%02x".format(it) } == digest

    @Suppress("DEPRECATION")
    private fun verify(context: Context, apk: File, version: String) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = requireNotNull(pm.getPackageArchiveInfo(apk.absolutePath, flags))
        val installed = pm.getPackageInfo(context.packageName, flags)
        require(candidate.packageName == installed.packageName && candidate.versionName == version)
        require(PackageInfoCompat.getLongVersionCode(candidate) > PackageInfoCompat.getLongVersionCode(installed))
        fun signers(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners.orEmpty().toSet()
        } else info.signatures.orEmpty().toSet()
        val expected = signers(installed)
        require(expected.isNotEmpty() && expected == signers(candidate))
    }

    private suspend fun fetch(start: String, hosts: Set<String>, limit: Long, out: OutputStream): Long {
        var url = URL(start)
        repeat(6) {
            currentCoroutineContext().ensureActive()
            require(url.protocol == "https" && url.host in hosts && url.port in setOf(-1, 443) && url.userInfo == null)
            val conn = url.openConnection() as HttpsURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "ChilliBook")
                when (conn.responseCode) {
                    301, 302, 303, 307, 308 -> url = URL(url, requireNotNull(conn.getHeaderField("Location")))
                    200 -> {
                        require(conn.contentLengthLong <= limit)
                        conn.inputStream.use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var total = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) return total
                                total += count
                                require(total <= limit)
                                out.write(buffer, 0, count)
                            }
                        }
                    }
                    else -> throw IOException("GitHub returned HTTP ${conn.responseCode}")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }
}
