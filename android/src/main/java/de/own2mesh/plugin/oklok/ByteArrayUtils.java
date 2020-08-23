package de.own2mesh.plugin.oklok;

import com.getcapacitor.JSArray;

import org.json.JSONException;

public class ByteArrayUtils {
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public static byte[] decStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 10)) * 10
                    + Character.digit(s.charAt(i + 1), 10));
        }
        return data;
    }

    public static String byteArrayToHexString(byte[] array){
        String s = "";
        for(byte b : array){
            s += String.format("%02x", b);
        }
        return s;
    }

    public static byte[] csvStringToByteArray(String csvString){
        String[] parts = csvString.split(",");
        byte[] data = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            int digit = Integer.parseInt(parts[i]);
            data[i] = (byte) digit;
        }
        return data;
    }

    public static String JSArrayToHexString(JSArray array) throws JSONException {
        String hexString = new String();
        for (int i = 0; i < array.length(); i++) {
            hexString.concat(array.getString(i).split("x")[1]);
        }
        return hexString;
    }
}