/*
 * Copyright 2007-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.maven.wrapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author Hans Dockter
 */
public class WrapperExecutor {
    public static final String DISTRIBUTION_URL_PROPERTY = "distributionUrl";
    public static final String DISTRIBUTION_BASE_PROPERTY = "distributionBase";
    public static final String ZIP_STORE_BASE_PROPERTY = "zipStoreBase";
    public static final String DISTRIBUTION_PATH_PROPERTY = "distributionPath";
    public static final String ZIP_STORE_PATH_PROPERTY = "zipStorePath";
    public static final String VERIFY_DOWNLOAD_PROPERTY = "verifyDownload";
    public static final String CHECKSUM_ALGORITHM_PROPERTY = "checksumAlgorithm";
    public static final String CHECKSUM_URL_PROPERTY = "checksumUrl";
    private final Properties properties;

    private final File propertiesFile;
    private final WrapperConfiguration config = new WrapperConfiguration();

    public static WrapperExecutor forProjectDirectory(File projectDir) {
        return new WrapperExecutor(new File(projectDir, "maven/wrapper/maven-wrapper.properties"), new Properties());
    }

    public static WrapperExecutor forWrapperPropertiesFile(File propertiesFile) {
        if (!propertiesFile.exists()) {
            throw new RuntimeException(String.format("Wrapper properties file '%s' does not exist.", propertiesFile));
        }
	
        return new WrapperExecutor(propertiesFile, new Properties());
    }

    WrapperExecutor(File propertiesFile, Properties properties) {
        this.properties = properties;
        this.propertiesFile = propertiesFile;

        if (propertiesFile.exists()) {
            try {
                loadProperties(propertiesFile, properties);

		config.setDistributionBase(getProperty(DISTRIBUTION_BASE_PROPERTY, config.getDistributionBase()));
                config.setDistributionPath(getProperty(DISTRIBUTION_PATH_PROPERTY, config.getDistributionPath()));
                config.setZipBase(getProperty(ZIP_STORE_BASE_PROPERTY, config.getZipBase()));
                config.setZipPath(getProperty(ZIP_STORE_PATH_PROPERTY, config.getZipPath()));
		config.setDistributionUris(prepareDistributionUris());
		config.setVerifyDownload(Boolean.valueOf(getProperty(VERIFY_DOWNLOAD_PROPERTY, "false")));		

		if (config.isVerifyDownload()) {
		    config.setChecksumAlgorithm(Checksum.valueOf(getProperty(CHECKSUM_ALGORITHM_PROPERTY)));
		}
            } catch (Exception e) {
                throw new RuntimeException(String.format("Could not load wrapper properties from '%s'.", propertiesFile), e);
            }
        }
    }

    private List<URI> prepareDistributionUris() throws URISyntaxException {
        return readRequiredUriList(DISTRIBUTION_URL_PROPERTY);
    }
    
    private List<URI> readRequiredUriList(String key) throws URISyntaxException {
        List<URI> uriList = new ArrayList<URI>();

        if (properties.getProperty(key) != null) {
            for (String value : getProperty(key).split(",")) {
                if (value.trim().length() == 0) {
                    continue;
                }

                URI uri = URI.create(value);

                if (uri.getScheme() == null) {
                    uri = new File(propertiesFile.getParentFile(), uri.getSchemeSpecificPart()).toURI();
                }

                uriList.add(uri);
            }

            return uriList;
        }

        reportMissingProperty(key);
        return null; // previous line will fail
    }

    private static void loadProperties(File propertiesFile, Properties properties) throws IOException {
        InputStream inStream = new FileInputStream(propertiesFile);

        try {
            properties.load(inStream);
        } finally {
            inStream.close();
        }
    }

    /**
     * Returns the distribution which this wrapper will use. Returns null if no
     * wrapper meta-data was found in the specified project directory.
     */
    public List<URI> getDistributionUris() {
        return config.getDistributionUris();
    }

    /**
     * Returns the configuration for this wrapper.
     */
    public WrapperConfiguration getConfiguration() {
        return config;
    }

    public void execute(String[] args, Installer install, BootstrapMainStarter bootstrapMainStarter) throws Exception {
        File mavenHome = install.createDist(config);
	bootstrapMainStarter.start(args, mavenHome);
    }

    private String getProperty(String propertyName) {
        return getProperty(propertyName, null);
    }

    private String getProperty(String propertyName, String defaultValue) {
        String value = properties.getProperty(propertyName);

	if (value != null) {
            return value;
        }
	
        if (defaultValue != null) {
            return defaultValue;
        }
	
        return reportMissingProperty(propertyName);
    }

    private String reportMissingProperty(String propertyName) {
        throw new RuntimeException(
                String.format("No value with key '%s' specified in wrapper properties file '%s'.", propertyName, propertiesFile));
    }
}
