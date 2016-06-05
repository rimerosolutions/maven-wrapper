/*
 * Copyright 2007-2009 the original author or authors.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * @author Hans Dockter
 */
public class DefaultDownloader implements Downloader {

    private static final int PROGRESS_CHUNK = 20000;
    private static final int BUFFER_SIZE = 10000;
    private final String applicationName;
    private final String applicationVersion;

    public DefaultDownloader(String applicationName, String applicationVersion) {
        this.applicationName = applicationName;
        this.applicationVersion = applicationVersion;
        configureProxyAuthentication();
    }

    private void configureProxyAuthentication() {
        if (System.getProperty("http.proxyUser") != null) {
            Authenticator.setDefault(new SystemPropertiesProxyAuthenticator());
        }
    }

    public void download(URI address, File destination) throws Exception {
        if (destination.exists()) {
            return;
        }
	
        destination.getParentFile().mkdirs();
        downloadInternal(address, destination);
    }

    private void downloadInternal(URI address, File destination) throws IOException {
        WritableByteChannel out = null;
        URLConnection conn;
        ReadableByteChannel in = null;
	
        try {	    
            URL url = address.toURL();
            out = Channels.newChannel(new FileOutputStream(destination));
            conn = url.openConnection();
            String userAgentValue = calculateUserAgent();
            conn.setRequestProperty("User-Agent", userAgentValue);
            in = Channels.newChannel(conn.getInputStream());
            int numRead;
            long progressCounter = 0;

	    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
	    	    
	    while ((numRead = in.read(buffer)) >= 0 || buffer.position() > 0) {
                progressCounter += numRead;
		buffer.flip();
		
                if (progressCounter / PROGRESS_CHUNK > 0) {
                    System.out.print(".");
                    progressCounter = progressCounter - PROGRESS_CHUNK;
                }
		
                out.write(buffer);
		buffer.clear();
            }
        } finally {
            System.out.println("");

	    if (in != null) {
                in.close();
            }

	    if (out != null) {
                out.close();
            }
        }
    }

    private String calculateUserAgent() {
        String javaVendor = System.getProperty("java.vendor");
        String javaVersion = System.getProperty("java.version");
        String javaVendorVersion = System.getProperty("java.vm.version");
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        return String.format("%s/%s (%s;%s;%s) (%s;%s;%s)",
			     applicationName,
			     applicationVersion,
			     osName,
			     osVersion,
			     osArch,
			     javaVendor,
			     javaVersion,
                             javaVendorVersion);
    }

    private static class SystemPropertiesProxyAuthenticator extends Authenticator {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
	    final String username = System.getProperty("http.proxyUser");
	    final char[] password = System.getProperty("http.proxyPassword", "").toCharArray();
	    
            return new PasswordAuthentication(username, password);
        }
    }
}
