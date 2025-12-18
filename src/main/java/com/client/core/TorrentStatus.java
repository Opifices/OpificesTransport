package com.client.core;

public class TorrentStatus {
    private final String id;
    private final String name;
    private final double progress; // 0.0 to 1.0
    private final long downloadedBytes;
    private final long uploadedBytes;
    private final long downloadRate; // bytes per second
    private final long uploadRate; // bytes per second
    private final int connectedPeers;
    private final String state;

    // New fields for details panel
    private final int seeds; // Peers with 100% complete
    private final int leechers; // Peers with < 100%
    private final long totalSize; // Total bytes
    private final String eta; // Estimated time remaining
    private final String lastError;

    public TorrentStatus(String id, String name, double progress, long downloadedBytes, long uploadedBytes,
            long downloadRate, long uploadRate, int connectedPeers, String state) {
        this(id, name, progress, downloadedBytes, uploadedBytes, downloadRate, uploadRate,
                connectedPeers, state, 0, connectedPeers, 0, "", "");
    }

    public TorrentStatus(String id, String name, double progress, long downloadedBytes, long uploadedBytes,
            long downloadRate, long uploadRate, int connectedPeers, String state,
            int seeds, int leechers, long totalSize, String eta, String lastError) {
        this.id = id;
        this.name = name;
        this.progress = progress;
        this.downloadedBytes = downloadedBytes;
        this.uploadedBytes = uploadedBytes;
        this.downloadRate = downloadRate;
        this.uploadRate = uploadRate;
        this.connectedPeers = connectedPeers;
        this.state = state;
        this.seeds = seeds;
        this.leechers = leechers;
        this.totalSize = totalSize;
        this.eta = eta;
        this.lastError = lastError;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getProgress() {
        return progress;
    }

    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    public long getUploadedBytes() {
        return uploadedBytes;
    }

    public long getDownloadRate() {
        return downloadRate;
    }

    public long getUploadRate() {
        return uploadRate;
    }

    public int getConnectedPeers() {
        return connectedPeers;
    }

    public String getState() {
        return state;
    }

    // New getters
    public int getSeeds() {
        return seeds;
    }

    public int getLeechers() {
        return leechers;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public String getEta() {
        return eta;
    }

    public String getLastError() {
        return lastError;
    }
}
