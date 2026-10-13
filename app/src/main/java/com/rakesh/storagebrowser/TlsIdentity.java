package com.rakesh.storagebrowser;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Calendar;
import java.util.Locale;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.security.auth.x500.X500Principal;

final class TlsIdentity {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "storage-browser-tls";

    private TlsIdentity() {}

    static SSLContext create() throws GeneralSecurityException {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
            keyStore.load(null);
            if (!keyStore.containsAlias(ALIAS)) {
                Calendar start = Calendar.getInstance();
                Calendar end = (Calendar) start.clone();
                end.add(Calendar.YEAR, 10);
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", KEYSTORE);
                generator.initialize(new KeyGenParameterSpec.Builder(
                    ALIAS, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setKeySize(2048)
                    .setCertificateSubject(new X500Principal("CN=Storage Browser"))
                    .setCertificateSerialNumber(BigInteger.ONE)
                    .setCertificateNotBefore(start.getTime())
                    .setCertificateNotAfter(end.getTime())
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build());
                generator.generateKeyPair();
                keyStore.load(null);
            }

            KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(keyStore, null);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers.getKeyManagers(), null, null);
            return sslContext;
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException("Unable to load the Android keystore", e);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new GeneralSecurityException("Unable to initialize the TLS identity", e);
        }
    }

    static String fingerprint() throws GeneralSecurityException {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
            keyStore.load(null);
            Certificate certificate = keyStore.getCertificate(ALIAS);
            if (certificate == null) {
                throw new GeneralSecurityException("TLS certificate is unavailable");
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            StringBuilder value = new StringBuilder(digest.length * 3 - 1);
            for (byte b : digest) {
                if (value.length() > 0) value.append(':');
                value.append(String.format(Locale.ROOT, "%02X", b & 0xff));
            }
            return value.toString();
        } catch (java.io.IOException | java.security.cert.CertificateException e) {
            throw new GeneralSecurityException("Unable to read the TLS certificate", e);
        }
    }
}
