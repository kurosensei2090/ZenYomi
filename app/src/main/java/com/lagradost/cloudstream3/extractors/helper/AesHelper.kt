package com.lagradost.cloudstream3.extractors.helper

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesHelper {
    @JvmOverloads
    fun cryptoAESHandler(
        data: String,
        password: ByteArray,
        encrypt: Boolean = false,
        iv: String? = null,
    ): String {
        return try {
            val keySpec = SecretKeySpec(password, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            if (iv != null) {
                cipher.init(
                    if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
                    keySpec,
                    IvParameterSpec(iv.toByteArray()),
                )
            } else {
                cipher.init(
                    if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
                    keySpec,
                )
            }
            if (encrypt) {
                Base64.encodeToString(cipher.doFinal(data.toByteArray()), Base64.NO_WRAP)
            } else {
                String(cipher.doFinal(Base64.decode(data, Base64.DEFAULT)), Charsets.UTF_8)
            }
        } catch (_: Throwable) {
            data
        }
    }
}
