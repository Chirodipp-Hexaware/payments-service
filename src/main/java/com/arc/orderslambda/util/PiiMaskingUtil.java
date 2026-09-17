package com.arc.orderslambda.util;

/**
 * Inline PII masking utility.
 *
 * <p>Masking rules:
 * <ul>
 *   <li><b>Email</b>: preserve last 4 characters of the local part and the full domain.
 *       E.g. {@code john.doe@example.com} → {@code ****doe@example.com}</li>
 *   <li><b>Name</b>: keep the first character of each word, replace the rest with {@code *}.
 *       E.g. {@code John Doe} → {@code J*** D**}</li>
 *   <li><b>Shipping address</b>: keep digits intact, mask all alpha characters.
 *       E.g. {@code 42 Baker Street, London} → {@code 42 B**** S*****, L*****}</li>
 * </ul>
 *
 * <p>All methods are {@code null}-safe and return an empty string for {@code null} input.
 */
public final class PiiMaskingUtil {

    private PiiMaskingUtil() {
        // utility class — no instantiation
    }

    /**
     * Masks an email address.
     *
     * <p>Preserves up to the last 4 characters of the local part (before {@code @})
     * and the full domain. If the local part is 4 characters or fewer, all characters
     * are masked. The {@code @} separator is always preserved.
     *
     * @param email raw email address; may be {@code null}
     * @return masked email, or empty string if input is {@code null} or blank
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int atIndex = email.indexOf('@');
        if (atIndex < 0) {
            // Not a recognisable email — mask the whole value
            return "*".repeat(email.length());
        }

        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex); // includes '@'

        if (local.length() <= 4) {
            return "*".repeat(local.length()) + domain;
        }

        // Keep last 4 chars of the local part
        String visible = local.substring(local.length() - 4);
        String masked = "*".repeat(local.length() - 4);
        return masked + visible + domain;
    }

    /**
     * Masks a personal name.
     *
     * <p>For each whitespace-delimited word, the first character is retained and the
     * remaining characters are replaced with {@code *}.
     *
     * @param name raw name; may be {@code null}
     * @return masked name, or empty string if input is {@code null} or blank
     */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String[] words = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) {
                continue;
            }
            sb.append(word.charAt(0));
            if (word.length() > 1) {
                sb.append("*".repeat(word.length() - 1));
            }
            if (i < words.length - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    /**
     * Masks a shipping address.
     *
     * <p>Digit characters and punctuation ({@code ,./- }) are preserved; all letter
     * characters are replaced with {@code *}. This retains structural cues (house numbers,
     * postal codes) while hiding street names and city names.
     *
     * @param address raw shipping address; may be {@code null}
     * @return masked address, or empty string if input is {@code null} or blank
     */
    public static String maskAddress(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(address.length());
        for (char c : address.toCharArray()) {
            if (Character.isLetter(c)) {
                sb.append('*');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
