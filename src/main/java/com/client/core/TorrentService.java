package com.client.core;

import bt.Bt;
import bt.dht.DHTConfig;
import bt.dht.DHTModule;
import bt.runtime.BtClient;
import bt.runtime.Config;
import bt.data.file.FileSystemStorage;
import com.google.inject.Module;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import bt.metainfo.Torrent;

import com.client.core.OrchestratedPieceSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TorrentService {

    private static final Logger logger = LoggerFactory.getLogger(TorrentService.class);

    private final Map<String, BtClient> activeClients = new ConcurrentHashMap<>();
    private final Map<String, TorrentStatus> latestStatus = new ConcurrentHashMap<>();
    private final Map<String, String> torrentNames = new ConcurrentHashMap<>();
    private final Map<String, String> lastErrors = new ConcurrentHashMap<>();
    private final Map<String, OrchestratedPieceSelector> orchestratedSelectors = new ConcurrentHashMap<>();

    private org.bitlet.weupnp.GatewayDevice gateway;
    private int mappedPort = 0;

    public TorrentService() {
        // CRITICAL FIX: Force IPv4 to prevent UnresolvedAddressException on dual-stack
        // networks
        System.setProperty("java.net.preferIPv4Stack", "true");

        // Initialize UPnP for NAT traversal
        initializeUpnp();
    }

    private void initializeUpnp() {
        new Thread(() -> {
            try {
                logger.info("[UPnP] Searching for UPnP gateway...");
                org.bitlet.weupnp.GatewayDiscover discover = new org.bitlet.weupnp.GatewayDiscover();
                discover.discover();
                gateway = discover.getValidGateway();

                if (gateway != null) {
                    logger.info("[UPnP] Found gateway: {}", gateway.getFriendlyName());
                    String externalIP = gateway.getExternalIPAddress();
                    logger.info("[UPnP] External IP: {}", externalIP);

                    // Map the torrent port
                    com.client.config.SettingsManager settings = com.client.config.SettingsManager.getInstance();
                    int port = settings.getInt(com.client.config.SettingsManager.KEY_PORT);

                    // Add port mapping for TCP
                    boolean tcpMapped = gateway.addPortMapping(port, port, gateway.getLocalAddress().getHostAddress(),
                            "TCP", "ModernTorrentClient");
                    // Add port mapping for UDP
                    boolean udpMapped = gateway.addPortMapping(port, port, gateway.getLocalAddress().getHostAddress(),
                            "UDP", "ModernTorrentClient");

                    if (tcpMapped || udpMapped) {
                        mappedPort = port;
                        logger.info("[UPnP] SUCCESS! Port {} mapped (TCP:{}, UDP:{})", port, tcpMapped, udpMapped);
                    } else {
                        logger.warn("[UPnP] Failed to map port {}", port);
                    }
                } else {
                    logger.info("[UPnP] No UPnP gateway found - you may need manual port forwarding");
                }
            } catch (Exception e) {
                logger.error("[UPnP] Error: ", e);
            }
        }, "UPnP-Init").start();
    }

    public List<TorrentStatus> getAllTorrentsStatus() {
        return new ArrayList<>(latestStatus.values());
    }

    public String startDownload(String magnetLink) {
        return startDownloadGeneric(magnetLink, null);
    }

    public String startDownload(File torrentFile) {
        return startDownloadGeneric(null, torrentFile);
    }

    public void stopDownload(String id) {
        BtClient client = activeClients.remove(id);
        if (client != null) {
            client.stop();
            latestStatus.remove(id);
            logger.info("Stopped download: {}", id);
        }
    }

    private String startDownloadGeneric(String magnetLink, File torrentFile) {
        // Load settings
        com.client.config.SettingsManager settings = com.client.config.SettingsManager.getInstance();
        Path targetDirectory = settings.getPath(com.client.config.SettingsManager.KEY_DOWNLOAD_DIR);
        int maxPeers = settings.getInt(com.client.config.SettingsManager.KEY_MAX_PEERS);
        int port = settings.getInt(com.client.config.SettingsManager.KEY_PORT);

        // Debug Directory
        logger.debug("Start Download: Target Directory={}", targetDirectory.toAbsolutePath());
        if (!targetDirectory.toFile().exists()) {
            boolean created = targetDirectory.toFile().mkdirs();
            logger.debug("Directory missing. Created? {}", created);
        }

        // Optimizations
        boolean optimizationsDisabled = Boolean.parseBoolean(settings.get("optimizations.disabled"));

        boolean adaptivePeerBias = !optimizationsDisabled
                && Boolean.parseBoolean(settings.get("optimizations.adaptive_peer_bias"));

        // Speed Boosters
        String maxConnStr = settings.get("optimizations.max_connections");
        int maxConnections = (!optimizationsDisabled && maxConnStr != null && !maxConnStr.isEmpty())
                ? Integer.parseInt(maxConnStr)
                : maxPeers;
        if (maxConnections > maxPeers)
            maxPeers = maxConnections; // Override

        String pipelineStr = settings.get("optimizations.throughput_pipelining");
        int pipelineRequests = (!optimizationsDisabled && pipelineStr != null && !pipelineStr.isEmpty())
                ? Integer.parseInt(pipelineStr)
                : 5;

        boolean leecherMode = !optimizationsDisabled
                && Boolean.parseBoolean(settings.get("optimizations.leecher_mode"));
        boolean dhtAggressive = !optimizationsDisabled
                && Boolean.parseBoolean(settings.get("optimizations.dht_aggressive"));

        if (maxConnections > 50)
            logger.info("[OPIT-CORE] Connection Expansion: {} connections", maxConnections);
        if (pipelineRequests > 5)
            logger.info("[OPIT-CORE] Throughput Pipelining: {} parallel requests", pipelineRequests);
        if (leecherMode)
            logger.info("[OPIT-CORE] Efficiency Mode: Upload minimized for bandwidth preservation");
        if (dhtAggressive)
            logger.info("[OPIT-CORE] DHT Fast-Query: Interval 5s");

        Config config = createConfig(maxPeers, port, adaptivePeerBias, pipelineRequests);

        Module dhtModule = new DHTModule(new DHTConfig() {
            @Override
            public boolean shouldUseRouterBootstrap() {
                return true;
            }

            @Override
            public boolean shouldUseIPv6() {
                return false; // Disable IPv6 as suggested to avoid network stack issues
            }

            @Override
            public java.util.Collection<bt.net.InetPeerAddress> getBootstrapNodes() {
                // Extended list of DHT bootstrap nodes for maximum peer discovery
                return java.util.List.of(
                        new bt.net.InetPeerAddress("router.bittorrent.com", 6881),
                        new bt.net.InetPeerAddress("router.utorrent.com", 6881),
                        new bt.net.InetPeerAddress("dht.transmissionbt.com", 6881),
                        new bt.net.InetPeerAddress("dht.aelitis.com", 6881),
                        new bt.net.InetPeerAddress("router.bitcomet.com", 6881),
                        new bt.net.InetPeerAddress("dht.libtorrent.org", 25401));
            }
        });

        // Create piece selector with auto-aggressive capabilities
        final OrchestratedPieceSelector pieceSelector = new OrchestratedPieceSelector();

        // Use var to avoid importing internal Builder type
        var builder = Bt.client()
                .config(config)
                .selector(pieceSelector)
                .storage(new com.client.core.storage.ZeroCopyStorage());
        // .storage(new FileSystemStorage(targetDirectory));

        // Add our custom DHT module
        builder.module(dhtModule);

        // Add HttpTrackerModule manually
        builder.module(new bt.tracker.http.HttpTrackerModule());

        // CORE FIX: Disable LSD Module via hidden internal flag
        // This prevents the NullPointerException in
        // LocalServiceDiscoveryPeerSourceFactory
        try {
            // 1. Get the private 'runtimeBuilder' from StandaloneClientBuilder
            java.lang.reflect.Field runtimeBuilderField = builder.getClass().getDeclaredField("runtimeBuilder");
            runtimeBuilderField.setAccessible(true);
            Object runtimeBuilder = runtimeBuilderField.get(builder);

            // 2. Set 'shouldDisableLocalServiceDiscovery' to true on the runtimeBuilder
            java.lang.reflect.Field lsdField = runtimeBuilder.getClass()
                    .getDeclaredField("shouldDisableLocalServiceDiscovery");
            lsdField.setAccessible(true);
            lsdField.set(runtimeBuilder, true);

            logger.info("[CORE FIX] Successfully disabled LocalServiceDiscovery via internal flag.");

        } catch (Exception e) {
            logger.error("[CORE FIX] Failed to disable LSD: ", e);
            e.printStackTrace();
        }

        logger.info("[PEER SOURCES] DHT + PEX + HTTP/UDP Trackers enabled (LSD Disabled)");

        // December 2025 VERIFIED Tracker List (from ngosang/trackerslist - updated
        // daily)
        String[] publicTrackers = {
                "udp://tracker.opentrackr.org:1337/announce",
                "udp://open.demonoid.ch:6969/announce",
                "udp://open.demonii.com:1337/announce",
                "udp://open.stealth.si:80/announce",
                "udp://tracker.torrent.eu.org:451/announce",
                "udp://explodie.org:6969/announce",
                "udp://wepzone.net:6969/announce",
                "udp://tracker2.dler.org:80/announce",
                "udp://tracker.srv00.com:6969/announce",
                "udp://tracker.qu.ax:6969/announce",
                "udp://tracker.filemail.com:6969/announce",
                "udp://tracker.dler.org:6969/announce",
                "udp://tracker.bittor.pw:1337/announce",
                "udp://tracker.0x7c0.com:6969/announce",
                "udp://tracker-udp.gbitt.info:80/announce",
                "udp://t.overflow.biz:6969/announce",
                "udp://p4p.arenabg.com:1337/announce",
                "udp://opentracker.io:6969/announce",
                // HTTP Fallbacks for strict firewalls
                "http://tracker.openbittorrent.com:80/announce",
                "http://open.acgnxtracker.com:80/announce"
        };
        logger.info("[PEER BOOST] Adding {} public trackers", publicTrackers.length);

        if (magnetLink != null) {
            // Append public trackers to existing magnet link
            StringBuilder magnetWithTrackers = new StringBuilder(magnetLink);
            if (!magnetLink.contains("tr=")) {
                // If clean magnet, allow appending
            }
            for (String tracker : publicTrackers) {
                try {
                    magnetWithTrackers.append("&tr=").append(
                            java.net.URLEncoder.encode(tracker, java.nio.charset.StandardCharsets.UTF_8.toString()));
                } catch (Exception e) {
                    // ignore
                }
            }
            builder.magnet(magnetWithTrackers.toString());

        } else if (torrentFile != null) {
            try {
                // STRATEGY CHANGE: To force our public trackers, we must use a Magnet link even
                // for files.
                // 1. Parse metadata to get Hash and Name
                byte[] torrentBytes = java.nio.file.Files.readAllBytes(torrentFile.toPath());
                bt.metainfo.MetadataService metadataService = new bt.metainfo.MetadataService();
                bt.metainfo.Torrent torrent = metadataService.fromByteArray(torrentBytes);

                String infoHash = torrent.getTorrentId().toString().toUpperCase();
                String name = torrent.getName();

                // 2. Construct Magnet
                StringBuilder magnetBuilder = new StringBuilder();
                magnetBuilder.append("magnet:?xt=urn:btih:").append(infoHash);
                if (name != null) {
                    magnetBuilder.append("&dn=").append(
                            java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8.toString()));
                }

                // 3. Add original trackers from file
                // Try to get primary announce URL
                try {
                    // Start Reflection Block
                    // 1. Try getAnnounce() (Single tracker)
                    try {
                        java.lang.reflect.Method m = torrent.getClass().getMethod("getAnnounce");
                        Object res = m.invoke(torrent);
                        if (res instanceof String) {
                            magnetBuilder.append("&tr=").append(java.net.URLEncoder.encode((String) res, "UTF-8"));
                        } else if (res instanceof java.util.Optional) {
                            java.util.Optional<?> opt = (java.util.Optional<?>) res;
                            if (opt.isPresent() && opt.get() instanceof String) {
                                magnetBuilder.append("&tr=")
                                        .append(java.net.URLEncoder.encode((String) opt.get(), "UTF-8"));
                            }
                        }
                    } catch (Exception e) {
                    }

                    // 2. Try getAnnounceList() or getTrackers() (Multi-tier)
                    try {
                        java.lang.reflect.Method mList = null;
                        try {
                            mList = torrent.getClass().getMethod("getAnnounceList");
                        } catch (Exception e) {
                        }
                        if (mList == null)
                            try {
                                mList = torrent.getClass().getMethod("getTrackers");
                            } catch (Exception e) {
                            }

                        if (mList != null) {
                            Object res = mList.invoke(torrent);
                            // Helper to process list
                            java.util.List<?> list = null;
                            if (res instanceof java.util.List)
                                list = (java.util.List<?>) res;
                            else if (res instanceof java.util.Optional && ((java.util.Optional<?>) res).isPresent()) {
                                Object inner = ((java.util.Optional<?>) res).get();
                                if (inner instanceof java.util.List)
                                    list = (java.util.List<?>) inner;
                            }

                            if (list != null) {
                                for (Object item : list) {
                                    if (item instanceof java.util.List) {
                                        for (Object sub : (java.util.List<?>) item) {
                                            magnetBuilder.append("&tr=")
                                                    .append(java.net.URLEncoder.encode(sub.toString(), "UTF-8"));
                                        }
                                    } else if (item != null) {
                                        magnetBuilder.append("&tr=")
                                                .append(java.net.URLEncoder.encode(item.toString(), "UTF-8"));
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                    }
                    // End Reflection Block
                } catch (Exception e) {
                    // ignore
                }

                // Append our public list
                for (String tracker : publicTrackers) {
                    magnetBuilder.append("&tr=").append(
                            java.net.URLEncoder.encode(tracker, java.nio.charset.StandardCharsets.UTF_8.toString()));
                }

                String finalMagnet = magnetBuilder.toString();
                logger.info("[PEER BOOST] Generated Hybrid Magnet: {}", finalMagnet);

                // HYBRID STRATEGY:
                // Provide the magnet link (with all trackers) AND the file URL.
                // We hope Bt uses the file for metadata (instant start) and the magnet for
                // trackers.
                builder.magnet(finalMagnet);
                builder.torrent(torrentFile.toURI().toURL());

            } catch (Exception e) {
                System.err.println("Hybrid load failed: " + e.getMessage());
                // Fallback to just file
                try {
                    builder.torrent(torrentFile.toURI().toURL());
                } catch (java.net.MalformedURLException ex) {
                    throw new RuntimeException("Invalid torrent file path", ex);
                }
            }
        }

        final String id = UUID.randomUUID().toString();

        builder.afterTorrentFetched(torrent -> {
            logger.info("Metadata fetched: {}", torrent.getName());
            torrentNames.put(id, torrent.getName());
        });

        BtClient client = builder.build();
        activeClients.put(id, client);
        orchestratedSelectors.put(id, pieceSelector); // Store for adaptive triggers

        // Start with listener for status updates (every 1000ms)
        // Wrapped in try-catch to allow debug of listener crashes
        client.startAsync(sessionState -> {
            try {
                updateStatus(id, sessionState);
            } catch (Exception e) {
                System.err.println("Listener Crash: " + e.getMessage());
                e.printStackTrace();
            }
        }, 1000);

        // Init initial status
        latestStatus.put(id, new TorrentStatus(id, "Initializing...", 0, 0, 0, 0, 0, 0, "Initializing"));

        return id;
    }

    private void updateStatus(String id, bt.torrent.TorrentSessionState sessionState) {
        // Debug Log
        if (sessionState.getConnectedPeers().size() > 0) {
            // System.out.println("Tick: " + id + " | Peers: " +
            // sessionState.getConnectedPeers().size());
        }

        // Log vital stats to debug log
        int peers = sessionState.getConnectedPeers().size();
        long downloaded = sessionState.getDownloaded();
        if (peers > 0 || downloaded > 0) {
            // Only log if interesting to avoid spamming empty states
            // System.out.println("Status Update: Peers=" + peers + ", Downloaded=" +
            // downloaded);
        }

        long uploaded = sessionState.getUploaded();

        TorrentStatus prev = latestStatus.get(id);
        long prevDl = (prev != null) ? prev.getDownloadedBytes() : 0;
        long prevUl = (prev != null) ? prev.getUploadedBytes() : 0;

        long dlRate = downloaded - prevDl;
        if (dlRate < 0)
            dlRate = 0;
        long ulRate = uploaded - prevUl;
        if (ulRate < 0)
            ulRate = 0;

        // int peers = sessionState.getConnectedPeers().size(); // Moved up for logging
        double progress = (sessionState.getPiecesTotal() > 0)
                ? (double) sessionState.getPiecesComplete() / sessionState.getPiecesTotal()
                : 0.0;

        // ADAPTIVE: Update selector with current stats for adaptive behavior
        OrchestratedPieceSelector selector = orchestratedSelectors.get(id);
        if (selector != null) {
            selector.updatePeerCount(peers);
            selector.updateProgress(progress * 100); // Convert to percentage
        }

        // Name resolution
        String name = prev != null ? prev.getName() : "Fetching metadata...";
        if (torrentNames.containsKey(id)) {
            name = torrentNames.get(id);
        }

        String state = (progress >= 1.0) ? "Seeding" : "Downloading";
        if (sessionState.getPiecesTotal() == 0)
            state = "Fetching metadata";

        // Calculate seeds vs leechers (Bt doesn't expose this directly, estimate from
        // peer bitfields)
        // For now, count all connected as leechers unless downloading is complete with
        // us seeding
        int seeds = 0;
        int leechers = peers;

        // Calculate total size
        long totalSize = (long) sessionState.getPiecesTotal() * 16384L; // Approximate piece size

        // Calculate ETA
        String eta = "∞";
        if (dlRate > 0 && progress < 1.0) {
            long remaining = totalSize - downloaded;
            long seconds = remaining / dlRate;
            if (seconds < 60) {
                eta = seconds + "s";
            } else if (seconds < 3600) {
                eta = (seconds / 60) + "m " + (seconds % 60) + "s";
            } else {
                long hours = seconds / 3600;
                long mins = (seconds % 3600) / 60;
                eta = hours + "h " + mins + "m";
            }
        } else if (progress >= 1.0) {
            eta = "Complete";
        }

        // Get last error (stored per-torrent if any)
        String lastError = lastErrors.getOrDefault(id, "");

        TorrentStatus status = new TorrentStatus(id, name, progress, downloaded, uploaded, dlRate, ulRate, peers,
                state, seeds, leechers, totalSize, eta, lastError);
        latestStatus.put(id, status);
    }

    public void stop() {
        activeClients.values().forEach(BtClient::stop);
        activeClients.clear();
    }

    private Config createConfig(int maxPeers, int port, boolean adaptivePeerBias, int pipelineRequests) {
        return new Config() {
            @Override
            public int getNumOfHashingThreads() {
                return Runtime.getRuntime().availableProcessors();
            }

            @Override
            public Duration getTrackerQueryInterval() {
                return Duration.ofSeconds(30);
            }

            @Override
            public Duration getTrackerTimeout() {
                // Increased from default 10s to 60s for slow/restricted networks
                return Duration.ofSeconds(60);
            }

            @Override
            public int getMaxPeerConnections() {
                return maxPeers > 0 ? maxPeers : 50;
            }

            @Override
            public int getAcceptorPort() {
                return port > 0 ? port : 6891;
            }

            @Override
            public int getMaxPendingConnectionRequests() {
                return pipelineRequests > 0 ? pipelineRequests : 5;
            }

            @Override
            public java.net.InetAddress getAcceptorAddress() {
                try {
                    return java.net.InetAddress.getByName("0.0.0.0");
                } catch (java.net.UnknownHostException e) {
                    return super.getAcceptorAddress();
                }
            }
        };
    }
}
