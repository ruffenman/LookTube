package com.looktube.app

import com.looktube.model.DefaultLocalCaptionModel
import com.looktube.model.MoonshineBaseEnglishCaptionModel
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

internal data class VerifiedModelAsset(
    val fileName: String,
    val url: String,
    val sha256: String,
    val expectedBytes: Long? = null,
)

internal val DefaultLocalCaptionModelAsset = VerifiedModelAsset(
    fileName = "ggml-base.en-q5_1.bin",
    url = DefaultLocalCaptionModel.downloadUrl,
    sha256 = "4baf70dd0d7c4247ba2b81fafd9c01005ac77c2f9ef064e00dcf195d0e2fdd2f",
    expectedBytes = 59_721_011L,
)

internal val MoonshineBaseEnglishCaptionModelAssets = listOf(
    VerifiedModelAsset(
        fileName = "decoder_model_merged.ort",
        url = MoonshineBaseEnglishCaptionModel.downloadUrl,
        sha256 = "370e0c1f161d68dc1c2264c1a82024fb123380da3b73f47f05531d1e8d3308c3",
        expectedBytes = 42_703_232L,
    ),
    VerifiedModelAsset(
        fileName = "encoder_model.ort",
        url = "https://huggingface.co/UsefulSensors/moonshine/resolve/48b4e427b587bcf67797a5be706d6ddc4a298149/onnx/merged/base/quantized/encoder_model.ort",
        sha256 = "9d1a19e044cbf89a207723d1adb667de89a5671a1e3e611b8ebe721b2c31219c",
        expectedBytes = 20_661_976L,
    ),
    VerifiedModelAsset(
        fileName = "tokenizer.bin",
        url = "https://huggingface.co/UsefulSensors/moonshine/resolve/48b4e427b587bcf67797a5be706d6ddc4a298149/onnx/merged/base/quantized/tokenizer.bin",
        sha256 = "04670d78994d030185c3dd843c60591788fed0a56cc6750747bd326825f13ca9",
        expectedBytes = 241_639L,
    ),
)

internal fun verifiedModelAssetsTotalBytes(assets: List<VerifiedModelAsset>): Long? =
    assets
        .map(VerifiedModelAsset::expectedBytes)
        .takeIf { lengths -> lengths.all { length -> length != null } }
        ?.sumOf { length -> requireNotNull(length) }

internal fun File.matchesVerifiedModelAsset(asset: VerifiedModelAsset): Boolean {
    if (!exists() || !isFile) {
        return false
    }
    if (asset.expectedBytes != null && length() != asset.expectedBytes) {
        return false
    }
    return runCatching { sha256Hex() == asset.sha256.normalizedSha256() }.getOrDefault(false)
}

internal fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    BufferedInputStream(FileInputStream(this)).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHexString()
}

internal fun downloadVerifiedModelAsset(
    asset: VerifiedModelAsset,
    outputFile: File,
    onChunkDownloaded: (Long) -> Unit = {},
): Long {
    val connection = (URL(asset.url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 60_000
        requestMethod = "GET"
        connect()
    }
    try {
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw IOException("Model download returned HTTP $responseCode.")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var downloadedBytes = 0L
        BufferedInputStream(connection.inputStream).use { input ->
            BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) {
                        break
                    }
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    downloadedBytes += read
                    onChunkDownloaded(read.toLong())
                }
            }
        }
        asset.expectedBytes?.let { expectedBytes ->
            if (downloadedBytes != expectedBytes) {
                throw IOException(
                    "Downloaded ${asset.fileName} had $downloadedBytes bytes, expected $expectedBytes.",
                )
            }
        }
        val actualSha256 = digest.digest().toHexString()
        if (actualSha256 != asset.sha256.normalizedSha256()) {
            throw IOException("Downloaded ${asset.fileName} failed SHA-256 verification.")
        }
        return downloadedBytes
    } finally {
        connection.disconnect()
    }
}

private fun String.normalizedSha256(): String = lowercase(Locale.US)
private val HexDigits = "0123456789abcdef".toCharArray()

private fun ByteArray.toHexString(): String {
    val chars = CharArray(size * 2)
    forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xff
        chars[index * 2] = HexDigits[value ushr 4]
        chars[(index * 2) + 1] = HexDigits[value and 0x0f]
    }
    return String(chars)
}
