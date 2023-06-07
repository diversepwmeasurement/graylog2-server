/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
package org.graylog.datanode.configuration.variants;

import org.graylog.datanode.Configuration;
import org.graylog.datanode.configuration.RootCertificateFinder;
import org.graylog.datanode.configuration.TlsConfigurationSupplier;
import org.graylog.datanode.configuration.TruststoreCreator;
import org.graylog.datanode.configuration.certificates.CertificateMetaData;
import org.graylog.datanode.configuration.certificates.KeystoreReEncryption;
import org.graylog.security.certutil.CertConstants;
import org.graylog.security.certutil.ca.exceptions.KeyStoreStorageException;
import org.graylog.security.certutil.keystore.storage.location.KeystoreFileLocation;
import org.graylog.security.certutil.keystore.storage.location.KeystoreMongoCollections;
import org.graylog.security.certutil.keystore.storage.location.KeystoreMongoLocation;
import org.graylog2.cluster.certificates.CertificatesService;
import org.graylog2.plugin.system.NodeId;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.graylog.datanode.configuration.TlsConfigurationSupplier.TRUSTSTORE_FILENAME;

public final class MongoCertSecureConfiguration extends SecureConfiguration {

    private final KeystoreFileLocation finalTransportKeystoreLocation;
    private final KeystoreFileLocation finalHttpKeystoreLocation;
    private final KeystoreReEncryption keystoreReEncryption;
    private final TlsConfigurationSupplier tlsConfigurationSupplier;
    private final TruststoreCreator truststoreCreator;
    private final RootCertificateFinder rootCertificateFinder;
    private final CertificatesService certificatesService;

    private final char[] secret;
    private final KeystoreMongoLocation mongoLocation;

    private final String mongoKeystorePassword;

    @Inject
    public MongoCertSecureConfiguration(final Configuration localConfiguration,
                                        final KeystoreReEncryption keystoreReEncryption,
                                        final TlsConfigurationSupplier tlsConfigurationSupplier,
                                        final TruststoreCreator truststoreCreator,
                                        final RootCertificateFinder rootCertificateFinder,
                                        final NodeId nodeId,
                                        final @Named("password_secret") String passwordSecret,
                                        final CertificatesService certificatesService
    ) {
        super(localConfiguration);
        this.keystoreReEncryption = keystoreReEncryption;
        this.tlsConfigurationSupplier = tlsConfigurationSupplier;
        this.truststoreCreator = truststoreCreator;
        this.rootCertificateFinder = rootCertificateFinder;
        this.certificatesService = certificatesService;

        this.finalTransportKeystoreLocation = new KeystoreFileLocation(
                opensearchConfigDir.resolve(localConfiguration.getDatanodeTransportCertificate())
        );
        this.finalHttpKeystoreLocation = new KeystoreFileLocation(
                opensearchConfigDir.resolve(localConfiguration.getDatanodeHttpCertificate())
        );

        this.mongoLocation = new KeystoreMongoLocation(nodeId.getNodeId(), KeystoreMongoCollections.DATA_NODE_KEYSTORE_COLLECTION);
        this.secret = passwordSecret.toCharArray();

        //TODO: matches line 123 of DataNodePreflightGeneratePeriodical, but both need to be changed
        this.mongoKeystorePassword = localConfiguration.getDatanodeHttpCertificatePassword();
    }

    @Override
    public boolean checkPrerequisites(Configuration localConfiguration) {
        return certificatesService.hasCert(mongoLocation);
    }

    @Override
    public Map<String, String> configure(Configuration localConfiguration) throws KeyStoreStorageException, IOException, GeneralSecurityException {
        Map<String, String> config = commonSecureConfig(localConfiguration);
        Map<String, X509Certificate> rootCerts = new HashMap<>();
        final String truststorePassword = UUID.randomUUID().toString();
        keystoreReEncryption.reEncyptWithSecret(
                mongoLocation,
                mongoKeystorePassword.toCharArray(),
                finalTransportKeystoreLocation);
        keystoreReEncryption.reEncyptWithSecret(
                mongoLocation,
                mongoKeystorePassword.toCharArray(),
                finalHttpKeystoreLocation);

        configureInitialAdmin(localConfiguration, localConfiguration.getRestApiUsername(), localConfiguration.getRestApiPassword());

        rootCerts.put("shared-chain-CA-root", rootCertificateFinder.findRootCert(
                finalTransportKeystoreLocation.keystorePath(),
                secret,
                CertConstants.DATANODE_KEY_ALIAS));
        tlsConfigurationSupplier.addTransportTlsConfig(config,
                new CertificateMetaData(
                        localConfiguration.getDatanodeTransportCertificate(),
                        secret
                )
        );
        tlsConfigurationSupplier.addHttpTlsConfig(config,
                new CertificateMetaData(
                        localConfiguration.getDatanodeHttpCertificate(),
                        secret
                )
        );

        if (!rootCerts.isEmpty()) {
            final Path trustStorePath = opensearchConfigDir.resolve(TRUSTSTORE_FILENAME);
            truststoreCreator.createTruststore(rootCerts,
                    truststorePassword.toCharArray(),
                    trustStorePath
            );
            System.setProperty("javax.net.ssl.trustStore", trustStorePath.toAbsolutePath().toString());
            System.setProperty("javax.net.ssl.trustStorePassword", truststorePassword);
            tlsConfigurationSupplier.addTrustStoreTlsConfig(config, truststorePassword);
        }

        return config;
    }
}