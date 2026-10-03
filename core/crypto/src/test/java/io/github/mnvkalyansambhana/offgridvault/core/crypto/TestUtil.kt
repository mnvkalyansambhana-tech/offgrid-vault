package io.github.mnvkalyansambhana.offgridvault.core.crypto

import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava

fun String.hex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** The production engine, on the desktop build of the same libsodium release (1.0.20). */
val jvmSodiumArgon2: Argon2id by lazy { Argon2id(SodiumArgon2Engine(LazySodiumJava(SodiumJava()))) }
