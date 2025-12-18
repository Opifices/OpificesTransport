package com.client.ui;

import com.client.config.SettingsManager;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import java.io.File;

public class SettingsController {

    @FXML
    private TextField downloadPathField;
    @FXML
    private CheckBox autoStartCheck;
    @FXML
    private CheckBox darkModeCheck;

    @FXML
    private TextField portField;
    @FXML
    private CheckBox randomizePortCheck;
    @FXML
    private TextField maxPeersField;

    @FXML
    private TextField maxDownloadSpeedField;
    @FXML
    private TextField maxUploadSpeedField;

    // Optimizations
    @FXML
    private CheckBox safeModeCheck;
    @FXML
    private TextField userAgentField;
    @FXML
    private CheckBox adaptivePeerBiasCheck;

    // Speed Boosters
    @FXML
    private TextField maxConnectionsField;
    @FXML
    private TextField throughputPipeliningField;
    @FXML
    private CheckBox leecherModeCheck;
    @FXML
    private CheckBox dhtAggressiveCheck;

    private Stage dialogStage;
    private final SettingsManager settings = SettingsManager.getInstance();

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    @FXML
    public void initialize() {
        downloadPathField.setText(settings.get(SettingsManager.KEY_DOWNLOAD_DIR));
        maxPeersField.setText(settings.get(SettingsManager.KEY_MAX_PEERS));
        portField.setText(settings.get(SettingsManager.KEY_PORT));

        // Defaults for new fields
        String dlRate = settings.get("bandwidth.max_download_rate");
        String ulRate = settings.get("bandwidth.max_upload_rate");
        maxDownloadSpeedField.setText(dlRate != null ? dlRate : "0");
        maxUploadSpeedField.setText(ulRate != null ? ulRate : "0");

        // Checkboxes
        autoStartCheck.setSelected(Boolean.parseBoolean(settings.get("general.auto_start")));
        darkModeCheck.setSelected(Boolean.parseBoolean(settings.get("ui.dark_mode")));
        randomizePortCheck.setSelected(Boolean.parseBoolean(settings.get("network.random_port")));

        // Optimizations
        safeModeCheck.setSelected(Boolean.parseBoolean(settings.get("optimizations.disabled")));
        userAgentField.setText(settings.get("optimizations.user_agent"));
        adaptivePeerBiasCheck.setSelected(Boolean.parseBoolean(settings.get("optimizations.adaptive_peer_bias")));

        // Advanced Hacks

        // Speed Boosters
        String maxConn = settings.get("optimizations.max_connections");
        if (maxConnectionsField != null)
            maxConnectionsField.setText(maxConn != null ? maxConn : "200");
        String pipeline = settings.get("optimizations.throughput_pipelining");
        if (throughputPipeliningField != null)
            throughputPipeliningField.setText(pipeline != null ? pipeline : "50");
        if (leecherModeCheck != null)
            leecherModeCheck.setSelected(Boolean.parseBoolean(settings.get("optimizations.leecher_mode")));
        if (dhtAggressiveCheck != null)
            dhtAggressiveCheck.setSelected(Boolean.parseBoolean(settings.get("optimizations.dht_aggressive")));
    }

    @FXML
    private void handleBrowse() {
        try {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Select Download Folder");

            String currentPath = downloadPathField.getText();
            if (currentPath != null && !currentPath.trim().isEmpty()) {
                File initialDir = new File(currentPath);
                if (initialDir.exists() && initialDir.isDirectory()) {
                    directoryChooser.setInitialDirectory(initialDir);
                }
            }

            File selectedDirectory = directoryChooser.showDialog(dialogStage);
            if (selectedDirectory != null) {
                downloadPathField.setText(selectedDirectory.getAbsolutePath());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Could not open folder browser");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleSave() {
        try {
            // Safe helper to avoid potential NPEs if FXML didn't inject
            settings.set(SettingsManager.KEY_DOWNLOAD_DIR, safeGet(downloadPathField));
            settings.set(SettingsManager.KEY_MAX_PEERS, safeGet(maxPeersField));
            settings.set(SettingsManager.KEY_PORT, safeGet(portField));

            settings.set("bandwidth.max_download_rate", safeGet(maxDownloadSpeedField));
            settings.set("bandwidth.max_upload_rate", safeGet(maxUploadSpeedField));

            settings.set("general.auto_start", String.valueOf(autoStartCheck != null && autoStartCheck.isSelected()));
            settings.set("ui.dark_mode", String.valueOf(darkModeCheck != null && darkModeCheck.isSelected()));
            settings.set("network.random_port",
                    String.valueOf(randomizePortCheck != null && randomizePortCheck.isSelected()));

            settings.set("optimizations.disabled", String.valueOf(safeModeCheck != null && safeModeCheck.isSelected()));
            settings.set("optimizations.user_agent", safeGet(userAgentField));
            settings.set("optimizations.adaptive_peer_bias",
                    String.valueOf(adaptivePeerBiasCheck != null && adaptivePeerBiasCheck.isSelected()));

            // Speed Boosters
            settings.set("optimizations.max_connections", safeGet(maxConnectionsField));
            settings.set("optimizations.throughput_pipelining", safeGet(throughputPipeliningField));
            settings.set("optimizations.leecher_mode",
                    String.valueOf(leecherModeCheck != null && leecherModeCheck.isSelected()));
            settings.set("optimizations.dht_aggressive",
                    String.valueOf(dhtAggressiveCheck != null && dhtAggressiveCheck.isSelected()));

            settings.save();
        } catch (Exception e) {
            e.printStackTrace(); // This goes to the debug log
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Settings Error");
            alert.setHeaderText("Failed to save settings");
            // Show the actual error class and message
            alert.setContentText(e.getClass().getSimpleName() + ": " + e.getMessage());
            alert.showAndWait();
        } finally {
            if (dialogStage != null) {
                dialogStage.close();
            }
        }
    }

    private String safeGet(TextField field) {
        if (field == null)
            return "";
        String text = field.getText();
        return text != null ? text : "";
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

}