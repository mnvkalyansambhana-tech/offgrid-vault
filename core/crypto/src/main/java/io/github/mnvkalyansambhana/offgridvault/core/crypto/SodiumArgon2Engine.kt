package io.github.mnvkalyansambhana.offgridvault.core.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.PwHash
import com.sun.jna.NativeLong

/** Argon2id through libsodium's `crypto_pwhash` (C13). Works with any Lazysodium binding. */
class SodiumArgon2Engine(private val sodium: PwHash.Native) : Argon2Engine {

    override fun hash(
        out: ByteArray,
        password: ByteArray,
        salt: ByteArray,
        iterations: Long,
        memoryBytes: Long,
    ): Boolean = sodium.cryptoPwHash(
        out,
        out.size,
        password,
        password.size,
        salt,
        iterations,
        NativeLong(memoryBytes),
        PwHash.Alg.PWHASH_ALG_ARGON2ID13,
    )
}

/** The Android libsodium binding. Kept separate so JVM tests never load Android classes. */
object AndroidSodium {
    val argon2id: Argon2id by lazy { Argon2id(SodiumArgon2Engine(LazySodiumAndroid(SodiumAndroid()))) }
}
