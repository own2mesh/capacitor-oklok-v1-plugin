package de.own2mesh.plugin.oklok;

import android.util.Log;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionUtils {
    // The sender needs to encrypt the communication frame before sending it, (PDF S.2)
    // The encryption algorithm is specified as AES-128
    public static byte[] Encrypt(byte[] sSrc, byte[] sKey) { // Methode kopiert aus PDF S.3
        try {
            SecretKeySpec skeySpec = new SecretKeySpec(sKey, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
            byte[] encrypted = cipher.doFinal(sSrc);
            return encrypted;
        } catch (Exception ex) {
            return null;
        }
    }

    // the receiver needs to decrypt to restore the frame after receiving the data (PDF S.2)
    // The encryption algorithm is specified as AES-128
    public static byte[] Decrypt(byte[] sSrc, byte[] sKey) { // Methode kopiert aus PDF S.3
        try {
            SecretKeySpec skeySpec = new SecretKeySpec(sKey, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            byte[] dncrypted = cipher.doFinal(sSrc);
            return dncrypted;
        } catch (Exception ex) {
            Log.i("EXCEPTION!!!!", ex.toString());
            return null;
        }
    }
}
