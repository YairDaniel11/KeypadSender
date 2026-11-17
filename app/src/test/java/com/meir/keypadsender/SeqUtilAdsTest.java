package com.meir.keypadsender;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SeqUtilAdsTest {

    @Test public void splitsAfterFirstKey() {
        SeqUtil.Script s = SeqUtil.toAdsScript(",,,3,,2,,", "2221#");
        assertEquals(1, s.confirmAfter);
        assertArrayEquals(new String[]{"3", "2", "2221#"}, s.parts);
        // המתנה לפני השלוחה נשמרת; אחרי האישור - בלי המתנה ראשונה, ואז כרגיל
        assertArrayEquals(new long[]{6000, 0, 4000}, s.delays);
    }

    @Test public void noCommas() {
        SeqUtil.Script s = SeqUtil.toAdsScript("32", "22#");
        assertEquals(1, s.confirmAfter);
        assertArrayEquals(new String[]{"3", "222#"}, s.parts);
    }

    @Test public void noKeyInSeq() {
        assertNull(SeqUtil.toAdsScript(",,", "22#"));
        assertNull(SeqUtil.toAdsScript("", "22#"));
    }

    @Test public void nothingAfterFirstKey() {
        assertNull(SeqUtil.toAdsScript("3", ""));
    }
}
