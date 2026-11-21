package ravens4.security;

import java.util.Base64;

/**
 * StringCrypt - lightweight string obfuscation for compile-time constants
 * (license hashes, class name lists, anything you don't want sitting as
 * plaintext in a decompiled jar).
 *
 * This is NOT cryptographically strong encryption - it's XOR + Base64,
 * designed to defeat "ctrl+F / grep the decompiled source" attacks, not
 * a determined reverse engineer who steps through the decrypt() call
 * itself. Pair this with real control-flow obfuscation (e.g. Zelix) if
 * you need to resist someone actually reading the logic.
 *
 * The XOR key is itself split across multiple fields and assembled at
 * runtime rather than stored as one plain string, so a naive string
 * search for the key constant doesn't immediately find it either.
 */
public final class StringCrypt {

    // Key is split into pieces and reassembled at runtime. Change these
    // bytes to any value you like (just keep ENCODE and DECODE using the
    // same key, obviously - they're the same operation since XOR is
    // symmetric).
    private static final byte K1 = 0x4F; // 'O'
    private static final byte K2 = 0x1A;
    private static final byte K3 = 0x7C;
    private static final byte K4 = 0x3E;

    private StringCrypt() {
    }

    private static byte[] key() {
        return new byte[]{K1, K2, K3, K4};
    }

    /** Encrypts plaintext into a Base64 string. Use this offline to generate
     *  the constants you paste into your source (see main() below). */
    public static String encrypt(String plaintext) {
        byte[] input = plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] key = key();
        byte[] out = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            out[i] = (byte) (input[i] ^ key[i % key.length]);
        }
        return Base64.getEncoder().encodeToString(out);
    }

    /** Decrypts a Base64-encoded, XOR'd string back to plaintext at runtime. */
    public static String decrypt(String encoded) {
        byte[] input = Base64.getDecoder().decode(encoded);
        byte[] key = key();
        byte[] out = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            out[i] = (byte) (input[i] ^ key[i % key.length]);
        }
        return new String(out, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Run this manually to generate an encrypted constant for any string
     * you want to hide (a hash, a class path, anything). Paste the output
     * into your source wrapped in StringCrypt.decrypt("...").
     *
     * Usage: java -cp yourjar.jar ravens4.security.StringCrypt "text to hide"
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: StringCrypt \"text to encrypt\"");
            return;
        }
        String plain = args[0];
        String enc = encrypt(plain);
        System.out.println("Plaintext : " + plain);
        System.out.println("Encrypted : " + enc);
        System.out.println("Paste as  : StringCrypt.decrypt(\"" + enc + "\")");
    }
}
