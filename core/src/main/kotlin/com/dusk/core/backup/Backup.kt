package com.dusk.core.backup

import com.dusk.core.journal.DayEntry
import com.dusk.core.json.Json
import com.dusk.core.json.JsonObj
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Everything a backup holds. Later releases add items, reminders and the decode key here. */
data class BackupContents(
    val createdAt: Long,
    val appVersion: String,
    val entries: List<DayEntry>,
) {
    fun toJson(): String = Json.write(
        linkedMapOf(
            "createdAt" to createdAt,
            "appVersion" to appVersion,
            "journal" to linkedMapOf("entries" to entries.sortedBy { it.date }.map { it.toMap() }),
        ),
    )

    companion object {
        fun fromJson(text: String): BackupContents {
            val o = JsonObj.parse(text)
            return BackupContents(
                createdAt = o.long("createdAt"),
                appVersion = o.strOr("appVersion", "?"),
                entries = o.obj("journal").objs("entries").map { DayEntry.fromJson(it) },
            )
        }
    }
}

class BackupException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { NOT_A_BACKUP, NEWER_VERSION, WRONG_PASSPHRASE, DAMAGED }
}

/**
 * A backup file is a small JSON envelope around AES-256-GCM ciphertext.
 * The key comes from the passphrase through PBKDF2-HMAC-SHA256, so the file opens on any phone
 * that knows the passphrase and on no phone that doesn't.
 */
object BackupCodec {
    const val FORMAT = "dusk-backup"
    const val VERSION = 1L
    const val DEFAULT_ITERATIONS = 310_000
    const val MIN_PASSPHRASE = 8
    private const val MAX_ITERATIONS = 5_000_000L
    private val AAD = "dusk-backup:v1".toByteArray()

    private fun key(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(contents: BackupContents, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS, random: SecureRandom = SecureRandom()): ByteArray {
        require(passphrase.size >= MIN_PASSPHRASE) { "passphrase must have at least $MIN_PASSPHRASE characters" }
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD)
        val data = cipher.doFinal(contents.toJson().toByteArray(Charsets.UTF_8))
        val b64 = Base64.getEncoder()
        val envelope = linkedMapOf(
            "format" to FORMAT,
            "version" to VERSION,
            "note" to "Encrypted Dusk backup. It opens only with the passphrase chosen when it was made.",
            "createdAt" to contents.createdAt,
            "entries" to contents.entries.size,
            "kdf" to linkedMapOf("name" to "PBKDF2WithHmacSHA256", "iterations" to iterations, "salt" to b64.encodeToString(salt)),
            "cipher" to linkedMapOf("name" to "AES-256-GCM", "iv" to b64.encodeToString(iv)),
            "data" to b64.encodeToString(data),
        )
        return Json.write(envelope).toByteArray(Charsets.UTF_8)
    }

    /** Reads the outside of the file without the passphrase: when it was made and how many days it holds. */
    data class Summary(val createdAt: Long, val entries: Int)

    fun summary(file: ByteArray): Summary {
        val o = envelope(file)
        return Summary(o.long("createdAt"), (o.longOrNull("entries") ?: 0L).toInt())
    }

    private fun envelope(file: ByteArray): JsonObj {
        val o = try {
            JsonObj.parse(String(file, Charsets.UTF_8))
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, "This file is not a Dusk backup.")
        }
        if (o.strOr("format", "") != FORMAT) throw BackupException(BackupException.Reason.NOT_A_BACKUP, "This file is not a Dusk backup.")
        val v = o.longOrNull("version") ?: 0L
        if (v > VERSION) throw BackupException(BackupException.Reason.NEWER_VERSION, "This backup was made by a newer version of Dusk. Update the app first.")
        return o
    }

    fun decrypt(file: ByteArray, passphrase: CharArray): BackupContents {
        val o = envelope(file)
        try {
            val kdf = o.obj("kdf")
            val iterations = kdf.long("iterations")
            if (iterations !in 1..MAX_ITERATIONS) throw BackupException(BackupException.Reason.DAMAGED, "The backup file is damaged.")
            val b64 = Base64.getDecoder()
            val salt = b64.decode(kdf.str("salt"))
            val iv = b64.decode(o.obj("cipher").str("iv"))
            val data = b64.decode(o.str("data"))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt, iterations.toInt()), GCMParameterSpec(128, iv))
            cipher.updateAAD(AAD)
            val plain = try {
                cipher.doFinal(data)
            } catch (e: AEADBadTagException) {
                throw BackupException(BackupException.Reason.WRONG_PASSPHRASE, "Wrong passphrase, or the file was changed.")
            }
            return BackupContents.fromJson(String(plain, Charsets.UTF_8))
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.DAMAGED, "The backup file is damaged.")
        }
    }
}
