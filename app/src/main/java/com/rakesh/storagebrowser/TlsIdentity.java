package com.rakesh.storagebrowser;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Locale;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.security.auth.x500.X500Principal;

final class TlsIdentity {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "storage-browser-tls-v2";

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
                    .setSignaturePaddings(
                        KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                        KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                    .build());
                generator.generateKeyPair();
                keyStore.load(null);
            }

            PrivateKey privateKey = (PrivateKey) keyStore.getKey(ALIAS, null);
            Certificate[] chain = keyStore.getCertificateChain(ALIAS);
            if (privateKey == null || chain == null || chain.length == 0
                || !(chain[0] instanceof X509Certificate)) {
                throw new GeneralSecurityException("TLS server key or certificate is missing");
            }
            X509Certificate[] certificates = new X509Certificate[chain.length];
            for (int i = 0; i < chain.length; i++) {
                if (!(chain[i] instanceof X509Certificate)) {
                    throw new GeneralSecurityException("TLS certificate chain is invalid");
                }
                certificates[i] = (X509Certificate) chain[i];
            }
            X509ExtendedKeyManager keyManager = new X509ExtendedKeyManager() {
                @Override public String[] getClientAliases(String keyType, java.security.Principal[] issuers) { return null; }
                @Override public String chooseClientAlias(String[] keyTypes, java.security.Principal[] issuers, java.net.Socket socket) { return null; }
                @Override public String[] getServerAliases(String keyType, java.security.Principal[] issuers) { return supportsRsa(keyType) ? new String[]{ALIAS} : null; }
                @Override public String chooseServerAlias(String keyType, java.security.Principal[] issuers, java.net.Socket socket) { return supportsRsa(keyType) ? ALIAS : null; }
                @Override public String chooseEngineClientAlias(String[] keyTypes, java.security.Principal[] issuers, SSLEngine engine) { return null; }
                @Override public String chooseEngineServerAlias(String keyType, java.security.Principal[] issuers, SSLEngine engine) { return supportsRsa(keyType) ? ALIAS : null; }
                @Override public X509Certificate[] getCertificateChain(String alias) { return ALIAS.equals(alias) ? certificates.clone() : null; }
                @Override public PrivateKey getPrivateKey(String alias) { return ALIAS.equals(alias) ? privateKey : null; }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(new javax.net.ssl.KeyManager[]{keyManager}, null, null);
            return sslContext;
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException("Unable to load the Android keystore", e);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new GeneralSecurityException("Unable to initialize the TLS identity", e);
        }
    }

    private static boolean supportsRsa(String keyType) {
        return "RSA".equalsIgnoreCase(keyType) || "RSASSA-PSS".equalsIgnoreCase(keyType);
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
