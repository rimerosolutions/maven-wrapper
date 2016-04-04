/*
 * Copyright 2016. Ville Koskela
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

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enumeration of checksum algorithms.
 *
 * @author Ville Koskela
 */
public enum Checksum {

    SHA1("sha1", "SHA-1")
    {
        @Override
        protected MessageDigest getDigest() throws NoSuchAlgorithmException
        {
            return MessageDigest.getInstance("SHA-1");
        }
    },
    MD5("md5", "MD5")
    {
        @Override
        protected MessageDigest getDigest() throws NoSuchAlgorithmException
        {
            return MessageDigest.getInstance("MD5");
        }
    };

    private static final Map<String, Checksum> CHECKSUM_BY_ALIAS = new HashMap<String, Checksum>();
    private static final char[] HEX_DIGITS = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
    private static final int BUFFER_SIZE = 65535;
    private final String defaultExtension;
    private final List<String> aliases;

    static
    {
        for (final Checksum checksum : Checksum.values())
        {
            for (final String alias : checksum.aliases)
            {
                assert !CHECKSUM_BY_ALIAS.containsKey(alias) : "Duplicate checksum alias detected: " + alias;
                CHECKSUM_BY_ALIAS.put(alias, checksum);
            }
        }
    }

    Checksum(String defaultExtension, String... aliases)
    {
        this.defaultExtension = defaultExtension;
        this.aliases = Arrays.asList(aliases);
    }

    public static Checksum fromAlias(String alias)
    {
        return CHECKSUM_BY_ALIAS.get(alias);
    }

    public String getDefaultExtension()
    {
        return defaultExtension;
    }

    public boolean verify(InputStream data, String checksum)
    {
        return checksum.equals(generate(data));
    }

    public String generate(InputStream data)
    {
        try
        {
            byte[] buffer = new byte[BUFFER_SIZE];
            int length = 0;
            MessageDigest digest = getDigest();
            while ((length = data.read(buffer)) > 0)
            {
                digest.update(buffer, 0, length);
            }
            return asHex(digest.digest());
        }
        catch (IOException e)
        {
            throw new RuntimeException("Could not generate checksum for stream.", e);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException("Could not generate checksum for stream.", e);
        }
    }

    protected abstract MessageDigest getDigest() throws NoSuchAlgorithmException;

    private static String asHex(byte[] bytes)
    {
        final StringBuilder sb = new StringBuilder(2 * bytes.length);
        for (final byte b : bytes) {
            sb.append(HEX_DIGITS[(b & 0xF0) >> 4]).append(HEX_DIGITS[(b & 0x0F)]);
        }
        return sb.toString();
    }
}
