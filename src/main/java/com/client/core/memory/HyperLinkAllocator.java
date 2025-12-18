package com.client.core.memory;

import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.io.File;

/**
 * Opifices Hyper-Link Allocator
 * Allocates off-heap memory specifically mapped to OS shared memory
 * for Zero-Copy inter-process communication with Python.
 */
public class HyperLinkAllocator {

    // Windows compatibility: Use a temp file instead of /dev/shm
    private static final String SHM_PATH = System.getProperty("os.name").toLowerCase().contains("win")
            ? System.getProperty("java.io.tmpdir") + "opifices_tensor_01"
            : "/dev/shm/opifices_tensor_01";

    private static final long SIZE_MB = 512 * 1024 * 1024; // 512MB Buffer
    private static MappedByteBuffer sharedBuffer;

    public static MappedByteBuffer getBuffer() {
        return sharedBuffer;
    }

    public static void allocateTensorBuffer() throws Exception {
        System.out.println("[OPIT-LINK] Allocating 512MB Shared Memory Segment...");
        System.out.println("[OPIT-LINK] Mapping file: " + SHM_PATH);

        File shmFile = new File(SHM_PATH);
        // Ensure parent exists
        if (shmFile.getParentFile() != null) {
            shmFile.getParentFile().mkdirs();
        }

        try (RandomAccessFile file = new RandomAccessFile(shmFile, "rw")) {
            // Pre-allocate disk space (sparse file avoidance)
            file.setLength(SIZE_MB);

            // Map directly to OS memory
            // Map directly to OS memory
            sharedBuffer = file.getChannel()
                    .map(FileChannel.MapMode.READ_WRITE, 0, SIZE_MB);

            // Write Header (Magic Bytes for our Python Client)
            sharedBuffer.putInt(0, 0x0F1F1CE5); // Magic "OPIFICES" (0F1F1CE5)
            sharedBuffer.putInt(4, 1); // Version

            System.out.println("[OPIT-LINK] Memory Mapped successfully.");
            System.out.println("[OPIT-LINK] Ready for Zero-Copy ingestion.");

            // Keep alive for demo - In production this would be handled by the main app
            // lifecycle
            // Thread.sleep(Long.MAX_VALUE);
        }
    }
}
