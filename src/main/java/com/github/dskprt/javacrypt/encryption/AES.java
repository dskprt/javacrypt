package com.github.dskprt.javacrypt.encryption;

import com.github.dskprt.javacrypt.main.Main;
import org.javatuples.Pair;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AES {

    private static Logger LOGGER;

    public static final int AES_KEY_SIZE = 256;
    public static final int GCM_IV_LENGTH = 12;
    public static final int GCM_TAG_LENGTH = 16;

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%2$s/%4$s] %5$s %6$s %n");

        LOGGER = Logger.getLogger(AES.class.getName());
        LOGGER.setLevel(Level.ALL);

        Handler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.ALL);

        LOGGER.addHandler(consoleHandler);
    }

    private static SecretKeySpec createKey(byte[] key) throws NoSuchAlgorithmException {
        LOGGER.entering(AES.class.getName(), "createKey");
        MessageDigest sha = MessageDigest.getInstance("SHA-256");

        key = sha.digest(key);
        key = Arrays.copyOf(key, AES_KEY_SIZE / 8);

        LOGGER.exiting(AES.class.getName(), "createKey");
        return new SecretKeySpec(key, "AES");
    }

    private static SecretKeySpec createKey(String password, Charset charset) throws NoSuchAlgorithmException {
        return createKey(password.getBytes(charset));
    }

    private static byte[] createIV() {
        LOGGER.entering(AES.class.getName(), "createIV");

        byte[] iv = new byte[GCM_IV_LENGTH];

        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);

        LOGGER.exiting(AES.class.getName(), "createIV");
        return iv;
    }

    public static Pair<byte[], String> encrypt(byte[] bytes, byte[] key)
            throws BadPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, NoSuchPaddingException,
                   InvalidAlgorithmParameterException, InvalidKeyException {

        LOGGER.entering(AES.class.getName(), "encrypt");

        byte[] iv = createIV();
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, createKey(key), gcmParameterSpec);

        LOGGER.exiting(AES.class.getName(), "encrypt");
        return new Pair<>(cipher.doFinal(bytes), Base64.getEncoder().encodeToString(iv));
    }

    public static Pair<byte[], String> encrypt(byte[] bytes, String password, Charset charset)
            throws BadPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, NoSuchPaddingException,
                   InvalidAlgorithmParameterException, InvalidKeyException {

        return encrypt(bytes, password.getBytes(charset));
    }

    public static byte[] decrypt(byte[] bytes, byte[] key, String iv)
            throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException,
                   InvalidAlgorithmParameterException, InvalidKeyException {

        LOGGER.entering(AES.class.getName(), "decrypt");

        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, Base64.getDecoder().decode(iv));

        Cipher cipher = Cipher.getInstance("AES/GCM/PKCS5PADDING");
        cipher.init(Cipher.DECRYPT_MODE, createKey(key), gcmParameterSpec);

        LOGGER.exiting(AES.class.getName(), "decrypt");
        return cipher.doFinal(bytes);
    }

    public static byte[] decrypt(byte[] bytes, String password, Charset charset, String iv)
            throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException,
            InvalidAlgorithmParameterException, InvalidKeyException {

        return decrypt(bytes, password.getBytes(charset), iv);
    }
}
