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

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.regex.Pattern;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Hans Dockter
 */
public class PathAssemblerTest {
        private static final String TEST_MAVEN_USER_HOME = "someUserHome";
        private static final URI TEST_DISTRIBUTION_URI = URI.create("http://server/dist/maven-0.9-bin.zip");
        private static final URI TEST_DISTRIBUTION_NO_TYPE_URI = URI.create("http://server/dist/maven-1.0.zip");

        private PathAssembler pathAssembler = new PathAssembler(new File(TEST_MAVEN_USER_HOME));
        final WrapperConfiguration configuration = new WrapperConfiguration();

        @Before
        public void setup() {
                configuration.setDistributionBase(PathAssembler.MAVEN_USER_HOME_STRING);
                configuration.setDistributionPath("somePath");
                configuration.setZipBase(PathAssembler.MAVEN_USER_HOME_STRING);
                configuration.setZipPath("somePath");
        }

        @Test
        public void distributionDirWithMavenUserHomeBase() throws Exception {
                configuration.setDistribution(Collections.singletonList(TEST_DISTRIBUTION_URI));

                File distributionDir = pathAssembler.getDistribution(configuration, TEST_DISTRIBUTION_URI).getDistributionDir();
                assertThat(distributionDir.getName(), matchesRegexp("[a-z0-9]+"));
                assertThat(distributionDir.getParentFile(), equalTo(file(TEST_MAVEN_USER_HOME + "/somePath/maven-0.9-bin")));
        }

        @Test
        public void distributionDirWithProjectBase() throws Exception {
                configuration.setDistributionBase(PathAssembler.PROJECT_STRING);
                configuration.setDistribution(Collections.singletonList(TEST_DISTRIBUTION_URI));

                File distributionDir = pathAssembler.getDistribution(configuration, TEST_DISTRIBUTION_URI).getDistributionDir();
                assertThat(distributionDir.getName(), matchesRegexp("[a-z0-9]+"));
                assertThat(distributionDir.getParentFile(), equalTo(file(currentDirPath() + "/somePath/maven-0.9-bin")));
        }

        @Test
        public void distributionDirWithUnknownBase() throws Exception {
                configuration.setDistribution(Collections.singletonList(TEST_DISTRIBUTION_NO_TYPE_URI));
                configuration.setDistributionBase("unknownBase");

                try {
                        pathAssembler.getDistribution(configuration, TEST_DISTRIBUTION_NO_TYPE_URI);
                        fail();
                }
                catch (RuntimeException e) {
                        assertEquals("Base: unknownBase is unknown", e.getMessage());
                }
        }

        @Test
        public void distZipWithMavenUserHomeBase() throws Exception {
                configuration.setDistribution(Collections.singletonList(TEST_DISTRIBUTION_NO_TYPE_URI));

                File dist = pathAssembler.getDistribution(configuration, TEST_DISTRIBUTION_NO_TYPE_URI).getZipFile();
                assertThat(dist.getName(), equalTo("maven-1.0.zip"));
                assertThat(dist.getParentFile().getName(), matchesRegexp("[a-z0-9]+"));
                assertThat(dist.getParentFile().getParentFile(),
                           equalTo(file(TEST_MAVEN_USER_HOME + "/somePath/maven-1.0")));
        }

        @Test
        public void distZipWithProjectBase() throws Exception {
                configuration.setZipBase(PathAssembler.PROJECT_STRING);
                configuration.setDistribution(Collections.singletonList(TEST_DISTRIBUTION_NO_TYPE_URI));

                File dist = pathAssembler.getDistribution(configuration, TEST_DISTRIBUTION_NO_TYPE_URI).getZipFile();
                assertThat(dist.getName(), equalTo("maven-1.0.zip"));
                assertThat(dist.getParentFile().getName(), matchesRegexp("[a-z0-9]+"));
                assertThat(dist.getParentFile().getParentFile(), equalTo(file(currentDirPath() + "/somePath/maven-1.0")));
        }

        private File file(String path) {
                return new File(path);
        }

        private String currentDirPath() {
                return System.getProperty("user.dir");
        }

        public static <T extends CharSequence> Matcher<T> matchesRegexp(final String pattern) {
                return new BaseMatcher<T>() {
                        public boolean matches(Object o) {
                                return Pattern.compile(pattern).matcher((CharSequence) o).matches();
                        }

                        public void describeTo(Description description) {
                                description.appendText("a CharSequence that matches regexp ").appendValue(pattern);
                        }
                };
        }
}
