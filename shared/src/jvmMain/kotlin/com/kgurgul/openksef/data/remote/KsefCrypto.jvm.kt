package com.kgurgul.openksef.data.remote

import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

class JvmKsefCrypto : KsefCrypto {
    override fun rsaOaepSha256Encrypt(data: ByteArray, certificateDer: ByteArray): ByteArray {
        val factory = CertificateFactory.getInstance("X.509")
        val cert =
            factory.generateCertificate(ByteArrayInputStream(certificateDer)) as X509Certificate
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val params = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
        )
        cipher.init(Cipher.ENCRYPT_MODE, cert.publicKey, params)
        return cipher.doFinal(data)
    }
}

actual fun defaultKsefCrypto(): KsefCrypto = JvmKsefCrypto()
