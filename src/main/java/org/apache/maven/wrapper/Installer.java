/*
 * Copyright 2010 the original author or authors.
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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.wrapper.PathAssembler.LocalDistribution;

/**
 * @author Hans Dockter
 */
public class Installer {
    private static final Logger LOG = Logger.getLogger(Installer.class.getName());
    public static final String DEFAULT_DISTRIBUTION_PATH = "wrapper/dists";

    private final Downloader download;

    private final PathAssembler pathAssembler;

    public Installer(Downloader download, PathAssembler pathAssembler) {
        this.download = download;
        this.pathAssembler = pathAssembler;
    }

    public File createDist(WrapperConfiguration configuration) throws Exception {
        Exception failure = null;

	for (URI distributionUri : configuration.getDistributionUris()) {
            try {
                return createDistFromUri(configuration, distributionUri);
            } catch (Exception e) {
                LOG.warning(String.format("Maven distribution '%s' failed: %s", distributionUri, e.getMessage()));
		
                if (failure == null) {
                    failure = e;
                }
            }
        }

        if (failure == null) {
            throw new RuntimeException("No distributions configured. Expected to find at least 1 distribution.");
        }

        throw failure;
    }

    private File createDistFromUri(final WrapperConfiguration configuration, final URI distributionUrl) throws Exception {
        boolean alwaysDownload = configuration.isAlwaysDownload();
        boolean alwaysUnpack = configuration.isAlwaysUnpack();

        LocalDistribution localDistribution = pathAssembler.getDistribution(configuration, distributionUrl);
        File localZipFile = localDistribution.getZipFile();
        boolean downloaded = false;
	
        if (alwaysDownload || !localZipFile.exists()) {
            File tmpZipFile = new File(localZipFile.getParentFile(), localZipFile.getName() + ".part");
            tmpZipFile.delete();
            LOG.info(String.format("Downloading %s", distributionUrl));
            download.download(distributionUrl, tmpZipFile);
	    
            if (configuration.isVerifyDownload()) {
                File localChecksumFile = new File(localZipFile.getParentFile(), localZipFile.getName() + ".checksum");
                verifyDistribution(configuration.getChecksumAlgorithm(), distributionUrl, localChecksumFile, tmpZipFile);
            }
	    
            tmpZipFile.renameTo(localZipFile);
            downloaded = true;
        }

        File distDir = localDistribution.getDistributionDir();
        List<File> dirs = listDirs(distDir);

        if (downloaded || alwaysUnpack || dirs.isEmpty()) {
            for (File dir : dirs) {
                LOG.info(String.format("Deleting directory %s", dir.getAbsolutePath()));
                deleteDir(dir);
            }
	    
            LOG.info(String.format("Unzipping %s to %s", localZipFile.getAbsolutePath(), distDir.getAbsolutePath()));
            unzip(localZipFile, distDir);
            dirs = listDirs(distDir);

	    if (dirs.isEmpty()) {
                throw new RuntimeException(
                        String.format("Maven distribution '%s' does not contain any directories. Expected to find exactly 1 directory.",
                                distributionUrl));
            }
	    
            setExecutablePermissions(dirs.get(0));
        }
	
        if (dirs.size() != 1) {
            throw new RuntimeException(String.format(
                    "Maven distribution '%s' contains too many directories. Expected to find exactly 1 directory.", distributionUrl));
        }
	
        return dirs.get(0);
    }

    private void verifyDistribution(Checksum checksum,
				    URI distributionUri,
				    File localChecksumFile,
				    File distributionZipFile) throws Exception {
        File tmpZipFile = new File(localChecksumFile.getParentFile(), localChecksumFile.getName() + ".part");
        tmpZipFile.delete();

	URI checksumUri = URI.create(String.format("%s.%s", distributionUri.toString(), checksum.getDefaultExtension()));
        LOG.info(String.format("Verifying download with %s", checksumUri));
        download.download(checksumUri, tmpZipFile);
        tmpZipFile.renameTo(localChecksumFile);

        BufferedReader checksumReader = null;

        try {
            checksumReader = new BufferedReader(new InputStreamReader(new FileInputStream(localChecksumFile), "UTF-8"));
	    
            if (!checksum.verify(new FileInputStream(distributionZipFile), checksumReader.readLine())) {
                throw new RuntimeException(
                        String.format("Maven distribution '%s' failed to verify against '%s'.", distributionUri, checksumUri));
            }
        } finally {
            if (checksumReader != null) {
                checksumReader.close();
            }
        }
    }

    private List<File> listDirs(File distDir) {
        List<File> dirs = new ArrayList<File>();
	
        if (distDir.exists()) {
            for (File file : distDir.listFiles()) {
                if (file.isDirectory()) {
                    dirs.add(file);
                }
            }
        }
	
        return dirs;
    }

    private void setExecutablePermissions(File mavenHome) {
        if (isWindows()) {
            return;
        }

        File mavenCommand = new File(mavenHome, "bin/mvn");
        String errorMessage = null;

        try {
            ProcessBuilder pb = new ProcessBuilder("chmod", "755", mavenCommand.getCanonicalPath());
            Process p = pb.start();

            if (p.waitFor() == 0) {
                LOG.info(String.format("Set executable permissions for: %s", mavenCommand.getAbsolutePath()));
            } else {
                BufferedReader is = null;

		try {
		    is = new BufferedReader(new InputStreamReader(p.getInputStream()));
		    StringWriter sw = new StringWriter();
		
		    for (String line; (line = is.readLine()) != null; ) {
			sw.write(String.format("%s%n", line));
		    }
		
		    errorMessage = sw.toString();
		    sw.close();
		} finally {
		    if (is != null) {
			is.close();
		    }
		}
            }
        } catch (IOException e) {
            errorMessage = e.getMessage();
        } catch (InterruptedException e) {
            errorMessage = e.getMessage();
        }

        if (errorMessage != null) {
            LOG.warning("Could not set executable permissions for: " + mavenCommand.getAbsolutePath());
            LOG.warning("Please do this manually if you want to use maven.");
        }
    }

    private boolean isWindows() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.US);

        if (osName.indexOf("windows") > -1) {
            return true;
        }

        return false;
    }

    private boolean deleteDir(File dir) {
	if (dir == null) {
	    throw new IllegalArgumentException("Cannot delete null directory");
	}

	Deque<File> fileDeque = new LinkedList<File>();
	File[] currentFileList;
	fileDeque.offerFirst(dir);
	
	while (!fileDeque.isEmpty()) {
	    if (fileDeque.peekFirst().isDirectory()) {
		currentFileList = fileDeque.peekFirst().listFiles();
		
		if (currentFileList != null && currentFileList.length > 0) {
		    for (File currentFile : currentFileList) {
			fileDeque.offerFirst(currentFile);
		    }
		} else {
		    if (!deleteFile(fileDeque.pollFirst())) {
			return false;
		    }
		}
	    } else {
		if (!deleteFile(fileDeque.pollFirst())) {
		    return false;
		}
	    }
	}

	return true;
    }

    private boolean deleteFile(File file) {
	return file != null && file.exists() && file.delete();
    }
    
    public void unzip(File zip, File dest) throws IOException {
        Enumeration<? extends ZipEntry> entries;
        ZipFile zipFile;

        zipFile = new ZipFile(zip);

        entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) entries.nextElement();

            if (entry.isDirectory()) {
                (new File(dest, entry.getName())).mkdirs();
                continue;
            }

	    copyInputStream(zipFile.getInputStream(entry), new BufferedOutputStream(new FileOutputStream(new File(dest, entry.getName()))));
        }
	
        zipFile.close();
    }

    public void copyInputStream(InputStream in, OutputStream out) throws IOException {
        IOException ioe = null;

        try {
            byte[] buffer = new byte[2048];
            int len;

            while ((len = in.read(buffer)) >= 0) {
                out.write(buffer, 0, len);
            }
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                if (ioe == null) {
                    ioe = ex;
                }
            }

            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ex) {
                if (ioe == null) {
                    ioe = ex;
                }
            }
        }

	if (ioe != null) {
	    throw ioe;
	}
    }

}
