package com.chatdiet.about;

import java.lang.management.ManagementFactory;

/**
 * JVM and system memory for the About page - on the 1 GB e2-micro, the numbers that say whether
 * the heap cap still has headroom and whether the VM is leaning on swap.
 *
 * @param heapUsedBytes      live and not-yet-collected objects on the heap
 * @param heapCommittedBytes heap the JVM has currently reserved from the OS
 * @param heapMaxBytes       the heap limit ({@code -Xmx}, or the JVM's default from host RAM)
 * @param heapFreeBytes      headroom before that limit: max minus used - what can still be
 *                           allocated, which is what matters, rather than free space within the
 *                           currently committed heap
 * @param nonHeapUsedBytes   class metadata, compiled code and other off-heap JVM memory
 * @param systemTotalBytes   physical memory as the JVM sees it (the container limit if one is set,
 *                           otherwise the host's RAM), or null if the platform doesn't report it
 * @param systemFreeBytes    free physical memory on the same basis, or null
 * @param swapTotalBytes     swap space, or null
 * @param swapFreeBytes      free swap space, or null
 */
public record MemoryStats(
        long heapUsedBytes,
        long heapCommittedBytes,
        long heapMaxBytes,
        long heapFreeBytes,
        long nonHeapUsedBytes,
        Long systemTotalBytes,
        Long systemFreeBytes,
        Long swapTotalBytes,
        Long swapFreeBytes
) {

    /** A snapshot of current memory use. */
    public static MemoryStats current() {
        var runtime = Runtime.getRuntime();
        var used = runtime.totalMemory() - runtime.freeMemory();
        var max = runtime.maxMemory();
        var nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed();

        // The com.sun.management extension is container-aware on JDK 21 and present on every JVM
        // this app ships on (Temurin), but it's not part of the standard API - degrade to nulls.
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            return new MemoryStats(used, runtime.totalMemory(), max, max - used, nonHeap,
                    os.getTotalMemorySize(), os.getFreeMemorySize(),
                    os.getTotalSwapSpaceSize(), os.getFreeSwapSpaceSize());
        }
        return new MemoryStats(used, runtime.totalMemory(), max, max - used, nonHeap, null, null, null, null);
    }
}
