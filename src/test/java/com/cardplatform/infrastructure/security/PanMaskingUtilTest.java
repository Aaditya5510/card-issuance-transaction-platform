package com.cardplatform.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PanMaskingUtilTest {

    @Test
    @DisplayName("Should mask standard 16-digit PAN keeping first 6 and last 4 digits")
    void shouldMaskStandard16DigitPan() {
        String rawPan = "4111112233441111";
        String masked = PanMaskingUtil.maskPan(rawPan);
        assertThat(masked).isEqualTo("411111******1111");
    }

    @Test
    @DisplayName("Should handle PAN with dashes and whitespace cleanly")
    void shouldMaskFormattedPan() {
        String formattedPan = "4111-1122-3344-1111";
        String masked = PanMaskingUtil.maskPan(formattedPan);
        assertThat(masked).isEqualTo("411111******1111");
    }

    @Test
    @DisplayName("Should return null when null PAN is provided")
    void shouldHandleNull() {
        assertThat(PanMaskingUtil.maskPan(null)).isNull();
    }
}
