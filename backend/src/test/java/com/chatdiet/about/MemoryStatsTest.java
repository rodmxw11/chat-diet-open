package com.chatdiet.about;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryStatsTest {

    @Test
    void readsMemAvailableFromProcMeminfoInBytes() {
        var meminfo = List.of(
                "MemTotal:         993292 kB",
                "MemFree:           76800 kB",
                "MemAvailable:     431104 kB",
                "Buffers:           12288 kB");

        assertThat(MemoryStats.parseMemAvailable(meminfo)).isEqualTo(431104L * 1024);
    }

    /** Kernels before 3.14 don't report it - the page then falls back to strict free memory. */
    @Test
    void missingMemAvailableIsNull() {
        assertThat(MemoryStats.parseMemAvailable(List.of("MemTotal: 993292 kB", "MemFree: 76800 kB"))).isNull();
    }
}
