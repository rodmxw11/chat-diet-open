package com.chatdiet.fdc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FdcClientTest {

    @Test
    void searchReturnsEmptyWhenNoApiKeyIsConfigured() {
        var unconfigured = new FdcClient("");
        assertThat(unconfigured.search("celery", 5)).isEmpty();
    }

    @Test
    void fetchDetailReturnsEmptyWhenNoApiKeyIsConfigured() {
        var unconfigured = new FdcClient("");
        assertThat(unconfigured.fetchDetail(173944L)).isEmpty();
    }

    @Test
    void lookupBrandedByUpcReturnsEmptyWhenNoApiKeyIsConfigured() {
        var unconfigured = new FdcClient("");
        assertThat(unconfigured.lookupBrandedByUpc("049000028911")).isEmpty();
    }

    @Test
    void qualifierCountPrefersPlainerDescriptions() {
        // Real case that motivated this: FDC's own relevance ranking put the powder ahead of the
        // raw fruit for a bare "banana" query - qualifier count should rank the plain one first.
        assertThat(FdcClient.qualifierCount("Bananas, raw"))
                .isLessThan(FdcClient.qualifierCount("Bananas, dehydrated, or banana powder"));
    }

    @Test
    void qualifierCountTreatsMissingDescriptionAsMaximallyQualified() {
        assertThat(FdcClient.qualifierCount(null)).isEqualTo(Integer.MAX_VALUE);
        assertThat(FdcClient.qualifierCount("")).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void rankingPrefersAnExactLeadingTokenMatch() {
        var exact = new FdcApiFood(1L, "Bananas, raw", null);
        var partial = new FdcApiFood(2L, "Bananas, dehydrated, or banana powder", null);

        var sorted = List.of(partial, exact).stream().sorted(FdcClient.rankingComparator("bananas")).toList();

        assertThat(sorted.get(0)).isEqualTo(exact);
    }

    @Test
    void rankingPrefersMoreTokenOverlapWhenNoExactLeadingMatch() {
        var moreOverlap = new FdcApiFood(1L, "Celery, raw, sticks", null);
        var lessOverlap = new FdcApiFood(2L, "Celery juice", null);

        var sorted = List.of(lessOverlap, moreOverlap).stream()
                .sorted(FdcClient.rankingComparator("celery sticks")).toList();

        assertThat(sorted.get(0)).isEqualTo(moreOverlap);
    }

    @Test
    void gtinStrippingIgnoresLeadingZerosOnBothSides() {
        assertThat(FdcClient.stripLeadingZeros("00049000028911")).isEqualTo(FdcClient.stripLeadingZeros("49000028911"));
        assertThat(FdcClient.stripLeadingZeros("0000")).isEqualTo("0");
    }
}
