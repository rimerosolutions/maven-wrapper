/*
 * Copyright 2013. Rimero Solutions
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;


import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.codehaus.plexus.configuration.PlexusConfiguration;

/**
 * Unit test the Maven Wrapper Mojo
 *
 * @author Yves Zoundi
 */
public class MavenWrapperMojoTest extends AbstractMojoTestCase {

        private static final String MAVEN_WRAPPER_PLUGIN_NAME = "wrapper-maven-plugin";
        private static final String TEST_WRAPPER_DIR_LOCATION = "target/test-wrapper";
        private static final String TEST_SUPPORT_DIR_LOCATION = "support";
        private static final String TEST_DISTRIBUTION_URL = "http://mirrors.ibiblio.org/maven2/org/apache/maven/apache-maven";
        private static final List<String> TEST_DISTRIBUTION_URL_LIST = Collections.unmodifiableList(Arrays.asList(
                "http://mirrors.ibiblio.org/maven2/org/apache/maven/apache-maven",
                "https://repo1.maven.org/maven2/org/apache/maven/apache-maven"));
        private static final String PLUGIN_TEST_FILE_LOCATION = "src/test/resources/org/apache/maven/wrapper/plugin-config.xml";
        private static final String PLUGIN_TEST_ARTIFACT_LOCATION = "src/test/resources/org/apache/maven/wrapper/dummy-wrapper-artifact.txt";
        private static final String MAVEN_RUNTIME_VERSION = "0.0.1";
        private static final Boolean VERIFY_DOWNLOAD = true;
        private static final String CHECKSUM_ALGORITHM = "MD5";
        private static final String CHECKSUM_EXTENSION = "md5";

        private File wrapperDir;
        private File wrapperSupportDir;

        private MavenWrapperMojo lookupMavenWrapperMojo() throws Exception {
                File pluginXml = new File(getBasedir(), PLUGIN_TEST_FILE_LOCATION);
                PlexusConfiguration pluginConfiguration = extractPluginConfiguration(MAVEN_WRAPPER_PLUGIN_NAME, pluginXml);
                MavenWrapperMojo mojoInstance = MavenWrapperMojo.class.newInstance();
                MavenWrapperMojo mojo = (MavenWrapperMojo) configureMojo(mojoInstance, pluginConfiguration);

                return mojo;
        }

        private Properties readProperties() throws IOException {
                InputStream in = null;

                try {
                        in = new FileInputStream(new File(wrapperSupportDir, MavenWrapperMojo.WRAPPER_PROPERTIES_FILE_NAME));
                        Properties props = new Properties();
                        props.load(in);

                        return props;
                } finally {
                        if (in != null) {
                                IOUtils.closeQuietly(in);
                        }
                }
        }

        private String readDistributionUrlFromWrapperProperties(final Properties properties) throws IOException {
                return properties.getProperty(MavenWrapperMojo.DISTRIBUTION_URL_PROPERTY);
        }

        private Boolean readVerifyDownloadFromWrapperProperties(final Properties properties) throws IOException {
                return Boolean.valueOf(properties.getProperty(MavenWrapperMojo.VERIFY_DOWNLOAD_PROPERTY));
        }

        private String readChecksumAlgorithmFromWrapperProperties(final Properties properties) throws IOException {
                return properties.getProperty(MavenWrapperMojo.CHECKSUM_ALGORITHM_PROPERTY);
        }

        private String readChecksumUrlFromWrapperProperties(final Properties properties) throws IOException {
                return properties.getProperty(MavenWrapperMojo.CHECKSUM_URL_PROPERTY);
        }

        private String getExpectedDistributionUrl() {
                StringBuilder sb = new StringBuilder();
                for (String testDistributionUrl : TEST_DISTRIBUTION_URL_LIST) {
                        sb.append(testDistributionUrl).append('/');
                        sb.append(String.format(MavenWrapperMojo.DIST_FILENAME_PATH_TEMPLATE, MAVEN_RUNTIME_VERSION, MAVEN_RUNTIME_VERSION));
                        sb.append(",");
                }
                sb.setLength(sb.length() - 1);
                return sb.toString();
        }

        private String getExpectedChecksumUrl() {
                StringBuilder sb = new StringBuilder();
                for (String testDistributionUrl : TEST_DISTRIBUTION_URL_LIST) {
                        sb.append(testDistributionUrl).append('/');
                        sb.append(String.format(MavenWrapperMojo.CHECKSUM_FILENAME_PATH_TEMPLATE, MAVEN_RUNTIME_VERSION, MAVEN_RUNTIME_VERSION, CHECKSUM_EXTENSION));
                        sb.append(",");
                }
                sb.setLength(sb.length() - 1);
                return sb.toString();
        }

        protected void setUp() throws Exception {
                super.setUp();
                wrapperDir = new File(getBasedir(), TEST_WRAPPER_DIR_LOCATION);
                wrapperSupportDir = new File(wrapperDir, TEST_SUPPORT_DIR_LOCATION);
        }

        public void testMojoLookup() throws Exception {
                MavenWrapperMojo mojo = lookupMavenWrapperMojo();
                assertNotNull(mojo);
        }

        public void testMojoParameters() throws Exception {
                MavenWrapperMojo mojo = lookupMavenWrapperMojo();

                assertNotNull(mojo.getWrapperDirectory());
                assertNotNull(mojo.getBaseDistributionUrl());
                assertNotNull(mojo.getBaseDistributionUrlList());
                assertNotNull(mojo.getWrapperScriptDirectory());
                assertNotNull(mojo.getMavenVersion());
                assertNotNull(mojo.getVerifyDownload());
                assertNotNull(mojo.getChecksumExtension());
                assertNotNull(mojo.getChecksumAlgorithm());

                assertEquals(wrapperDir.getAbsolutePath(), mojo.getWrapperScriptDirectory());
                assertEquals(wrapperSupportDir.getAbsolutePath(), mojo.getWrapperDirectory());
                assertEquals(MAVEN_RUNTIME_VERSION, mojo.getMavenVersion());
                assertEquals(TEST_DISTRIBUTION_URL, mojo.getBaseDistributionUrl());
                assertEquals(TEST_DISTRIBUTION_URL_LIST, mojo.getBaseDistributionUrlList());
                assertEquals(VERIFY_DOWNLOAD, mojo.getVerifyDownload());
                assertEquals(CHECKSUM_EXTENSION, mojo.getChecksumExtension());
                assertEquals(CHECKSUM_ALGORITHM, mojo.getChecksumAlgorithm());
        }

        public void testMojoExecution() throws Exception {
                Artifact artifact = mock(Artifact.class);
                when(artifact.getFile()).thenReturn(new File(getBasedir(), PLUGIN_TEST_ARTIFACT_LOCATION));

                PluginDescriptor pluginDescriptor = mock(PluginDescriptor.class);
                when(pluginDescriptor.getPluginArtifact()).thenReturn(artifact);

                MavenWrapperMojo mojo = lookupMavenWrapperMojo();

                mojo.setPlugin(pluginDescriptor);
                mojo.setMavenVersion(MAVEN_RUNTIME_VERSION);
                mojo.execute();

                Properties properties = readProperties();

                assertTrue(new File(mojo.getWrapperScriptDirectory(), MavenWrapperMojo.SCRIPT_FILENAME_UNIX).exists());
                assertTrue(new File(mojo.getWrapperScriptDirectory(), MavenWrapperMojo.SCRIPT_FILENAME_WINDOWS).exists());
                assertTrue(new File(mojo.getWrapperDirectory(), MavenWrapperMojo.WRAPPER_PROPERTIES_FILE_NAME).exists());
                assertTrue(new File(mojo.getWrapperDirectory(), MavenWrapperMojo.WRAPPER_JAR_FILE_NAME).exists());
                assertEquals(getExpectedDistributionUrl(), readDistributionUrlFromWrapperProperties(properties));
                assertEquals(VERIFY_DOWNLOAD, readVerifyDownloadFromWrapperProperties(properties));
                assertEquals(CHECKSUM_ALGORITHM, readChecksumAlgorithmFromWrapperProperties(properties));
                assertEquals(getExpectedChecksumUrl(), readChecksumUrlFromWrapperProperties(properties));

                verify(artifact, times(1)).getFile();
        }
}
