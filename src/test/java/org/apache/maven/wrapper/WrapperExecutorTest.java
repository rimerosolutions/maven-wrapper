package org.apache.maven.wrapper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class WrapperExecutorTest {
    private final Installer install;
    private final BootstrapMainStarter start;
    private File propertiesFile;
    private Properties properties = new Properties();
    private File testDir = new File("target/test-files/SystemPropertiesHandlerTest-" + System.currentTimeMillis());
    private File mockInstallDir = new File(testDir, "mock-dir");

    public WrapperExecutorTest() throws Exception {
        install = mock(Installer.class);
        when(install.createDist(Mockito.any(WrapperConfiguration.class))).thenReturn(mockInstallDir);
        start = mock(BootstrapMainStarter.class);

        testDir.mkdirs();
        propertiesFile = new File(testDir, "maven/wrapper/maven-wrapper.properties");

        properties.put("distributionUrl", "http://server/test/maven.zip");
        properties.put("distributionBase", "testDistBase");
        properties.put("distributionPath", "testDistPath");
        properties.put("zipStoreBase", "testZipBase");
        properties.put("zipStorePath", "testZipPath");
        properties.put("verifyDownload", Boolean.TRUE.toString());
        properties.put("checksumAlgorithm", Checksum.MD5.toString());
        properties.put("checksumUrl", "http://server/test/maven.zip.md5");

        writePropertiesFile(properties, propertiesFile, "header");
    }

    @Test
    public void loadWrapperMetadataFromFile() throws Exception {
        WrapperExecutor wrapper = WrapperExecutor.forWrapperPropertiesFile(propertiesFile);

        Assert.assertEquals(1, wrapper.getDistributionUris().size());
        Assert.assertEquals(new URI("http://server/test/maven.zip"), wrapper.getDistributionUris().get(0));
        Assert.assertEquals(1, wrapper.getConfiguration().getDistributionUris().size());
        Assert.assertEquals(new URI("http://server/test/maven.zip"), wrapper.getConfiguration().getDistributionUris().get(0));
        Assert.assertEquals("testDistBase", wrapper.getConfiguration().getDistributionBase());
        Assert.assertEquals("testDistPath", wrapper.getConfiguration().getDistributionPath());
        Assert.assertEquals("testZipBase", wrapper.getConfiguration().getZipBase());
        Assert.assertEquals("testZipPath", wrapper.getConfiguration().getZipPath());
        Assert.assertTrue(wrapper.getConfiguration().isVerifyDownload());
        Assert.assertEquals(Checksum.MD5, wrapper.getConfiguration().getChecksumAlgorithm());
    }

    @Test
    public void loadWrapperMetadataFromDirectory() throws Exception {
        WrapperExecutor wrapper = WrapperExecutor.forProjectDirectory(testDir);

        Assert.assertEquals(1, wrapper.getDistributionUris().size());
        Assert.assertEquals(new URI("http://server/test/maven.zip"), wrapper.getDistributionUris().get(0));
        Assert.assertEquals(1, wrapper.getConfiguration().getDistributionUris().size());
        Assert.assertEquals(new URI("http://server/test/maven.zip"), wrapper.getConfiguration().getDistributionUris().get(0));
        Assert.assertEquals("testDistBase", wrapper.getConfiguration().getDistributionBase());
        Assert.assertEquals("testDistPath", wrapper.getConfiguration().getDistributionPath());
        Assert.assertEquals("testZipBase", wrapper.getConfiguration().getZipBase());
        Assert.assertEquals("testZipPath", wrapper.getConfiguration().getZipPath());
        Assert.assertTrue(wrapper.getConfiguration().isVerifyDownload());
        Assert.assertEquals(Checksum.MD5, wrapper.getConfiguration().getChecksumAlgorithm());
    }

    @Test
    public void useDefaultMetadataNoPropertiesFile() throws Exception {
        WrapperExecutor wrapper = WrapperExecutor.forProjectDirectory(new File(testDir, "unknown"));

        Assert.assertNull(wrapper.getDistributionUris());
        Assert.assertNull(wrapper.getConfiguration().getDistributionUris());
        Assert.assertEquals(PathAssembler.MAVEN_USER_HOME_STRING, wrapper.getConfiguration().getDistributionBase());
        Assert.assertEquals(Installer.DEFAULT_DISTRIBUTION_PATH, wrapper.getConfiguration().getDistributionPath());
        Assert.assertEquals(PathAssembler.MAVEN_USER_HOME_STRING, wrapper.getConfiguration().getZipBase());
        Assert.assertEquals(Installer.DEFAULT_DISTRIBUTION_PATH, wrapper.getConfiguration().getZipPath());
        Assert.assertFalse(wrapper.getConfiguration().isVerifyDownload());
        Assert.assertNull(wrapper.getConfiguration().getChecksumAlgorithm());
    }

    @Test
    public void propertiesFileOnlyContainsDistURL() throws Exception {

        properties = new Properties();
        properties.put("distributionUrl", "http://server/test/maven.zip");
        writePropertiesFile(properties, propertiesFile, "header");

        WrapperExecutor wrapper = WrapperExecutor.forWrapperPropertiesFile(propertiesFile);

        Assert.assertEquals(1, wrapper.getDistributionUris().size());
        Assert.assertEquals(new URI("http://server/test/maven.zip"), wrapper.getDistributionUris().get(0));
        Assert.assertEquals(1, wrapper.getConfiguration().getDistributionUris().size());
        Assert.assertEquals(new URI("http://server/test/maven.zip"), wrapper.getConfiguration().getDistributionUris().get(0));
        Assert.assertEquals(PathAssembler.MAVEN_USER_HOME_STRING, wrapper.getConfiguration().getDistributionBase());
        Assert.assertEquals(Installer.DEFAULT_DISTRIBUTION_PATH, wrapper.getConfiguration().getDistributionPath());
        Assert.assertEquals(PathAssembler.MAVEN_USER_HOME_STRING, wrapper.getConfiguration().getZipBase());
        Assert.assertEquals(Installer.DEFAULT_DISTRIBUTION_PATH, wrapper.getConfiguration().getZipPath());
        Assert.assertFalse(wrapper.getConfiguration().isVerifyDownload());
        Assert.assertNull(wrapper.getConfiguration().getChecksumAlgorithm());
    }

    @Test
    public void executeInstallAndLaunch() throws Exception {
        WrapperExecutor wrapper = WrapperExecutor.forProjectDirectory(propertiesFile);

        wrapper.execute(new String[] { "arg" }, install, start);
        verify(install).createDist(Mockito.any(WrapperConfiguration.class));
        verify(start).start(new String[] { "arg" }, mockInstallDir);
    }

    @Test
    public void failWhenDistNotSetInProperties() throws Exception {
        properties = new Properties();
        writePropertiesFile(properties, propertiesFile, "header");

        try {
            WrapperExecutor.forWrapperPropertiesFile(propertiesFile);
            Assert.fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            Assert.assertEquals("Could not load wrapper properties from '" + propertiesFile + "'.", e.getMessage());
            Assert.assertEquals("No value with key 'distributionUrl' specified in wrapper properties file '" + propertiesFile + "'.",
                    e.getCause().getMessage());
        }
    }

    @Test
    public void failWhenPropertiesFileDoesNotExist() {
        propertiesFile = new File(testDir, "unknown.properties");

        try {
            WrapperExecutor.forWrapperPropertiesFile(propertiesFile);
            Assert.fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            Assert.assertEquals("Wrapper properties file '" + propertiesFile + "' does not exist.", e.getMessage());
        }
    }

    @Test
    public void failWhenVerifyDownloadWithoutAlgorithm() throws Exception {
        properties = new Properties();
        properties.put("distributionUrl", "http://server/test/maven.zip");
        properties.put("verifyDownload", Boolean.TRUE.toString());
        properties.put("checksumUrl", "http://server/test/maven.md5");
        writePropertiesFile(properties, propertiesFile, "header");

        try {
            WrapperExecutor.forWrapperPropertiesFile(propertiesFile);
            Assert.fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            Assert.assertEquals("Could not load wrapper properties from '" + propertiesFile + "'.", e.getMessage());
            Assert.assertEquals("No value with key 'checksumAlgorithm' specified in wrapper properties file '" + propertiesFile + "'.",
                    e.getCause().getMessage());
        }
    }

    @Test
    public void failWhenVerifyDownloadWithInvalidAlgorithm() throws Exception {
        properties = new Properties();
        properties.put("distributionUrl", "http://server/test/maven.zip");
        properties.put("verifyDownload", Boolean.TRUE.toString());
        properties.put("checksumAlgorithm", "FOO_BAR");
        properties.put("checksumExtension", "md5");
        writePropertiesFile(properties, propertiesFile, "header");

        try {
            WrapperExecutor.forWrapperPropertiesFile(propertiesFile);
            Assert.fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            Assert.assertEquals("Could not load wrapper properties from '" + propertiesFile + "'.", e.getMessage());
            Assert.assertEquals("No enum constant org.apache.maven.wrapper.Checksum.FOO_BAR", e.getCause().getMessage());
        }
    }

    @Test
    public void testRelativeDistUrl() throws Exception {
        properties = new Properties();
        properties.put("distributionUrl", "some/relative/url/to/bin.zip");
        writePropertiesFile(properties, propertiesFile, "header");

        WrapperExecutor wrapper = WrapperExecutor.forWrapperPropertiesFile(propertiesFile);
        Assert.assertEquals(1, wrapper.getDistributionUris().size());
        Assert.assertNotEquals("some/relative/url/to/bin.zip", wrapper.getDistributionUris().get(0).getSchemeSpecificPart());
        Assert.assertTrue(wrapper.getDistributionUris().get(0).getSchemeSpecificPart().endsWith("some/relative/url/to/bin.zip"));
    }

    @Test
    public void testRelativeChecksumUrl() throws Exception {
        properties = new Properties();
        properties.put("distributionUrl", "http://server/test/maven.zip");
        properties.put("verifyDownload", Boolean.TRUE.toString());
        properties.put("checksumUrl", "some/relative/url/to/bin.md5");
        properties.put("checksumAlgorithm", Checksum.MD5.toString());
        writePropertiesFile(properties, propertiesFile, "header");

        WrapperExecutor wrapper = WrapperExecutor.forWrapperPropertiesFile(propertiesFile);
	
	Assert.assertNotNull(wrapper);
	Assert.assertNotNull(wrapper.getConfiguration());
	Assert.assertThat(wrapper.getConfiguration().getChecksumAlgorithm(), Matchers.is(Checksum.MD5));
	Assert.assertTrue(wrapper.getConfiguration().isVerifyDownload());
	
        // Assert.assertEquals(1, wrapper.getConfiguration().getChecksum().size());
        // Assert.assertNotEquals("some/relative/url/to/bin.md5", wrapper.getConfiguration().getChecksum().get(0).getSchemeSpecificPart());
        // Assert.assertTrue(wrapper.getConfiguration().getChecksum().get(0).getSchemeSpecificPart().endsWith("some/relative/url/to/bin.md5"));
    }

    private void writePropertiesFile(Properties properties, File propertiesFile, String message) throws Exception {
        propertiesFile.getParentFile().mkdirs();

        OutputStream outStream = null;

        try {
            outStream = new FileOutputStream(propertiesFile);
            properties.store(outStream, message);
        } finally {
            IOUtils.closeQuietly(outStream);
        }
    }
}
