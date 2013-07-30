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
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
        private static final String PLUGIN_TEST_FILE_LOCATION = "src/test/resources/org/apache/maven/wrapper/plugin-config.xml";
        private static final String PLUGIN_TEST_ARTIFACT_LOCATION = "src/test/resources/org/apache/maven/wrapper/dummy-wrapper-artifact.txt";
        private static final String MAVEN_RUNTIME_VERSION = "0.0.1";

        private File wrapperDir;
        private File wrapperSupportDir;

        private MavenWrapperMojo lookupMavenWrapperMojo() throws Exception {
                File pluginXml = new File(getBasedir(), PLUGIN_TEST_FILE_LOCATION);
                PlexusConfiguration pluginConfiguration = extractPluginConfiguration(MAVEN_WRAPPER_PLUGIN_NAME, pluginXml);
                MavenWrapperMojo mojoInstance = MavenWrapperMojo.class.newInstance();
                MavenWrapperMojo mojo = (MavenWrapperMojo) configureMojo(mojoInstance, pluginConfiguration);

                return mojo;
        }

        private String readDistributionUrlFromWrapperProperties() throws IOException {
                InputStream in = null;

                try {
                        in = new FileInputStream(new File(wrapperSupportDir, MavenWrapperMojo.WRAPPER_PROPERTIES_FILE_NAME));
                        Properties props = new Properties();
                        props.load(in);

                        return props.getProperty(MavenWrapperMojo.DISTRIBUTION_URL_PROPERTY);
                } finally {
                        if (in != null) {
                                IOUtils.closeQuietly(in);
                        }
                }
        }

        private String getExpectedDistributionUrl() {
                StringBuilder sb = new StringBuilder(TEST_DISTRIBUTION_URL).append('/');
                sb.append(MavenWrapperMojo.DIST_FILENAME_PATH_TEMPLATE);

                return String.format(sb.toString(), MAVEN_RUNTIME_VERSION, MAVEN_RUNTIME_VERSION);
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
                assertNotNull(mojo.getWrapperScriptDirectory());
                assertNotNull(mojo.getMavenVersion());

                assertEquals(wrapperDir.getAbsolutePath(), mojo.getWrapperScriptDirectory());
                assertEquals(wrapperSupportDir.getAbsolutePath(), mojo.getWrapperDirectory());
                assertEquals(MAVEN_RUNTIME_VERSION, mojo.getMavenVersion());
                assertEquals(TEST_DISTRIBUTION_URL, mojo.getBaseDistributionUrl());
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

                assertTrue(new File(mojo.getWrapperScriptDirectory(), MavenWrapperMojo.SCRIPT_FILENAME_UNIX).exists());
                assertTrue(new File(mojo.getWrapperScriptDirectory(), MavenWrapperMojo.SCRIPT_FILENAME_WINDOWS).exists());
                assertTrue(new File(mojo.getWrapperDirectory(), MavenWrapperMojo.WRAPPER_PROPERTIES_FILE_NAME).exists());
                assertTrue(new File(mojo.getWrapperDirectory(), MavenWrapperMojo.WRAPPER_JAR_FILE_NAME).exists());
                assertEquals(getExpectedDistributionUrl(), readDistributionUrlFromWrapperProperties());
        }
}
