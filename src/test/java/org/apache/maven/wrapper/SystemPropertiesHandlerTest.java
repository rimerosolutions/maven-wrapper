package org.apache.maven.wrapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SystemPropertiesHandlerTest {
    private File tmpDir = new File("target/test-files/SystemPropertiesHandlerTest");

    @Before
    public void setupTempDir() {
        tmpDir.mkdirs();
    }

    @After
    public void deleteTempDir() {
        if (tmpDir.exists()) {
            try {
                FileUtils.deleteDirectory(tmpDir);
            } catch (IOException ioe) {
                throw new RuntimeException("Could not cleanup temp directory." + tmpDir.getAbsolutePath());
            }
        }
    }

    @Test
    public void testParsePropertiesFile() throws Exception {
        File propFile = new File(tmpDir, "props");
        Properties props = new Properties();
        props.put("a", "b");
        props.put("c", "d");
        props.put("e", "f");

        FileOutputStream fos = null;

        try {
            fos = new FileOutputStream(propFile);
            props.store(fos, "");
        } finally {
            IOUtils.closeQuietly(fos);
        }

        Map<String, String> expected = new HashMap<String, String>(3);
        expected.put("a", "b");
        expected.put("c", "d");
        expected.put("e", "f");

        assertThat(SystemPropertiesHandler.getSystemProperties(propFile), equalTo(expected));
    }

    @Test
    public void ifNoPropertyFileExistShouldReturnEmptyMap() {
        Map<String, String> expected = new HashMap<String, String>();
        assertThat(SystemPropertiesHandler.getSystemProperties(new File(tmpDir, "unknown")), equalTo(expected));
    }
}
