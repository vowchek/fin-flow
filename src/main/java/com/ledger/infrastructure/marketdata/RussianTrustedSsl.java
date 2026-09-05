package com.ledger.infrastructure.marketdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

/**
 * Builds an SSLContext that trusts the JVM default CAs plus Russian Trusted Root/Sub CA
 * (required for invest-public-api.tinkoff.ru).
 */
public final class RussianTrustedSsl {

    private static final Logger log = LoggerFactory.getLogger(RussianTrustedSsl.class);

    private RussianTrustedSsl() {
    }

    public static SSLContext create() {
        try {
            TrustManagerFactory defaultTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            defaultTmf.init((KeyStore) null);
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            // Copy default trusted certs when available via default TrustManager is complex;
            // load default cacerts explicitly.
            String javaHome = System.getProperty("java.home");
            try (InputStream cacerts = new java.io.FileInputStream(javaHome + "/lib/security/cacerts")) {
                trustStore.load(cacerts, "changeit".toCharArray());
            } catch (Exception ex) {
                trustStore.load(null, null);
                log.warn("Could not load default cacerts, using empty trust store: {}", ex.getMessage());
            }

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            importPem(trustStore, cf, "russian-trusted-root", "/certs/russian_trusted_root_ca.pem");
            importPem(trustStore, cf, "russian-trusted-sub", "/certs/russian_trusted_sub_ca.pem");

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build Russian Trusted SSL context", ex);
        }
    }

    private static void importPem(KeyStore trustStore, CertificateFactory cf, String aliasPrefix, String resource)
            throws Exception {
        try (InputStream in = RussianTrustedSsl.class.getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("CA resource missing: {}", resource);
                return;
            }
            Collection<?> certs = cf.generateCertificates(in);
            int i = 0;
            for (Object c : certs) {
                if (c instanceof X509Certificate x509) {
                    trustStore.setCertificateEntry(aliasPrefix + "-" + i++, x509);
                }
            }
            log.info("Imported {} cert(s) from {}", i, resource);
        }
    }
}
