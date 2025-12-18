package com.client.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class SettingsManager {
    private static final String CONFIG_DIR = ".ModernTorrentClient";
    private static final String CONFIG_FILE = "config.properties";

    // Keys
    public static final String KEY_DOWNLOAD_DIR = "download.dir";
    public static final String KEY_MAX_PEERS = "network.max_peers";
    public static final String KEY_PORT = "network.port";
    public static final String KEY_THEME = "ui.theme";

    private static SettingsManager instance;
    private final Properties properties;
    private final File configFile;

    private SettingsManager() {
        properties = new Properties();
        String userHome = System.getProperty("user.home");
        File configDir = new File(userHome, CONFIG_DIR);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        configFile = new File(configDir, CONFIG_FILE);
        load();
    }

    public static synchronized SettingsManager getInstance() {
        if (instance == null) {
            instance = new SettingsManager();
        }
        return instance;
    }

    private void load() {
        if (configFile.exists()) {
            try (FileInputStream in = new FileInputStream(configFile)) {
                properties.load(in);
            } catch (IOException e) {
                System.err.println("Failed to load settings: " + e.getMessage());
            }
        }
        // Set defaults if missing
        if (!properties.containsKey(KEY_DOWNLOAD_DIR)) {
            properties.setProperty(KEY_DOWNLOAD_DIR,
                    Paths.get(System.getProperty("user.home"), "Downloads").toString());
        }
        if (!properties.containsKey(KEY_MAX_PEERS)) {
            properties.setProperty(KEY_MAX_PEERS, "50");
        }
        if (!properties.containsKey(KEY_PORT)) {
            properties.setProperty(KEY_PORT, "6891");
        }
    }

    public void save() throws IOException {
        try (FileOutputStream out = new FileOutputStream(configFile)) {
            properties.store(out, "ModernTorrentClient Configuration");
        }
    }

    public String get(String key) {
        return properties.getProperty(key);
    }

    public Path getPath(String key) {
        return Paths.get(properties.getProperty(key));
    }

    public void set(String key, String value) {
        properties.setProperty(key, value);
    }

    public int getInt(String key) {
        try {
            return Integer.parseInt(properties.getProperty(key));
        } catch (NumberFormatException e) {
            return 0; // Or default
        }
    }
}
