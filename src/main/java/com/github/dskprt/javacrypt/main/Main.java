package com.github.dskprt.javacrypt.main;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.github.dskprt.javacrypt.classloaders.ByteClassLoader;
import com.github.dskprt.javacrypt.encryption.AES;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.security.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Main {

    private static Logger LOGGER;

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%2$s/%4$s] %5$s %6$s %n");

        LOGGER = Logger.getLogger(Main.class.getName());
    }

    public static void main(String[] args) {
        CommandEncrypt encrypt = new CommandEncrypt();
        CommandLaunch launch = new CommandLaunch();

        JCommander jc = JCommander.newBuilder()
                .addCommand("encrypt", encrypt)
                .addCommand("launch", launch)
                .build();

        try {
            jc.parse(args);
        } catch(ParameterException e) {
            jc.usage();
            return;
        }

        if(jc.getParsedCommand() == null) {
            jc.usage();
            return;
        }

        switch(jc.getParsedCommand()) {
            case "encrypt":
                JarFile jar;
                ZipOutputStream newJar;

                try {
                    jar = new JarFile(encrypt.jar);
                    newJar = new ZipOutputStream(new FileOutputStream(FilenameUtils.removeExtension(encrypt.jar) + "-encrypted.jar"));
                } catch(IOException e) {
                    LOGGER.log(Level.SEVERE, "Could not read the file", e);
                    return;
                }

                Enumeration<JarEntry> entries = jar.entries();
                byte[] iv = AES.createIV();

                while(entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();

                    if(entry.getName().endsWith(".class")) {
                        String clsName = entry.getName().replace(".class", "").replace("/", ".");

                        LOGGER.log(Level.FINE, "Encrypting class \"" + clsName + "\"...");

                        try {
                            byte[] cls = IOUtils.toByteArray(jar.getInputStream(entry));
                            byte[] encrypted = AES.encrypt(cls, encrypt.key, iv, Charset.forName(encrypt.charset));

                            newJar.putNextEntry(new ZipEntry(entry.getName()));
                            newJar.write(encrypted);
                            newJar.closeEntry();
                        } catch(IOException | BadPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException |
                                NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {

                            LOGGER.log(Level.WARNING, "Could not encrypt class \"" + clsName + "\".", e);
                        }
                    } else {
                        try {
                            newJar.putNextEntry(new ZipEntry(entry.getName()));
                            newJar.write(IOUtils.toByteArray(jar.getInputStream(entry)));
                            newJar.closeEntry();
                        } catch(IOException e) {
                            LOGGER.log(Level.WARNING, "Could not write resource \"" + entry.getName() + "\".", e);
                        }
                    }
                }

                try {
                    jar.close();
                    newJar.close();
                } catch(IOException e) {
                    LOGGER.log(Level.WARNING, "Could not close the file.", e);
                }

                LOGGER.info("IV: " + Base64.getEncoder().encodeToString(iv));
                LOGGER.info("Finished.");
                break;
            case "launch":
//                File temp;

                try {
//                    temp = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
//
//                    DataOutputStream dos = new DataOutputStream(new FileOutputStream(temp));
//                    dos.write(IOUtils.toByteArray(new FileInputStream(launch.jar)));
//                    dos.writeByte(0x50);
//                    dos.writeByte(0x4b);
//                    dos.writeByte(0x05);
//                    dos.writeByte(0x06);
//                    dos.writeShort(0);
//                    dos.writeShort(0);
//                    dos.writeShort(0);
//                    dos.writeShort(0);
//                    dos.writeInt(0);
//                    dos.writeInt(0);
//
//                    String comment = "shit yourself";
//                    byte[] commentBytes = comment.getBytes(StandardCharsets.UTF_8);
//
//                    dos.writeShort(commentBytes.length);
//                    dos.write(commentBytes);
//
//                    jar = new JarFile(temp);

                    jar = new JarFile(launch.jar);
                } catch(IOException e) {
                    LOGGER.log(Level.SEVERE, "Could not read the file", e);
                    return;
                }

                entries = jar.entries();

                HashMap<String, byte[]> classes = new HashMap<>();
                HashMap<String, byte[]> resources = new HashMap<>();

                while(entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();

                    if(entry.getName().endsWith(".class")) {
                        String clsName = entry.getName().replace(".class", "").replace("/", ".");

                        LOGGER.log(Level.FINE, "Decrypting class \"" + clsName + "\"...");

                        try {
                            byte[] cls = IOUtils.toByteArray(jar.getInputStream(entry));
                            byte[] decrypted = AES.decrypt(cls, launch.key, Base64.getDecoder().decode(launch.iv),
                                    Charset.forName(launch.charset));

                            classes.put(FilenameUtils.removeExtension(entry.getName()), decrypted);
                        } catch(IOException | BadPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException |
                                NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {

                            LOGGER.log(Level.WARNING, "Could not decrypt class \"" + clsName + "\".", e);
                        }
                    } else {
                        try {
                            resources.put(entry.getName(), IOUtils.toByteArray(jar.getInputStream(entry)));
                        } catch(IOException e) {
                            LOGGER.log(Level.WARNING, "Could not read resource \"" + entry.getName() + "\".", e);
                        }
                    }
                }

                try {
                    jar.close();
                    //temp.delete();
                } catch(IOException e) {
                    LOGGER.log(Level.WARNING, "Could not close the file.", e);
                }

                ByteClassLoader classLoader = new ByteClassLoader(jar, classes, resources);

                try {
                    Class<?> cls = classLoader.loadClass(launch.main);
                    Method m = cls.getDeclaredMethod("main", String[].class);
                    m.invoke(null, new Object[] { launch.args.toArray(new String[0]) });
                } catch(Exception e) {
                    LOGGER.log(Level.SEVERE, "Unable to launch.", e);
                }
                break;
        }
    }

    @Parameters(commandDescription = "Generate encrypted JAR.")
    private static class CommandEncrypt {

        @Parameter(names = "--jar", description = "JAR file", required = true)
        private String jar;

        @Parameter(names = "--key", description = "Encryption key", required = true)
        private String key;

        @Parameter(names = "--charset", description = "Key charset")
        private String charset = "UTF-8";
    }

    @Parameters(commandDescription = "Launch encrypted JAR.")
    private static class CommandLaunch {

        @Parameter(names = "--jar", description = "JAR file", required = true)
        private String jar;

        @Parameter(names = "--key", description = "Encryption key", required = true)
        private String key;

        @Parameter(names = "--iv", description = "Encryption IV", required = true)
        private String iv;

        @Parameter(names = "--charset", description = "Key charset")
        private String charset = "UTF-8";

        @Parameter(names = "--main", description = "Main class", required = true)
        private String main = null;

        @Parameter(description = "Arguments")
        private List<String> args = new ArrayList<>();
    }
}
