package com.client.core.brain;

import bt.net.Peer;
import java.util.Collection;

public interface SwarmBrain {
    /**
     * Optimizes the swarm strategy based on current metrics.
     * 
     * @param peers        The list of currently connected peers.
     * @param progress     The download progress (0.0 to 100.0).
     * @param downloadRate The current download rate in bytes/sec.
     */
    void optimize(Collection<Peer> peers, double progress, long downloadRate);

    /**
     * Reloads the logic from the source file.
     */
    void reload();
}
