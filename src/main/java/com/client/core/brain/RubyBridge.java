package com.client.core.brain;

import bt.net.Peer;
import org.jruby.embed.ScriptingContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

public class RubyBridge implements SwarmBrain {
    private static final Logger logger = LoggerFactory.getLogger(RubyBridge.class);
    private final ScriptingContainer container;
    private final String scriptPath;
    private Object receiver;
    private long lastModified;

    public RubyBridge(String scriptPath) {
        this.scriptPath = scriptPath;
        this.container = new ScriptingContainer();
        reload();
    }

    @Override
    public void optimize(Collection<Peer> peers, double progress, long downloadRate) {
        if (receiver != null) {
            checkReload();
            try {
                // expecting a method 'on_swarm_update(peers, progress, download_rate)' in the
                // Ruby script
                container.callMethod(receiver, "on_swarm_update", peers, progress, downloadRate);
            } catch (Exception e) {
                logger.error("[RUBY-BRAIN] Enhancement execution failed", e);
            }
        }
    }

    private void checkReload() {
        // Hot-reload check (simple timestamp based)
        // Only check every 5 seconds or just do it here for simplicity of POC?
        // Let's do it on every tick for now, but in real world we'd cache it.
        File f = new File(scriptPath);
        if (f.exists() && f.lastModified() > lastModified) {
            logger.info("[RUBY-BRAIN] Change detected. Hot-reloading logic...");
            reload();
        }
    }

    @Override
    public void reload() {
        try {
            Path path = Paths.get(scriptPath);
            if (Files.exists(path)) {
                // Read script to string (reliable way)
                String scriptContent = new String(Files.readAllBytes(path));
                container.runScriptlet(scriptContent);

                // Retrieve the global variable set by the script
                // In Ruby: $brain_instance
                // In Java Embed: container.get("brain_instance") should work for global
                // variable
                this.receiver = container.get("brain_instance");

                if (this.receiver == null) {
                    logger.warn("[RUBY-BRAIN] Script loaded but 'brain_instance' global was null.");
                } else {
                    logger.info("[RUBY-BRAIN] Logic loaded successfully from {}", scriptPath);
                }

                this.lastModified = new File(scriptPath).lastModified();

            } else {
                logger.warn("[RUBY-BRAIN] Script not found at {}", scriptPath);
            }
        } catch (Exception e) {
            logger.error("[RUBY-BRAIN] Failed to load Ruby script", e);
        }
    }
}
