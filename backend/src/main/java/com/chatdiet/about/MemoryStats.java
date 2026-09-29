package com.chatdiet.about;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
 * @param systemFreeBytes    free physical memory on the same basis, or null - Linux's strict
 *                           "free", which excludes page cache the kernel reclaims on demand, so it
 *                           reads alarmingly low on any busy machine
 * @param systemAvailableBytes Linux's {@code MemAvailable} from {@code /proc/meminfo}: an estimate
 *                           of memory available for new work without swapping, counting
 *                           reclaimable cache - the honest measure of memory pressure. Null off
 *                           Linux or if unreadable. It's the host's (here, the VM's) figure, not a
 *                           container limit - none is set on the e2-micro.
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
        Long systemAvailableBytes,
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
                    os.getTotalMemorySize(), os.getFreeMemorySize(), memAvailable(),
                    os.getTotalSwapSpaceSize(), os.getFreeSwapSpaceSize());
        }
        return new MemoryStats(used, runtime.totalMemory(), max, max - used, nonHeap,
                null, null, memAvailable(), null, null);
    }

    private static Long memAvailable() {
        try {
            return parseMemAvailable(Files.readAllLines(Path.of("/proc/meminfo")));
        } catch (IOException | RuntimeException e) {
            return null; // not Linux, or /proc unreadable
        }
    }

    /** The {@code MemAvailable:   123456 kB} line of {@code /proc/meminfo}, in bytes; null if absent. */
    static Long parseMemAvailable(List<String> meminfoLines) {
        for (var line : meminfoLines) {
            if (line.startsWith("MemAvailable:")) {
                var kib = Long.parseLong(line.substring("MemAvailable:".length()).replace("kB", "").trim());
                return kib * 1024;
            }
        }
        return null;
    }
}
