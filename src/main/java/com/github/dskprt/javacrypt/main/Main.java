package com.github.dskprt.javacrypt.main;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.github.dskprt.javacrypt.classloaders.ByteArrayClassLoader;
import com.github.dskprt.javacrypt.encryption.AES;
import org.javatuples.Pair;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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
                byte[] bytes;

                try {
                    bytes = Files.readAllBytes(Paths.get(encrypt.jar));
                } catch(IOException e) {
                    LOGGER.log(Level.SEVERE, "Could not read the file.", e);
                    return;
                }

                Pair<byte[], String> encrypted;

                try {
                    encrypted = AES.encrypt(bytes, encrypt.key, Charset.forName(encrypt.charset));
                } catch(BadPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException |
                        NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {

                    LOGGER.log(Level.SEVERE, "Could not encrypt the file.", e);
                    return;
                }

                try {
                    Files.write(Paths.get(encrypt.jar), encrypted.getValue0());
                } catch(IOException e) {
                    LOGGER.log(Level.SEVERE, "Could not write the file.", e);
                    return;
                }

                LOGGER.info("IV: " + encrypted.getValue1());
                LOGGER.info("Finished.");
                break;
            case "launch":
                try {
                    bytes = Files.readAllBytes(Paths.get(launch.jar));
                } catch(IOException e) {
                    LOGGER.log(Level.SEVERE, "Could not read the file.", e);
                    return;
                }

                byte[] decrypted;

                try {
                    decrypted = AES.decrypt(bytes, launch.key, Charset.forName(launch.charset), launch.iv);
                } catch(NoSuchPaddingException | NoSuchAlgorithmException | BadPaddingException |
                        IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException e) {

                    LOGGER.log(Level.SEVERE, "Could not decrypt the file.", e);
                    return;
                }

                try {
                    ByteArrayClassLoader classLoader = new ByteArrayClassLoader(decrypted);

                    Class<?> cls = classLoader.loadClass(launch.main, true);
                    Method m = cls.getDeclaredMethod("main", String[].class);
                    m.invoke(null, new Object[] { launch.args.toArray(new String[0]) });
                } catch(IOException | InvocationTargetException | NoSuchMethodException | IllegalAccessException |
                        ClassNotFoundException e) {

                    LOGGER.log(Level.SEVERE, "Unable to launch", e);
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
