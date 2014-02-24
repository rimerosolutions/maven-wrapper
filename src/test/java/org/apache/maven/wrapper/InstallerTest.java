package org.apache.maven.wrapper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Hans Dockter
 */
public class InstallerTest {
        private File testDir = new File("target/test-files/SystemPropertiesHandlerTest-" + System.currentTimeMillis());
        private Installer install;
        private Downloader downloadMock;
        private PathAssembler pathAssemblerMock;
        private boolean downloadCalled;
        private File zip;
        private File distributionDir;
        private File zipStore;
        private File mavenHomeDir;
        private File zipDestination;
        private WrapperConfiguration configuration = new WrapperConfiguration();
        private Downloader download;
        private PathAssembler pathAssembler;
        private PathAssembler.LocalDistribution localDistribution;

        @Before
        public void setup() throws Exception {
                testDir.mkdirs();

                downloadCalled = false;
                configuration.setZipBase(PathAssembler.PROJECT_STRING);
                configuration.setZipPath("someZipPath");
                configuration.setDistributionBase(PathAssembler.MAVEN_USER_HOME_STRING);
                configuration.setDistributionPath("someDistPath");
                configuration.setDistribution(new URI("http://server/maven-0.9.zip"));
                configuration.setAlwaysDownload(false);
                configuration.setAlwaysUnpack(false);
                distributionDir = new File(testDir, "someDistPath");
                mavenHomeDir = new File(distributionDir, "maven-0.9");
                zipStore = new File(testDir, "zips");
                zipDestination = new File(zipStore, "maven-0.9.zip");

                download = mock(Downloader.class);
                pathAssembler = mock(PathAssembler.class);
                localDistribution = mock(PathAssembler.LocalDistribution.class);

                when(localDistribution.getZipFile()).thenReturn(zipDestination);
                when(localDistribution.getDistributionDir()).thenReturn(distributionDir);
                when(pathAssembler.getDistribution(configuration)).thenReturn(localDistribution);

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

        public void testCreateDist() throws Exception {
                File homeDir = install.createDist(configuration);

                Assert.assertEquals(mavenHomeDir, homeDir);
                Assert.assertTrue(homeDir.isDirectory());
                Assert.assertTrue(new File(homeDir, "bin/mvn").exists());
                Assert.assertTrue(zipDestination.exists());

                Assert.assertEquals(localDistribution, pathAssembler.getDistribution(configuration));
                Assert.assertEquals(distributionDir, localDistribution.getDistributionDir());
                Assert.assertEquals(zipDestination, localDistribution.getZipFile());

                // download.download(new URI("http://some/test"), distributionDir);
                // verify(download).download(new URI("http://some/test"), distributionDir);
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

                Assert.assertEquals(localDistribution, pathAssembler.getDistribution(configuration));
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

                Assert.assertEquals(localDistribution, pathAssembler.getDistribution(configuration));
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

                Assert.assertEquals(localDistribution, pathAssembler.getDistribution(configuration));
                Assert.assertEquals(distributionDir, localDistribution.getDistributionDir());
                Assert.assertEquals(zipDestination, localDistribution.getZipFile());

                // download.download(new URI("http://some/test"), distributionDir);
                // verify(download).download(new URI("http://some/test"), distributionDir);
        }

        private static void zipTo(File directoryToZip, File destFile) throws IOException {
                String zipFilename = destFile.getName();
                String zipBasename = zipFilename.substring(0, zipFilename.lastIndexOf("."));

                FileOutputStream fos = null;
                ZipOutputStream zout = null;

                try {
                        fos = new FileOutputStream(destFile);
                        zout = new ZipOutputStream(fos);

                        for (File f : directoryToZip.listFiles()) {
                                zipFiles("", zout, f);
                        }
                }
                finally {
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
                String prefixPath = prefix  + "/";

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
