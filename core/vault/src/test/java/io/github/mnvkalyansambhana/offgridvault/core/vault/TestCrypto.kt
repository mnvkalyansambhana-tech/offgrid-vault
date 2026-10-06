package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.AesGcm
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2Engine
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2id
import io.github.mnvkalyansambhana.offgridvault.core.crypto.DeviceKeyUnavailableException
import io.github.mnvkalyansambhana.offgridvault.core.crypto.DeviceSealer
import java.security.MessageDigest

/** Stand-in for Keystore K_device: AES-GCM under a key that "lives on this device". */
class FakeDevice : DeviceSealer {
    private val key = AesGcm.newKey()
    var gone = false
    override fun seal(plaintext: ByteArray, associatedData: ByteArray) =
        if (gone) throw DeviceKeyUnavailableException() else AesGcm.encrypt(key, plaintext, associatedData)
    override fun open(sealed: ByteArray, associatedData: ByteArray) =
        if (gone) throw DeviceKeyUnavailableException() else AesGcm.decrypt(key, sealed, associatedData)
}

/** Counts derivations so tests can prove "no Argon2 run when locked out". */
class CountingFastArgon2 {
    var calls = 0
    val argon2 = Argon2id(
        Argon2Engine { out, password, salt, iterations, memory ->
            calls++
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(password); digest.update(salt); digest.update("$iterations/$memory".toByteArray())
            digest.digest().copyInto(out)
            true
        },
    )
}

/** Fast deterministic KDF stand-in (real Argon2id is covered in :core:crypto). */
val fastArgon2: Argon2id = CountingFastArgon2().argon2
