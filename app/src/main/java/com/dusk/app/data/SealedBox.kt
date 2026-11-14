package com.dusk.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encryption for one store (journal now; planner and decode key later).
 *
 * Each store has its own random 256-bit data key. That key is saved only in wrapped form,
 * encrypted by a key that lives in the phone's hardware keystore and can never be read out.
 * Files are then encrypted with AES-256-GCM using the data key, which keeps opening a year of
 * entries fast (one keystore call, not one per day).
 *
 * The keystore key is deliberately NOT tied to fingerprint or PIN. A key tied to the screen lock is
 * erased by Android if the screen lock is ever removed, and the journal would be lost. The app lock
 * screen guards access instead. See the release note.
 */
class SealedBox(private val store: String, private val keyFile: File) {

    class Unreadable(message: String, cause: Throwable? = null) : Exception(message, cause)

    private val keystoreAlias = "dusk.$store.wrap"
    @Volatile private var dataKey: SecretKeySpec? = null

    private fun keystore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun wrappingKey(create: Boolean): SecretKey? {
        (keystore().getKey(keystoreAlias, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(keystoreAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    /** True when a data key file exists but this phone cannot unwrap it (moved from another phone, or keystore reset). */
    fun keyIsUnreadable(): Boolean = keyFile.exists() && runCatching { key() }.isFailure

    @Synchronized
    fun key(): SecretKeySpec {
        dataKey?.let { return it }
        val k = if (keyFile.exists()) {
            val wrap = wrappingKey(create = false) ?: throw Unreadable("the key for the $store store is missing from this phone")
            val raw = try {
                openWith(wrap, keyFile.readBytes())
            } catch (e: Exception) {
                throw Unreadable("the key for the $store store cannot be opened on this phone", e)
            }
            SecretKeySpec(raw, "AES")
        } else {
            val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val sealed = sealWith(wrappingKey(create = true)!!, raw)
            keyFile.parentFile?.mkdirs()
            writeAtomically(keyFile, sealed)
            SecretKeySpec(raw, "AES")
        }
        dataKey = k
        return k
    }

    fun seal(plain: ByteArray): ByteArray = sealWith(key(), plain)

    fun open(sealed: ByteArray): ByteArray = try {
        openWith(key(), sealed)
    } catch (e: Unreadable) {
        throw e
    } catch (e: Exception) {
        throw Unreadable("a file in the $store store cannot be opened", e)
    }

    /** Forget the data key so the store can start fresh after its files were moved aside. */
    @Synchronized
    fun reset() {
        dataKey = null
        keyFile.delete()
    }

    companion object {
        private const val VERSION: Byte = 1

        private fun sealWith(key: SecretKey, plain: ByteArray): ByteArray {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            if (key is SecretKeySpec) {
                val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
                c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            } else {
                c.init(Cipher.ENCRYPT_MODE, key) // the keystore picks the IV itself
            }
            val iv = c.iv
            val ct = c.doFinal(plain)
            return byteArrayOf(VERSION, iv.size.toByte()) + iv + ct
        }

        private fun openWith(key: SecretKey, sealed: ByteArray): ByteArray {
            require(sealed.size > 2 && sealed[0] == VERSION) { "unknown file version" }
            val ivLen = sealed[1].toInt()
            require(ivLen in 12..16 && sealed.size > 2 + ivLen) { "bad header" }
            val iv = sealed.copyOfRange(2, 2 + ivLen)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            return c.doFinal(sealed, 2 + ivLen, sealed.size - 2 - ivLen)
        }

        /** Write to a temp file, then rename, so a crash mid-write never leaves half a file. */
        fun writeAtomically(target: File, bytes: ByteArray) {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.outputStream().use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) throw java.io.IOException("could not save ${target.name}")
            }
        }
    }
}
