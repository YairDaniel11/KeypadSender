package com.meir.keypadsender;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KeypadEncoderTest {

    @Test public void letters() {
        assertEquals("7755552224#", KeypadEncoder.encode("\u05E9\u05DC\u05D5\u05DD")); // שלום (ם סופית = 4)
        assertEquals("777#", KeypadEncoder.encode("\u05EA"));
        assertEquals("5555#", KeypadEncoder.encode("\u05DC"));
    }

    @Test public void separatorBetweenSameKey() {
        assertEquals("313#", KeypadEncoder.encode("\u05D0\u05D0"));   // אא
        assertEquals("7177#", KeypadEncoder.encode("\u05E8\u05E9"));  // רש
        assertEquals("3#", KeypadEncoder.encode("\u05D0"));
    }

    @Test public void finalLettersAreSentAsRegular() {
        assertEquals(KeypadEncoder.encode("\u05DB"), KeypadEncoder.encode("\u05DA")); // כ / ך
        assertEquals(KeypadEncoder.encode("\u05E4"), KeypadEncoder.encode("\u05E3")); // פ / ף
        assertEquals(KeypadEncoder.encode("\u05E6"), KeypadEncoder.encode("\u05E5")); // צ / ץ
        assertEquals(KeypadEncoder.encode("\u05E0"), KeypadEncoder.encode("\u05DF")); // נ / ן
    }

    @Test public void spacesAndDigits() {
        assertEquals("7755552224" + "0" + "44444#", KeypadEncoder.encode("\u05E9\u05DC\u05D5\u05DD 4"));
        assertEquals("11#", KeypadEncoder.encode("1"));
        assertEquals("00#", KeypadEncoder.encode("0"));
        assertEquals("30100#", KeypadEncoder.encode("\u05D0 0")); // רווח ואז 0: מפריד, כדי לא ליצור 000 (מחיקה)
    }

    @Test public void whitespaceIsCollapsed() {
        assertEquals(KeypadEncoder.encode("\u05D0 \u05D1"), KeypadEncoder.encode("  \u05D0   \u05D1\n"));
    }

    @Test public void unsupportedIsSkippedAndReported() {
        assertEquals("3#", KeypadEncoder.encode("a\u05D0!"));
        assertEquals("a!", KeypadEncoder.findUnsupported("a\u05D0!a"));
        assertEquals("", KeypadEncoder.findUnsupported("\u05E9\u05DC\u05D5\u05DD 12"));
    }

    @Test public void emptyIsJustEnd() {
        assertEquals("#", KeypadEncoder.encode(""));
        assertEquals("#", KeypadEncoder.encode(null));
    }

    @Test public void dialString() {
        assertEquals("0771234567,,,1,,2,,7755#",
                Dialer.buildDialString("077-123 4567", 3, 2, 2, 1, "7755#"));
    }

    @Test public void phoneNumbers() {
        assertTrue(PhoneNumberUtil.same("+972771234567", "0771234567"));
        assertTrue(PhoneNumberUtil.same("0771234567,,,1,,2,,77#", "077-123-4567"));
        assertTrue(PhoneNumberUtil.same("771234567", "0771234567"));
        assertFalse(PhoneNumberUtil.same("0771234567", "0771234568"));
        assertFalse(PhoneNumberUtil.same("", "0771234567"));
    }
}
