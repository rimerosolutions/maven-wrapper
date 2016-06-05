package org.apache.maven.wrapper;

import static org.mockito.AdditionalMatchers.or;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.maven.wrapper.PathAssembler.LocalDistribution;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * @author Hans Dockter
 */
public class InstallerTest {
    private File testDir = new File("target/test-files/SystemPropertiesHandlerTest-" + System.currentTimeMillis());
    private Installer install;
    private File distributionDir;
    private File zipStore;
    private File mavenHomeDir;
    private File zipDestination;
    private File checksumDestination;
    private WrapperConfiguration configuration = new WrapperConfiguration();
    private Downloader download;
    private PathAssembler pathAssembler;
    private LocalDistribution localDistribution;
    private static URI WORKING_DISTRIBUTION_URI = URI.create("http://server/maven-0.9.zip");
    private static URI BROKEN_DISTRIBUTION_URI = URI.create("http://server.down/maven-0.9.zip");
    private static URI CHECKSUM_URI = URI.create("http://server/maven-0.9.zip.sha1");

    @Before
    public void setup() throws Exception {
        testDir.mkdirs();
        configuration.setZipBase(PathAssembler.PROJECT_STRING);
        configuration.setZipPath("someZipPath");
        configuration.setDistributionBase(PathAssembler.MAVEN_USER_HOME_STRING);
        configuration.setDistributionPath("someDistPath");
        configuration.setDistributionUris(Collections.singletonList(WORKING_DISTRIBUTION_URI));
        configuration.setAlwaysDownload(false);
        configuration.setAlwaysUnpack(false);
        distributionDir = new File(testDir, "someDistPath");
        mavenHomeDir = new File(distributionDir, "maven-0.9");
        zipStore = new File(testDir, "zips");
        zipDestination = new File(zipStore, "maven-0.9.zip");
        checksumDestination = new File(zipStore, "maven-0.9.zip.checksum");

        download = mock(Downloader.class);
        pathAssembler = mock(PathAssembler.class);
        localDistribution = mock(LocalDistribution.class);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(final InvocationOnMock invocationOnMock) throws Throwable {
                File downloadTarget = (File) invocationOnMock.getArguments()[1];
                FileUtils.copyFile(zipDestination, downloadTarget);
                return null;
            }
        }).when(download).download(eq(WORKING_DISTRIBUTION_URI), any(File.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(final InvocationOnMock invocationOnMock) throws Throwable {
                URI address = (URI) invocationOnMock.getArguments()[0];
                throw new IOException("Connection refused to '" + address + "'.");
            }
        }).when(download).download(eq(BROKEN_DISTRIBUTION_URI), any(File.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(final InvocationOnMock invocationOnMock) throws Throwable {
                File downloadTarget = (File) invocationOnMock.getArguments()[1];
                FileUtils.copyFile(checksumDestination, downloadTarget);
                return null;
            }
        }).when(download).download(eq(CHECKSUM_URI), any(File.class));
        when(localDistribution.getZipFile()).thenReturn(zipDestination);
        when(localDistribution.getDistributionDir()).thenReturn(distributionDir);
        when(pathAssembler.getDistribution(configuration, WORKING_DISTRIBUTION_URI)).thenReturn(localDistribution);
        when(pathAssembler.getDistribution(configuration, BROKEN_DISTRIBUTION_URI)).thenReturn(localDistribution);

        install = new Installer(download, pathAssembler);
    }

    private void createTestZip(File zipDestination) throws Exception {
        File explodedZipDir = new File(testDir, "explodedZip");
        explodedZipDir.mkdirs();
        zipDestination.getParentFile().mkdirs();
        File mavenScript = new File(explodedZipDir, "maven-0.9/bin/mvn");
        mavenScript.getParentFile().mkdirs();
        FileUtils.write(mavenScript, "something");

        zipTo(explodedZipDir, zipDestination);
    }

    private void createChecksum(final File checksumDestination, final String checksum) throws Exception {
        FileUtils.write(checksumDestination, checksum);
    }

    public void testCreateDist() throws Exception {
        File homeDir = install.createDist(configuration);

        Assert.assertEquals(mavenHomeDir, homeDir);
        Assert.assertTrue(homeDir.isDirectory());
        Assert.assertTrue(new File(homeDir, "bin/mvn").exists());
        Assert.assertTrue(zipDestination.exists());

        Assert.assertEquals(localDistribution, pathAssembler.getDistribution(configuration, WORKING_DISTRIBUTION_URI));
        Assert.assertEquals(distributionDir, localDistribution.getDistributionDir());
        Assert.assertEquals(zipDestination, localDistribution.getZipFile());

        download.download(new URI("http://some/test"), distributionDir);
        verify(download).download(new URI("http://some/test"),
        distributionDir);
    }

    @Test
    public void testCreateDistWithExistingDistribution() throws Exception {
        FileUtils.touch(zipDestination);
        mavenHomeDir.mkdirs();
        File someFile = new File(mavenHomeDir, "some-file");
        FileUtils.touch(someFile);

        File homeDir = install.createDist(configuration);

        Assert.assertEquals(mavenHomeDir, homeDir);
        Assert.assertTrue(mavenHomeDir.isDirectory());
        Assert.assertTrue(new File(homeDir, "some-file").exists());
        Assert.assertTrue(zipDestination.exists());

        Assert.assertEquals(localDistribution, pathAssembler.getDistribution(configuration, WORKING_DISTRIBUTION_URI));
        Assert.assertEquals(distributionDir, localDistribution.getDistributionDir());
        Assert.assertEquals(zipDestination, localDistribution.getZipFile());
    }

    @Test
    public void testCreateDistWithExistingDistAndZipAndAlwaysUnpackTrue() throws Exception {
        createTestZip(zipDestination);
        mavenHomeDir.mkdirs();
        File garbage = new File(mavenHomeDir, "garbage");
        FileUtils.touch(garbage);
        configuration.setAlwaysUnpack(true);

        File homeDir = install.createDist(configuration);

        Assert.assertEquals(mavenHomeDir, homeDir);
        Assert.assertTrue(mavenHomeDir.isDirectory());
        Assert.assertFalse(new File(homeDir, "garbage").exists());
        Assert.assertTrue(zipDestination.exists());

        Assert.assertEquals(localDistribution, pathAssembler.getDistribution(configuration, WORKING_DISTRIBUTION_URI));
        Assert.assertEquals(distributionDir, localDistribution.getDistributionDir());
        Assert.assertEquals(zipDestination, localDistribution.getZipFile());
    }

    @Test
    public void testCreateDistWithExistingZipAndDistAndAlwaysDownloadTrue() throws Exception {
        createTestZip(zipDestination);
        File garbage = new File(mavenHomeDir, "garbage");
        FileUtils.touch(garbage);
        configuration.setAlwaysUnpack(true);

        File homeDir = install.createDist(configuration);

        Assert.assertEquals(mavenHomeDir, homeDir);
        Assert.assertTrue(mavenHomeDir.isDirectory());
        Assert.assertTrue(new File(homeDir, "bin/mvn").exists());
        Assert.assertFalse(new File(homeDir, "garbage").exists());
        Assert.assertTrue(zipDestination.exists());

        Assert.assertEquals(localDistribution, pathAssembler.getDistribution(configuration, WORKING_DISTRIBUTION_URI));
        Assert.assertEquals(distributionDir, localDistribution.getDistributionDir());
        Assert.assertEquals(zipDestination, localDistribution.getZipFile());

        // download.download(new URI("http://some/test"), distributionDir);
        // verify(download).download(new URI("http://some/test"),
        // distributionDir);
    }

    @Test
    public void testVerifyDownload() throws Exception {
        configuration.setAlwaysDownload(true);
        configuration.setVerifyDownload(true);
        // configuration.setChecksum(Collections.singletonList(CHECKSUM_URI));
        configuration.setChecksumAlgorithm(Checksum.SHA1);

        createTestZip(zipDestination);
        createChecksum(checksumDestination, Checksum.SHA1.generate(new FileInputStream(zipDestination)));
        mavenHomeDir.mkdirs();
        File garbage = new File(mavenHomeDir, "garbage");
        FileUtils.touch(garbage);
        configuration.setAlwaysUnpack(true);

        File homeDir = install.createDist(configuration);

        Assert.assertEquals(mavenHomeDir, homeDir);
        Assert.assertTrue(mavenHomeDir.isDirectory());
        Assert.assertTrue(new File(homeDir, "bin/mvn").exists());
        Assert.assertFalse(new File(homeDir, "garbage").exists());
        Assert.assertTrue(zipDestination.exists());

        Assert.assertEquals(localDistribution, pathAssembler.getDistribution(configuration, WORKING_DISTRIBUTION_URI));
        Assert.assertEquals(distributionDir, localDistribution.getDistributionDir());
        Assert.assertEquals(zipDestination, localDistribution.getZipFile());
    }

    @Test
    public void testVerifyDownloadFails() throws Exception {
        configuration.setAlwaysDownload(true);
        configuration.setVerifyDownload(true);
        configuration.setChecksumAlgorithm(Checksum.SHA1);

        createTestZip(zipDestination);
        createChecksum(checksumDestination, "Foo-Bar");
        mavenHomeDir.mkdirs();
        File garbage = new File(mavenHomeDir, "garbage");
        FileUtils.touch(garbage);
        configuration.setAlwaysUnpack(true);

        try {
            install.createDist(configuration);
            Assert.fail("Expected RuntimeException");
        } catch (final RuntimeException e) {
            Assert.assertEquals("Maven distribution '" + WORKING_DISTRIBUTION_URI.toString() + "' failed to verify against '"
                    + CHECKSUM_URI.toString() + "'.", e.getMessage());
        }
    }

    @Test
    public void testMultipleDistributionsFirstSucceeds() throws Exception {
        configuration.setAlwaysDownload(true);
        configuration.setDistributionUris(Arrays.asList(WORKING_DISTRIBUTION_URI, BROKEN_DISTRIBUTION_URI));

        createTestZip(zipDestination);
        mavenHomeDir.mkdirs();
        File garbage = new File(mavenHomeDir, "garbage");
        FileUtils.touch(garbage);
        configuration.setAlwaysUnpack(true);

        File homeDir = install.createDist(configuration);

        Assert.assertEquals(mavenHomeDir, homeDir);
        Assert.assertTrue(mavenHomeDir.isDirectory());
        Assert.assertTrue(new File(homeDir, "bin/mvn").exists());
        Assert.assertFalse(new File(homeDir, "garbage").exists());
        Assert.assertTrue(zipDestination.exists());

        Assert.assertEquals(localDistribution, pathAssembler.getDistribution(configuration, WORKING_DISTRIBUTION_URI));
        Assert.assertEquals(distributionDir, localDistribution.getDistributionDir());
        Assert.assertEquals(zipDestination, localDistribution.getZipFile());

        verify(download).download(eq(WORKING_DISTRIBUTION_URI), any(File.class));
    }

    @Test
    public void testMultipleDistributionsFirstFail() throws Exception {
        configuration.setAlwaysDownload(true);
        configuration.setDistributionUris(Arrays.asList(BROKEN_DISTRIBUTION_URI, WORKING_DISTRIBUTION_URI));

        createTestZip(zipDestination);
        mavenHomeDir.mkdirs();
        File garbage = new File(mavenHomeDir, "garbage");
        FileUtils.touch(garbage);
        configuration.setAlwaysUnpack(true);

        File homeDir = install.createDist(configuration);

        Assert.assertEquals(mavenHomeDir, homeDir);
        Assert.assertTrue(mavenHomeDir.isDirectory());
        Assert.assertTrue(new File(homeDir, "bin/mvn").exists());
        Assert.assertFalse(new File(homeDir, "garbage").exists());
        Assert.assertTrue(zipDestination.exists());

        Assert.assertEquals(localDistribution, pathAssembler.getDistribution(configuration, WORKING_DISTRIBUTION_URI));
        Assert.assertEquals(distributionDir, localDistribution.getDistributionDir());
        Assert.assertEquals(zipDestination, localDistribution.getZipFile());

        verify(download, times(2)).download(or(eq(BROKEN_DISTRIBUTION_URI), eq(WORKING_DISTRIBUTION_URI)), any(File.class));
    }

    @Test
    public void testMultipleDistributionsBothFail() throws Exception {
        configuration.setAlwaysDownload(true);
        configuration.setDistributionUris(Arrays.asList(BROKEN_DISTRIBUTION_URI, BROKEN_DISTRIBUTION_URI));

        createTestZip(zipDestination);
        mavenHomeDir.mkdirs();
        File garbage = new File(mavenHomeDir, "garbage");
        FileUtils.touch(garbage);
        configuration.setAlwaysUnpack(true);

        try {
            install.createDist(configuration);
            Assert.fail("Expected RuntimeException");
        } catch (final IOException e) {
            Assert.assertEquals("Connection refused to '" + BROKEN_DISTRIBUTION_URI + "'.", e.getMessage());
        }

        verify(download, times(2)).download(eq(BROKEN_DISTRIBUTION_URI), any(File.class));
    }

    private static void zipTo(File directoryToZip, File destFile) throws IOException {
        FileOutputStream fos = null;
        ZipOutputStream zout = null;

        try {
            fos = new FileOutputStream(destFile);
            zout = new ZipOutputStream(fos);

            for (File f : directoryToZip.listFiles()) {
                zipFiles("", zout, f);
            }
        } finally {
            if (zout != null) {
                zout.close();
            }

            if (fos != null) {
                fos.close();
            }
        }
    }

    private static void zipFile(String prefixPath, ZipOutputStream zout, File f) throws IOException {
        InputStream fin = null;

        try {
            ZipEntry ze = new ZipEntry(prefixPath + f.getName());
            ze.setTime(f.lastModified());
            zout.putNextEntry(ze);
            fin = new FileInputStream(f);
            byte[] buffer = new byte[4096];

            for (int n; (n = fin.read(buffer)) > 0;) {
                zout.write(buffer, 0, n);
            }

            zout.closeEntry();
        } finally {
            if (fin != null) {
                fin.close();
            }
        }

    }

    private static void zipFolder(String prefixPath, ZipOutputStream zout, File f) throws IOException {
        ZipEntry ze = new ZipEntry(prefixPath + f.getName() + '/');
        ze.setTime(f.lastModified());
        zout.putNextEntry(ze);
        zout.closeEntry();

        for (File file : f.listFiles()) {
            zipFiles(prefixPath + f.getName(), zout, file);
        }
    }

    private static void zipFiles(String prefix, ZipOutputStream zout, File f) throws IOException {
        String prefixPath = prefix + "/";

        if (prefixPath.startsWith("/")) {
            prefixPath = prefixPath.substring(1);
        }

        if (f.isFile()) {
            zipFile(prefixPath, zout, f);
        } else {
            zipFolder(prefixPath, zout, f);
        }
    }

}
