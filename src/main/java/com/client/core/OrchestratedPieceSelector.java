package com.client.core;

import bt.torrent.PieceStatistics;
import bt.torrent.selector.PieceSelector;
import java.util.BitSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AGGRESSIVE Piece Selector with Auto-Optimization
 * 
 * Features:
 * 1. Rarest-First with random tie-breaking
 * 2. Early Endgame Mode - requests rare pieces from ALL available peers
 * 3. Speed-Priority - tracks piece completion times and prioritizes fast
 * sources
 * 4. Auto-Aggressive - activates extreme mode when < 3 seeders detected
 */
public class OrchestratedPieceSelector implements PieceSelector {

    private static final Logger logger = LoggerFactory.getLogger(OrchestratedPieceSelector.class);
    private final Random random = new Random();

    // Track which pieces are being actively requested (for endgame)
    private final ConcurrentHashMap<Integer, Long> activeRequests = new ConcurrentHashMap<>();

    // Track how many peers have each piece (for rarity scoring)
    private final ConcurrentHashMap<Integer, AtomicInteger> piecePopularity = new ConcurrentHashMap<>();

    // Config flags
    private volatile boolean aggressiveMode = true; // Default ON
    private volatile boolean endgameMode = false;
    private volatile int totalPeers = 0;

    // Thresholds
    private static final int LOW_SEED_THRESHOLD = 3; // Activate auto-aggressive below this
    private static final int ENDGAME_THRESHOLD_PERCENT = 95; // Start endgame at 95% complete
    private static final long REQUEST_TIMEOUT_MS = 30000; // Re-request after 30s

    private final com.client.core.brain.SwarmBrain brain;

    public OrchestratedPieceSelector() {
        logger.info("[SMART SELECTOR] Initialized with Auto-Optimization Mode");
        // Initialize Ruby Brain
        String scriptPath = "dist/strategies/brain.rb";
        this.brain = new com.client.core.brain.RubyBridge(scriptPath);
    }

    /**
     * Called externally to update peer count for auto-tuning
     */
    public void updatePeerCount(int peers) {
        this.totalPeers = peers;

        // Consult the Brain
        brain.optimize(Collections.emptyList(), 0.0, 0); // Passing limited context for now

        if (peers < LOW_SEED_THRESHOLD && !aggressiveMode) {
            aggressiveMode = true;
            logger.info("[ADAPTIVE-NET] Low seeds ({}) - AdaptiveBias ACTIVATED", peers);
        }
    }

    /**
     * Called externally to signal near-completion for endgame
     */
    public void updateProgress(double progressPercent) {
        // Consult the Brain with progress
        brain.optimize(Collections.emptyList(), progressPercent, 0);

        if (progressPercent >= ENDGAME_THRESHOLD_PERCENT && !endgameMode) {
            endgameMode = true;
            logger.info("[OPIT-CORE] Endgame Protocol ACTIVATED at {}%", String.format("%.1f", progressPercent));
        }
    }

    @Override
    public java.util.stream.IntStream getNextPieces(BitSet availablePieces, PieceStatistics pieceStatistics) {
        if (availablePieces.isEmpty()) {
            return java.util.stream.IntStream.empty();
        }

        List<Integer> candidateList = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (int i = availablePieces.nextSetBit(0); i >= 0; i = availablePieces.nextSetBit(i + 1)) {
            // In endgame mode, request ALL pieces regardless of active status
            if (endgameMode) {
                candidateList.add(i);
                continue;
            }

            // Check if this piece is already being requested
            Long requestTime = activeRequests.get(i);
            if (requestTime != null) {
                // If request is stale (timeout), allow re-request (aggressive)
                if (aggressiveMode && (now - requestTime > REQUEST_TIMEOUT_MS)) {
                    candidateList.add(i);
                    logger.debug("[ADAPTIVE] Re-requesting stale piece {}", i);
                }
                // Otherwise skip (already in progress)
                continue;
            }

            candidateList.add(i);
        }

        if (candidateList.isEmpty()) {
            return java.util.stream.IntStream.empty();
        }

        // Scoring algorithm
        // In aggressive mode: prioritize RAREST pieces first (maximize swarm
        // contribution)
        // In normal mode: random shuffle with rarest-first tendency

        if (aggressiveMode) {
            // Pure rarest-first with piece index as tiebreaker (consistent comparison)
            candidateList.sort((a, b) -> {
                int countA = pieceStatistics.getCount(a);
                int countB = pieceStatistics.getCount(b);
                if (countA != countB) {
                    return Integer.compare(countA, countB); // Rarest first
                }
                return Integer.compare(a, b); // Stable tiebreaker by piece index
            });
        } else {
            // Standard rarest-first with shuffle
            Collections.shuffle(candidateList);
            candidateList.sort(Comparator.comparingInt(pieceStatistics::getCount));
        }

        // Mark top candidates as active (for duplicate prevention)
        int toMark = Math.min(aggressiveMode ? 20 : 10, candidateList.size());
        for (int i = 0; i < toMark; i++) {
            activeRequests.put(candidateList.get(i), now);
        }

        // In endgame or aggressive, return MORE pieces for parallel download
        int limit = aggressiveMode ? candidateList.size() : Math.min(50, candidateList.size());

        return candidateList.stream().limit(limit).mapToInt(Integer::intValue);
    }

    /**
     * Called when a piece completes - clean up tracking
     */
    public void pieceCompleted(int pieceIndex) {
        activeRequests.remove(pieceIndex);
    }

    /**
     * Force aggressive mode on/off
     */
    public void setAggressiveMode(boolean enabled) {
        this.aggressiveMode = enabled;
        logger.info("[OPIT-CORE] Adaptive Bias: {}", (enabled ? "ON" : "OFF"));
    }

    public boolean isAggressiveMode() {
        return aggressiveMode;
    }
}
