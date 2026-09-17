package com.arc.orderslambda.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PiiMaskingUtil} — covers the masking branches (REQ-7).
 *
 * <p>Assertions pin the <em>actual</em> implementation behavior (last-4 of the email
 * local part is preserved, including any punctuation such as a dot). Follows
 * Arrange–Act–Assert; deterministic.
 */
class PiiMaskingUtilTest {

    // ── Email ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Email: local part > 4 chars keeps last 4 (incl. punctuation) and full domain")
    void maskEmail_longLocal_keepsLastFour() {
        // "david.lee" is 9 chars → 5 stars + last 4 ".lee"
        assertThat(PiiMaskingUtil.maskEmail("david.lee@corp.com"))
                .isEqualTo("*****.lee@corp.com");
    }

    @Test
    @DisplayName("Email: local part exactly 4 chars is fully masked")
    void maskEmail_localExactlyFour_fullyMasked() {
        assertThat(PiiMaskingUtil.maskEmail("john@example.com"))
                .isEqualTo("****@example.com");
    }

    @Test
    @DisplayName("Email: local part shorter than 4 chars is fully masked")
    void maskEmail_shortLocal_fullyMasked() {
        assertThat(PiiMaskingUtil.maskEmail("ab@x.io"))
                .isEqualTo("**@x.io");
    }

    @Test
    @DisplayName("Email: value without @ is fully masked")
    void maskEmail_noAtSign_fullyMasked() {
        assertThat(PiiMaskingUtil.maskEmail("notanemail"))
                .isEqualTo("**********");
    }

    @Test
    @DisplayName("Email: null and blank return empty string")
    void maskEmail_nullOrBlank_returnsEmpty() {
        assertThat(PiiMaskingUtil.maskEmail(null)).isEmpty();
        assertThat(PiiMaskingUtil.maskEmail("   ")).isEmpty();
    }

    // ── Name ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Name: multi-word keeps first char of each word")
    void maskName_multiWord_keepsInitials() {
        assertThat(PiiMaskingUtil.maskName("David Lee")).isEqualTo("D**** L**");
    }

    @Test
    @DisplayName("Name: single word keeps first char")
    void maskName_singleWord_keepsFirstChar() {
        assertThat(PiiMaskingUtil.maskName("Alice")).isEqualTo("A****");
    }

    @Test
    @DisplayName("Name: single-character word passes through unmasked (only initial)")
    void maskName_singleChar_keepsChar() {
        assertThat(PiiMaskingUtil.maskName("X")).isEqualTo("X");
    }

    @Test
    @DisplayName("Name: extra whitespace collapses to single spaces")
    void maskName_extraWhitespace_collapsed() {
        assertThat(PiiMaskingUtil.maskName("  John   Doe  ")).isEqualTo("J*** D**");
    }

    @Test
    @DisplayName("Name: null and blank return empty string")
    void maskName_nullOrBlank_returnsEmpty() {
        assertThat(PiiMaskingUtil.maskName(null)).isEmpty();
        assertThat(PiiMaskingUtil.maskName("  ")).isEmpty();
    }

    // ── Address ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Address: letters masked, digits and punctuation preserved")
    void maskAddress_keepsDigitsAndPunctuation() {
        assertThat(PiiMaskingUtil.maskAddress("22 Elm Street, Boston"))
                .isEqualTo("22 *** ******, ******");
    }

    @Test
    @DisplayName("Address: value with no letters is unchanged")
    void maskAddress_noLetters_unchanged() {
        assertThat(PiiMaskingUtil.maskAddress("123 456")).isEqualTo("123 456");
    }

    @Test
    @DisplayName("Address: null and blank return empty string")
    void maskAddress_nullOrBlank_returnsEmpty() {
        assertThat(PiiMaskingUtil.maskAddress(null)).isEmpty();
        assertThat(PiiMaskingUtil.maskAddress("   ")).isEmpty();
    }
}
