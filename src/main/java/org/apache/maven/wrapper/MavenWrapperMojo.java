/*
 * Copyright 2013. Yves Zoundi
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Iterator;
import java.util.Properties;
import java.util.Deque;
import java.util.ArrayDeque;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

@Mojo(name = "wrapper", requiresProject = true)
/**
 * Generates a Maven Command Wrapper for the current project.
 *
 * @author Yves Zoundi
 */
public class MavenWrapperMojo extends AbstractMojo implements Contextualizable {

        static final String DIST_FILENAME_PATH_TEMPLATE = "%s/apache-maven-%s-bin.zip";
        static final String WRAPPER_PROPERTIES_FILE_NAME = "maven-wrapper.properties";
        static final String WRAPPER_JAR_FILE_NAME = "maven-wrapper.jar";
        static final String DISTRIBUTION_URL_PROPERTY = "distributionUrl";

        static final String[] LAUNCHER_FILE_SEPARATORS = {"\\", "/"};
        static final String[] LAUNCHER_FILE_BASE_NAMES = {"mvnw.bat", "mvnw"};
        static final String[] LAUNCHERS_PARTS_WINDOWS = {"mvnw_header.bat", "", "mvnw_footer.bat"};
        static final String[] LAUNCHERS_PARTS_UNIX = {"mvnw_header", "", "mvnw_footer"};
        static final String[][] LAUNCHERS_PARTS = { LAUNCHERS_PARTS_WINDOWS, LAUNCHERS_PARTS_UNIX};

        private PlexusContainer container;

        @Component
        private MavenProject project;

        @Component
        private PluginDescriptor plugin;

        @Parameter(property = "baseDistributionUrl", defaultValue = "https://repository.apache.org/content/repositories/releases/org/apache/maven/apache-maven")
        /** Base distribution URL for the Maven binaries download. The default base distribution URL is https://repository.apache.org/content/repositories/releases/org/apache/maven/apache-maven */
        private String baseDistributionUrl;

        @Parameter(property = "wrapperDirectory", defaultValue = "${basedir}/maven")
        /** The wrapper jar output folder */
        private String wrapperDirectory;

        private static final String WRAPPER_TEMPLATES_LOCATION = "com/rimerosolutions/maven/plugins/wrapper/";

        public void contextualize(Context context) throws ContextException {
                container = (PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY);
        }

        private String normalizePath(String fileSeparator, Deque<String> deque, String filename) {
                return joinPathSeparatorWithReversedDequeIterator(fileSeparator, deque) + filename;
        }

        private String joinPathSeparatorWithReversedDequeIterator(String separator, Deque<String> deque) {
                Iterator<String> it = deque.iterator();

                StringBuilder sb = new StringBuilder();

                while (it.hasNext()) {
                        String pathName = it.next();
                        sb.append(pathName);
                        sb.append(separator);
                }

                sb.insert(0, "." + separator);

                return sb.toString();
        }

        private Deque<String> buildPathDeque(File wrapperDestFolder, File baseDir) {
                File tmp = wrapperDestFolder;
                StringBuilder baseWrapperPathBuilder = new StringBuilder();
                Deque<String> pathNameDeque = new ArrayDeque<String>();

                while(!tmp.equals(baseDir)) {
                        pathNameDeque.push(tmp.getName());
                        tmp = tmp.getParentFile();
                }

                return pathNameDeque;
        }

        public void execute() throws MojoExecutionException {
                try {
                        File baseDir = project.getBasedir();
                        Artifact mainArtifact = (Artifact) plugin.getPluginArtifact();

                        // Get the maven version information via Plexus
                        RuntimeInformation runtimeInformation = container.lookup(RuntimeInformation.class);
                        final String mavenVersion = runtimeInformation.getMavenVersion();

                        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

                        File wrapperDestFolder = new File(wrapperDirectory);
                        wrapperDestFolder.mkdirs();

                        String baseDirPath = baseDir.getAbsolutePath();
                        String wrapperFolderPath = wrapperDestFolder.getAbsolutePath();

                        int pos = wrapperFolderPath.indexOf(baseDirPath);

                        if(pos == -1) {
                                throw new MojoExecutionException("The wrapper output folder must be relative to the project folder");
                        }

                        Deque<String> pathNameDeque = buildPathDeque(wrapperDestFolder, baseDir);

                        for (int i = 0; i < LAUNCHERS_PARTS.length; i++) {
                                String[] launcherOsParts = LAUNCHERS_PARTS[i];
                                InputStream[] streams = new InputStream[launcherOsParts.length];

                                for (int j = 0; j < launcherOsParts.length; j++) {
                                        String launcherFilePartName = launcherOsParts[j];

                                        if (launcherFilePartName.equals("")) {
                                                String fileSeparator = LAUNCHER_FILE_SEPARATORS[i];
                                                String jarPath = normalizePath(fileSeparator, pathNameDeque, WRAPPER_JAR_FILE_NAME);
                                                streams[j] = new ByteArrayInputStream(jarPath.getBytes("UTF-8"));
                                        }
                                        else {
                                                InputStream mvnLauncherStream = classLoader.getResourceAsStream(WRAPPER_TEMPLATES_LOCATION + launcherFilePartName);
                                                streams[j] = mvnLauncherStream;
                                        }
                                }

                                File launcherFile = new File(baseDir, LAUNCHER_FILE_BASE_NAMES[i]);
                                streamsToFile(streams, launcherFile);

                                if (!launcherFile.setExecutable(true)) {
                                        getLog().warn("Could not set executable flag on file: " + launcherFile.getAbsolutePath());
                                }
                        }

                        Properties props = new Properties();
                        StringBuilder sb = new StringBuilder(baseDistributionUrl);

                        if (!baseDistributionUrl.endsWith("/")) {
                                sb.append('/');
                        }

                        sb.append(DIST_FILENAME_PATH_TEMPLATE);

                        props.put(DISTRIBUTION_URL_PROPERTY, String.format(sb.toString(), mavenVersion, mavenVersion));
                        File file = new File(wrapperDestFolder, WRAPPER_PROPERTIES_FILE_NAME);

                        FileOutputStream fileOut = null;
                        InputStream is = null;

                        try {
                                is = new FileInputStream(mainArtifact.getFile());
                                streamsToFile(new InputStream[] {is}, new File(wrapperDestFolder, WRAPPER_JAR_FILE_NAME));
                                fileOut = new FileOutputStream(file);
                                props.store(fileOut, "Maven download properties");

                        }
                        finally {
                                if (fileOut != null) {
                                        fileOut.close();
                                }

                                if (is != null) {
                                        is.close();
                                }
                        }
                }
                catch (ComponentLookupException cle) {
                        throw new MojoExecutionException("Could not lookup Maven runtime information", cle);
                }
                catch (IOException ioe) {
                        throw new MojoExecutionException("Unexpected IO Error", ioe);
                }
        }

        private static void streamsToFile(InputStream[] streams, File filePath) throws IOException {
                FileChannel outChannel = null;
                ReadableByteChannel inChannel = null;
                FileOutputStream fos = null;

                try {
                        fos = new FileOutputStream(filePath);
                        outChannel = fos.getChannel();

                        for (InputStream stream: streams) {
                                try {
                                        inChannel = Channels.newChannel(stream);
                                        ByteBuffer buffer = ByteBuffer.allocate(1024);

                                        while (inChannel.read(buffer) >= 0 || buffer.position() > 0) {
                                                buffer.flip();
                                                outChannel.write(buffer);
                                                buffer.clear();
                                        }
                                }
                                finally {
                                        if (inChannel != null) {
                                                inChannel.close();
                                        }

                                        if (stream != null) {
                                                stream.close();
                                        }
                                }
                        }
                }
                finally {
                        if (outChannel != null) {
                                outChannel.close();
                        }

                        if (fos != null) {
                                fos.close();
                        }
                }
        }
}
