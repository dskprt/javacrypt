package com.github.dskprt.javacrypt.api;

import com.github.dskprt.javacrypt.classloaders.ByteClassLoader;
import com.github.dskprt.javacrypt.encryption.AES;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.javatuples.Pair;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Javacrypt {

    /**
     * Encrypts a JAR file
     *
     * @param jar      the JAR file
     * @param password encryption password
     * @param charset  charset for the password
     * @return the encrypted file as the first item and the IV as the second item
     * @throws Exception when something goes wrong
     */
    public static Pair<File, byte[]> encryptJar(JarFile jar, String password, Charset charset) throws Exception {
        String newFileName = FilenameUtils.removeExtension(jar.getName()) + "-encrypted.jar";

        ZipOutputStream newJar;
        newJar = new ZipOutputStream(new FileOutputStream(newFileName));

        Enumeration<JarEntry> entries = jar.entries();
        byte[] iv = AES.createIV();

        while(entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();

            if(entry.getName().endsWith(".class")) {
                byte[] cls = IOUtils.toByteArray(jar.getInputStream(entry));
                byte[] encrypted = AES.encrypt(cls, password, iv, charset);

                newJar.putNextEntry(new ZipEntry(entry.getName()));
                newJar.write(encrypted);
            } else {
                newJar.putNextEntry(new ZipEntry(entry.getName()));
                newJar.write(IOUtils.toByteArray(jar.getInputStream(entry)));
            }

            newJar.closeEntry();
        }

        jar.close();
        newJar.close();

        return new Pair<>(new File(newFileName), iv);
    }

    /**
     * starts the encrypted JAR file
     * @param jar the JAR file
     * @param password encryption password
     * @param iv IV
     * @param charset charset for the password
     * @param main fully qualified main class
     * @param args main arguments
     * @throws Exception when something goes wrong
     */
    public static void launchJar(JarFile jar, String password, byte[] iv, Charset charset, String main, String... args) throws Exception {
        Enumeration<JarEntry> entries = jar.entries();

        HashMap<String, byte[]> classes = new HashMap<>();
        HashMap<String, byte[]> resources = new HashMap<>();

        while(entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();

            if(entry.getName().endsWith(".class")) {
                byte[] cls = IOUtils.toByteArray(jar.getInputStream(entry));
                byte[] decrypted = AES.decrypt(cls, password, iv, charset);

                classes.put(FilenameUtils.removeExtension(entry.getName()), decrypted);

            } else {
                resources.put(entry.getName(), IOUtils.toByteArray(jar.getInputStream(entry)));
            }
        }

        jar.close();

        ByteClassLoader classLoader = new ByteClassLoader(jar, classes, resources);

        Class<?> cls = classLoader.loadClass(main);
        Method m = cls.getDeclaredMethod("main", String[].class);
        m.invoke(null, new Object[] { args });
    }
}
