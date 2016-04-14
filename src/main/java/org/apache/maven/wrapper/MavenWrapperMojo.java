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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;

@Mojo(name = "wrapper", requiresProject = true)
/**
 * Generates a Maven Command Line Wrapper for the current project.
 *
 * @author Yves Zoundi
 */
public class MavenWrapperMojo extends AbstractMojo implements Contextualizable {

        public static final String DIST_FILENAME_PATH_TEMPLATE = "%s/apache-maven-%s-bin.zip";
        public static final String CHECKSUM_FILENAME_PATH_TEMPLATE = "%s/apache-maven-%s-bin.zip.%s";
        public static final String WRAPPER_PROPERTIES_FILE_NAME = "maven-wrapper.properties";
        public static final String WRAPPER_JAR_FILE_NAME = "maven-wrapper.jar";
        public static final String DISTRIBUTION_URL_PROPERTY = "distributionUrl";
        public static final String VERIFY_DOWNLOAD_PROPERTY = "verifyDownload";
        public static final String CHECKSUM_URL_PROPERTY = "checksumUrl";
        public static final String CHECKSUM_ALGORITHM_PROPERTY = "checksumAlgorithm";
        public static final String SCRIPT_FILENAME_WINDOWS = "mvnw.bat";
        public static final String SCRIPT_FILENAME_UNIX = "mvnw";

        private static final String DEFAULT_BASE_DISTRIBUTION_URL = "https://repository.apache.org/content/repositories/releases/org/apache/maven/apache-maven";
        private static final String WRAPPER_TEMPLATES_LOCATION = "com/rimerosolutions/maven/plugins/wrapper/";
        private static final String WRAPPER_PROPERTIES_COMMENTS = "Maven download properties";
        private static final String ENCODING_UTF8 = "UTF-8";

        static final String[] LAUNCHER_FILE_SEPARATORS = { "\\", "/" };
        static final String[] LAUNCHER_FILE_BASE_NAMES = { SCRIPT_FILENAME_WINDOWS, SCRIPT_FILENAME_UNIX };
        static final String[] LAUNCHERS_PARTS_WINDOWS = { "mvnw_header.bat", "", "mvnw_footer.bat" };
        static final String[] LAUNCHERS_PARTS_UNIX = { "mvnw_header", "", "mvnw_footer" };
        static final String[][] LAUNCHERS_PARTS = { LAUNCHERS_PARTS_WINDOWS, LAUNCHERS_PARTS_UNIX };
        static final String[] LAUNCHERS_JAR_PREFIXES = { "set WRAPPER_JAR=\"%MAVEN_PROJECTBASEDIR%\\", "\"$MAVEN_PROJECTBASEDIR/"};
        private PlexusContainer container;

        @Component
        private MavenProject project;

        @Component
        private PluginDescriptor plugin;

        @Parameter(property = "baseDistributionUrl", defaultValue = DEFAULT_BASE_DISTRIBUTION_URL)
        /** DEPRECATED: Base distribution URL for the Maven binaries download. Use baseDistributionUrlList instead. */
        private String baseDistributionUrl;

        @Parameter(property = "baseDistributionUrlList", required = false)
        /** List of base distribution URLs for the Maven binaries download. Default is: https://repository.apache.org/content/repositories/releases/org/apache/maven/apache-maven */
        private List<String> baseDistributionUrlList;

        @Parameter(property = "wrapperScriptDirectory", defaultValue = "${basedir}")
        /** The wrapper scripts folder for Windows/Unix */
        private String wrapperScriptDirectory;

        @Parameter(property = "wrapperDirectory", defaultValue = "${basedir}/maven")
        /** The wrapper jar output folder. The location should be a sub-folder of the wrapper scripts directory */
        private String wrapperDirectory;

        @Parameter(property = "mavenVersion", required = false)
        /** The Maven version to use for the generate wrapper */
        private String mavenVersion;

        @Parameter(property = "verifyDownload", defaultValue = "false")
        /** Whether to verify the download using a checksum. */
        private Boolean verifyDownload;

        @Parameter(property = "checksumExtension", required = false)
        /** The checksum file extension. */
        private String checksumExtension;

        @Parameter(property = "checksumAlgorithm", defaultValue = "SHA-1")
        /** The checksum algorithm. */
        private String checksumAlgorithm;

        /**
         * Sets the plugin descriptor (Exposed for unit tests)
         *
         * @param plugin The plugin descriptor
         */
        protected void setPlugin(PluginDescriptor plugin) {
                this.plugin = plugin;
        }

        /**
         * Sets the Maven version (Exposed for unit tests only)
         *
         * @param mavenVersion The Maven version to use for the wrapper
         */
        protected void setMavenVersion(String mavenVersion) {
                this.mavenVersion = mavenVersion;
        }

        /**
         * Returns the Maven version if set (Exposed for unit tests only)
         *
         * @return The Maven version to use for the wrapper
         */
        protected String getMavenVersion() {
                return mavenVersion;
        }

        /**
         * Returns the wrapper folder (Exposed for unit tests only)
         *
         * @return the wrapper output folder
         */
        protected String getWrapperDirectory() {
                return this.wrapperDirectory;
        }

        /**
         * Returns the wrapper folder (Exposed for unit tests only)
         *
         * @return the wrapper output folder
         */
        protected String getWrapperScriptDirectory() {
                return this.wrapperScriptDirectory;
        }

        /**
         * Returns the wrapper base distribution URL (Exposed for unit tests only)
         *
         * @return the wrapper base distribution URL
         */
        protected String getBaseDistributionUrl() {
                return this.baseDistributionUrl;
        }

        /**
         * Returns the wrapper base distribution URL list (Exposed for unit tests only)
         *
         * @return the wrapper base distribution URL list
         */
        protected List<String> getBaseDistributionUrlList() {
                return this.baseDistributionUrlList;
        }

        /**
         * Returns the checksum extension (Exposed for unit tests only)
         *
         * @return the checksum extension
         */
        protected String getChecksumExtension() {
                return this.checksumExtension;
        }

        /**
         * Returns the checksum algorithm (Exposed for unit tests only)
         *
         * @return the checksum algorithm
         */
        protected String getChecksumAlgorithm() {
                return this.checksumAlgorithm;
        }

        /**
         * Returns whether to verify the download (Exposed for unit tests only)
         *
         * @return true if and only if the download should be verified
         */
        protected Boolean getVerifyDownload() {
                return this.verifyDownload;
        }

        public void contextualize(Context context) throws ContextException {
                container = (PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY);
        }

        private String composePath(String fileSeparator, Deque<String> folderDeque, String filename) {
                StringBuilder sb = new StringBuilder();
                String folderPath = pathFromDequeWithFileSeparator(folderDeque, fileSeparator);

                return sb.append(folderPath).append(filename).toString();
        }

        private String pathFromDequeWithFileSeparator(Deque<String> deque, String separator) {
                Iterator<String> it = deque.iterator();
                StringBuilder sb = new StringBuilder();

                while (it.hasNext()) {
                        String pathName = it.next();
                        sb.append(pathName);
                        sb.append(separator);
                }

                return sb.toString();
        }

        private Deque<String> buildPathDeque(File wrapperDestFolder, File baseDir) {
                File tmp = wrapperDestFolder;
                Deque<String> pathNameDeque = new ArrayDeque<String>();

                while (!tmp.equals(baseDir)) {
                        pathNameDeque.push(tmp.getName());
                        tmp = tmp.getParentFile();
                }

                return pathNameDeque;
        }

        public void execute() throws MojoExecutionException {
                try {
                        File wrapperFolder = new File(wrapperScriptDirectory);
                        if (!wrapperFolder.exists()) {
                                wrapperFolder.mkdirs();
                        }

                        Artifact mainArtifact = (Artifact) plugin.getPluginArtifact();

                        if (mavenVersion == null) {
                                RuntimeInformation runtimeInformation = container.lookup(RuntimeInformation.class);
                                mavenVersion = runtimeInformation.getMavenVersion();
                        }

                        File wrapperSupportFolder = new File(wrapperDirectory);
                        if (!wrapperSupportFolder.exists()) {
                                wrapperSupportFolder.mkdirs();
                        }

                        String baseDirPath = wrapperFolder.getAbsolutePath();
                        String wrapperFolderPath = wrapperSupportFolder.getAbsolutePath();

                        if (!wrapperFolderPath.startsWith(baseDirPath)) {
                                throw new MojoExecutionException("wrapperDirectory folder must be a sub-folder of wrapperScriptDirectory.");
                        }

                        generateWrapperMainArtefacts(wrapperFolder, wrapperSupportFolder);
                        generateWrapperProperties(wrapperSupportFolder, mainArtifact);
                } catch (ComponentLookupException cle) {
                        throw new MojoExecutionException("Could not lookup Maven runtime information", cle);
                } catch (IOException ioe) {
                        throw new MojoExecutionException("Unexpected IO Error", ioe);
                }
        }

        private void generateWrapperMainArtefacts(File baseDir, File wrapperDestFolder) throws IOException {
                final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                Deque<String> pathNameDeque = buildPathDeque(wrapperDestFolder, baseDir);

                for (int i = 0; i < LAUNCHERS_PARTS.length; i++) {
                        String[] launcherOsParts = LAUNCHERS_PARTS[i];
                        InputStream[] streams = new InputStream[launcherOsParts.length];

                        for (int j = 0; j < launcherOsParts.length; j++) {
                                String launcherFilePartName = launcherOsParts[j];

                                if (launcherFilePartName.equals("")) {
                                        String fileSeparator = LAUNCHER_FILE_SEPARATORS[i];
                                        String jarPathLocation = composePath(fileSeparator, pathNameDeque, WRAPPER_JAR_FILE_NAME);
                                        String jarPathPrefix = LAUNCHERS_JAR_PREFIXES[i];
                                        String jarPath = jarPathPrefix + jarPathLocation;
                                        streams[j] = new ByteArrayInputStream(jarPath.getBytes(ENCODING_UTF8));
                                } else {
                                        String launcherLocation = WRAPPER_TEMPLATES_LOCATION + launcherFilePartName;
                                        InputStream mvnLauncherStream = classLoader.getResourceAsStream(launcherLocation);
                                        streams[j] = mvnLauncherStream;
                                }
                        }

                        File launcherFile = new File(baseDir, LAUNCHER_FILE_BASE_NAMES[i]);
                        streamsToFile(streams, launcherFile);

                        if (!launcherFile.setExecutable(true)) {
                                getLog().warn("Could not set executable flag on file: " + launcherFile.getAbsolutePath());
                        }
                }
        }

        private void generateWrapperProperties(File wrapperSupportFolder, Artifact pluginArtifact) throws MojoExecutionException, IOException {
                List<String> effectiveBaseDistributionUrls;
                if (baseDistributionUrlList == null) {
                        effectiveBaseDistributionUrls = Collections.singletonList(baseDistributionUrl);
                } else if (baseDistributionUrlList.isEmpty()) {
                        effectiveBaseDistributionUrls = Collections.singletonList(DEFAULT_BASE_DISTRIBUTION_URL);
                } else {
                        effectiveBaseDistributionUrls = baseDistributionUrlList;
                }

                Properties props = new Properties();

                String resolvedChecksumExtension = "";
                props.put(VERIFY_DOWNLOAD_PROPERTY, verifyDownload.toString());
                if (verifyDownload) {
                        Checksum checksum = Checksum.fromAlias(checksumAlgorithm);
                        if (checksum == null) {
                                throw new MojoExecutionException(String.format("Unsupported checksum algorithm: %s", checksumAlgorithm));
                        }
                        resolvedChecksumExtension = checksumExtension == null ? checksum.getDefaultExtension() : checksumExtension;
                        props.put(CHECKSUM_ALGORITHM_PROPERTY, checksum.toString());
                }

                StringBuilder distlistsb = new StringBuilder();
                StringBuilder checksumlistsb = new StringBuilder();
                for (final String effectiveBaseDistributionUrl : effectiveBaseDistributionUrls) {
                        distlistsb.append(effectiveBaseDistributionUrl);
                        checksumlistsb.append(effectiveBaseDistributionUrl);

                        if (!effectiveBaseDistributionUrl.endsWith("/")) {
                                distlistsb.append('/');
                                checksumlistsb.append('/');
                        }

                        distlistsb.append(String.format(DIST_FILENAME_PATH_TEMPLATE, mavenVersion, mavenVersion));
                        checksumlistsb.append(String.format(CHECKSUM_FILENAME_PATH_TEMPLATE, mavenVersion, mavenVersion, resolvedChecksumExtension));

                        distlistsb.append(",");
                        checksumlistsb.append(",");
                }
                distlistsb.setLength(distlistsb.length() - 1);
                checksumlistsb.setLength(checksumlistsb.length() - 1);

                props.put(DISTRIBUTION_URL_PROPERTY, distlistsb.toString());
                if (verifyDownload) {
                        props.put(CHECKSUM_URL_PROPERTY, checksumlistsb.toString());
                }

                File file = new File(wrapperSupportFolder, WRAPPER_PROPERTIES_FILE_NAME);

                FileOutputStream fileOut = null;
                InputStream is = null;

                try {
                        is = new FileInputStream(pluginArtifact.getFile());
                        streamsToFile(new InputStream[] { is }, new File(wrapperSupportFolder, WRAPPER_JAR_FILE_NAME));
                        fileOut = new FileOutputStream(file);
                        props.store(fileOut, WRAPPER_PROPERTIES_COMMENTS);

                } finally {
                        if (fileOut != null) {
                                IOUtil.close(fileOut);
                        }

                        if (is != null) {
                                IOUtil.close(is);
                        }
                }
        }

        private static void streamsToFile(InputStream[] streams, File filePath) throws IOException {
                FileChannel outChannel = null;
                ReadableByteChannel inChannel = null;
                FileOutputStream fos = null;

                try {
                        fos = new FileOutputStream(filePath);
                        outChannel = fos.getChannel();

                        for (InputStream stream : streams) {
                                try {
                                        inChannel = Channels.newChannel(stream);
                                        ByteBuffer buffer = ByteBuffer.allocate(1024);

                                        while (inChannel.read(buffer) >= 0 || buffer.position() > 0) {
                                                buffer.flip();
                                                outChannel.write(buffer);
                                                buffer.clear();
                                        }
                                } finally {
                                        if (stream != null) {
                                                IOUtil.close(stream);
                                        }

                                        if (inChannel != null) {
                                                inChannel.close();
                                        }
                                }
                        }
                } finally {
                        if (fos != null) {
                                IOUtil.close(fos);
                        }

                        if (outChannel != null) {
                                outChannel.close();
                        }
                }
        }
}
