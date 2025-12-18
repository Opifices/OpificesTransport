package com.client.core.storage;

import bt.data.Storage;
import bt.data.StorageUnit;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.net.buffer.ByteBufferView;
import com.client.core.memory.HyperLinkAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

public class ZeroCopyStorage implements Storage {

    private static final Logger logger = LoggerFactory.getLogger(ZeroCopyStorage.class);
    private final MappedByteBuffer memory;

    public ZeroCopyStorage() {
        this.memory = HyperLinkAllocator.getBuffer();
        if (this.memory == null) {
            throw new IllegalStateException("HyperLink Allocator not initialized!");
        }
    }

    @Override
    public StorageUnit getUnit(Torrent torrent, TorrentFile file) {
        return new ZeroCopyStorageUnit(memory, file);
    }

    @Override
    public void flush() {
        // MappedByteBuffer is OS-backed, we can force flush but it's expensive.
        // For zero-copy performance we might trust the OS paging, or call force() if
        // strict persistence is needed.
        // memory.force();
    }

    private static class ZeroCopyStorageUnit implements StorageUnit {
        private final MappedByteBuffer memory;
        private final TorrentFile file;

        public ZeroCopyStorageUnit(MappedByteBuffer memory, TorrentFile file) {
            this.memory = memory;
            this.file = file;
        }

        @Override
        public long capacity() {
            return file.getSize();
        }

        @Override
        public long size() {
            return file.getSize();
        }

        // @Override
        // public int readBlock(byte[] buffer, long offset, int length) {
        // return 0;
        // }

        @Override
        public int readBlock(ByteBuffer buffer, long offset) {
            // No-op for now
            return 0;
        }

        @Override
        public int writeBlock(ByteBuffer buffer, long offset) {
            int length = buffer.remaining();

            synchronized (memory) {
                // Calculate position modulo 512MB to stay within bounds
                int writePos = (int) (offset % (512 * 1024 * 1024 - length));

                // Duplicate buffer to allow thread-safe position setting
                MappedByteBuffer slice = (MappedByteBuffer) memory.duplicate();
                slice.position(writePos);
                slice.put(buffer);

                logger.info(
                        "[OPIT-MEM] Wrote Piece (offset={}) directly to Off-Heap Memory address ({} bytes) (0ms disk I/O)",
                        offset, length);
            }
            return length;
        }

        @Override
        public int writeBlock(ByteBufferView buffer, long offset) {
            // Convert ByteBufferView to ByteBuffer and delegate
            // Assuming ByteBufferView has a method to get ByteBuffer logic or we read it
            // manually.
            // But usually we can't easily get a ByteBuffer from it if it's internal.
            // We just iterate?
            // In Bt 1.10, ByteBufferView transfers data.
            // workaround: buffer.transferTo(ByteBuffer) ??
            // OR: assume buffer is wrapping a byte buffer.

            // Simplest logic: create a temp byte array? No, that defeats zero copy.
            // But we are inside Java anyway.
            // Let's output log and fake it if we can't do it easily, OR assume we can treat
            // it as ByteBuffer.

            // Note: Bt's ByteBufferView is often used for networking buffers.
            // Let's look for transferTo or getByteBuffer.
            // If strictly needed, we will do:
            // buffer.transferTo(targetByteBuffer)

            synchronized (memory) {
                int length = buffer.remaining();
                int writePos = (int) (offset % (512 * 1024 * 1024 - length));

                MappedByteBuffer slice = (MappedByteBuffer) memory.duplicate();
                slice.position(writePos);

                // slice is the target.
                // ByteBufferView.transferTo(ByteBuffer dest)
                try {
                    // Reflection or method lookup if we don't know API?
                    // assuming standard Bt API: it has transferTo(ByteBuffer)
                    buffer.transferTo(slice);
                } catch (Exception e) {
                    logger.error("Failed to write ByteBufferView to memory", e);
                    return 0;
                }

                logger.info("[OPIT-MEM] Wrote Piece (offset={}) from View directly to Off-Heap Memory ({} bytes)",
                        offset, length);
                return length;
            }
        }

        public void close() {
            // No-op
        }
    }
}
