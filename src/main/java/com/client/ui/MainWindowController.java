package com.client.ui;

import com.client.core.TorrentService;
import com.client.core.TorrentStatus;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import java.io.File;

public class MainWindowController {

    @FXML
    private TextField magnetInput;
    @FXML
    private Button downloadButton;
    @FXML
    private TableView<TorrentViewModel> torrentTable;
    @FXML
    private TableColumn<TorrentViewModel, String> nameCol;
    @FXML
    private TableColumn<TorrentViewModel, String> progressCol;
    @FXML
    private TableColumn<TorrentViewModel, String> downSpeedCol;
    @FXML
    private TableColumn<TorrentViewModel, String> upSpeedCol;
    @FXML
    private TableColumn<TorrentViewModel, String> seedsCol;
    @FXML
    private TableColumn<TorrentViewModel, String> peersCol;
    @FXML
    private TableColumn<TorrentViewModel, String> etaCol;
    @FXML
    private TableColumn<TorrentViewModel, String> statusCol;
    @FXML
    private Label statusLabel;
    @FXML
    private Label refreshLabel;

    // Details Panel
    @FXML
    private VBox detailsPanel;
    @FXML
    private Label detailsTitle;
    @FXML
    private Label detailDownloaded;
    @FXML
    private Label detailUploaded;
    @FXML
    private Label detailSeeds;
    @FXML
    private Label detailLeechers;
    @FXML
    private Label detailSize;
    @FXML
    private Label detailEta;
    @FXML
    private Label detailError;

    private TorrentService torrentService;
    private final ObservableList<TorrentViewModel> torrents = FXCollections.observableArrayList();
    private java.util.concurrent.ScheduledExecutorService refreshExecutor;
    private String selectedTorrentId = null;

    public void initialize() {
        torrentService = new TorrentService();

        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        progressCol.setCellValueFactory(new PropertyValueFactory<>("progress"));
        downSpeedCol.setCellValueFactory(new PropertyValueFactory<>("downSpeed"));
        upSpeedCol.setCellValueFactory(new PropertyValueFactory<>("upSpeed"));
        if (seedsCol != null)
            seedsCol.setCellValueFactory(new PropertyValueFactory<>("seeds"));
        peersCol.setCellValueFactory(new PropertyValueFactory<>("peers"));
        if (etaCol != null)
            etaCol.setCellValueFactory(new PropertyValueFactory<>("eta"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));

        torrentTable.setItems(torrents);

        // Selection listener for details panel
        torrentTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                selectedTorrentId = newSel.getId();
                updateDetailsPanel(newSel);
            } else {
                selectedTorrentId = null;
                clearDetailsPanel();
            }
        });

        // Context Menu for Remove
        ContextMenu contextMenu = new ContextMenu();
        MenuItem removeItem = new MenuItem("Remove Download");
        removeItem.setOnAction(e -> handleRemoveAction());
        contextMenu.getItems().add(removeItem);
        torrentTable.setContextMenu(contextMenu);

        // Start polling for updates
        refreshExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        refreshExecutor.scheduleAtFixedRate(this::updateUI, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void updateDetailsPanel(TorrentViewModel vm) {
        if (detailsTitle != null)
            detailsTitle.setText(vm.getName());
        if (detailDownloaded != null)
            detailDownloaded.setText(vm.getDownloaded());
        if (detailUploaded != null)
            detailUploaded.setText(vm.getUploaded());
        if (detailSeeds != null)
            detailSeeds.setText(vm.getSeeds());
        if (detailLeechers != null)
            detailLeechers.setText(vm.getLeechers());
        if (detailSize != null)
            detailSize.setText(vm.getSize());
        if (detailEta != null)
            detailEta.setText(vm.getEta());
        if (detailError != null) {
            String error = vm.getError();
            detailError.setText(error != null && !error.isEmpty() ? "Error: " + error : "");
        }
    }

    private void clearDetailsPanel() {
        if (detailsTitle != null)
            detailsTitle.setText("Select a torrent to see details");
        if (detailDownloaded != null)
            detailDownloaded.setText("-");
        if (detailUploaded != null)
            detailUploaded.setText("-");
        if (detailSeeds != null)
            detailSeeds.setText("-");
        if (detailLeechers != null)
            detailLeechers.setText("-");
        if (detailSize != null)
            detailSize.setText("-");
        if (detailEta != null)
            detailEta.setText("-");
        if (detailError != null)
            detailError.setText("");
    }

    private void updateUI() {
        try {
            java.util.List<TorrentStatus> statuses = torrentService.getAllTorrentsStatus();
            Platform.runLater(() -> {
                if (refreshLabel != null) {
                    refreshLabel.setText("Last update: " + java.time.LocalTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
                }
                for (TorrentStatus status : statuses) {
                    TorrentViewModel vm = findViewModel(status.getId());
                    if (vm == null) {
                        vm = new TorrentViewModel();
                        vm.setId(status.getId());
                        torrents.add(vm);
                    }

                    // Update all fields
                    vm.name.set(status.getName());
                    vm.progress.set(String.format("%.1f%%", status.getProgress() * 100));
                    vm.downSpeed.set(formatSpeed(status.getDownloadRate()));
                    vm.upSpeed.set(formatSpeed(status.getUploadRate()));
                    vm.seeds.set(String.valueOf(status.getSeeds()));
                    vm.peers.set(String.valueOf(status.getConnectedPeers()));
                    vm.eta.set(status.getEta());
                    vm.status.set(status.getState());
                    vm.downloaded.set(formatBytes(status.getDownloadedBytes()));
                    vm.uploaded.set(formatBytes(status.getUploadedBytes()));
                    vm.leechers.set(String.valueOf(status.getLeechers()));
                    vm.size.set(formatBytes(status.getTotalSize()));
                    vm.error.set(status.getLastError());

                    // Update details panel if this torrent is selected
                    if (status.getId().equals(selectedTorrentId)) {
                        updateDetailsPanel(vm);
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private TorrentViewModel findViewModel(String id) {
        for (TorrentViewModel vm : torrents) {
            if (id != null && id.equals(vm.getId())) {
                return vm;
            }
        }
        return null;
    }

    private String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 1024)
            return bytesPerSecond + " B/s";
        double kb = bytesPerSecond / 1024.0;
        if (kb < 1024)
            return String.format("%.1f KB/s", kb);
        double mb = kb / 1024.0;
        return String.format("%.1f MB/s", mb);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024)
            return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024)
            return String.format("%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }

    public void shutdown() {
        if (refreshExecutor != null)
            refreshExecutor.shutdownNow();
        if (torrentService != null)
            torrentService.stop();
    }

    @FXML
    private void handleRemoveAction() {
        TorrentViewModel selected = torrentTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            torrentService.stopDownload(selected.getId());
            torrents.remove(selected);
            statusLabel.setText("Removed: " + selected.getName());
            clearDetailsPanel();
        }
    }

    @FXML
    private void handleOpenTorrentAction() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open .torrent File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Torrent Files", "*.torrent"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File selectedFile = fileChooser.showOpenDialog(magnetInput.getScene().getWindow());
        if (selectedFile != null) {
            statusLabel.setText("Starting file download...");
            TorrentViewModel vm = new TorrentViewModel();
            vm.name.set("Fetching metadata...");
            torrents.add(vm);

            new Thread(() -> {
                try {
                    String id = torrentService.startDownload(selectedFile);
                    Platform.runLater(() -> {
                        vm.setId(id);
                        vm.status.set("Downloading");
                        vm.name.set(selectedFile.getName());
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        statusLabel.setText("Error: " + e.getMessage());
                        torrents.remove(vm);
                    });
                }
            }).start();
        }
    }

    @FXML
    private void handleDownloadAction() {
        String magnet = magnetInput.getText();
        if (magnet != null && !magnet.isEmpty()) {
            statusLabel.setText("Starting download...");
            TorrentViewModel vm = new TorrentViewModel();
            vm.name.set("Fetching metadata...");
            torrents.add(vm);

            new Thread(() -> {
                try {
                    String id = torrentService.startDownload(magnet);
                    Platform.runLater(() -> {
                        vm.setId(id);
                        vm.status.set("Downloading");
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        statusLabel.setText("Error: " + e.getMessage());
                        torrents.remove(vm);
                    });
                }
            }).start();

            magnetInput.clear();
        }
    }

    @FXML
    private void handleSettingsAction() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/com/client/ui/SettingsWindow.fxml"));
            javafx.scene.Parent page = loader.load();

            javafx.stage.Stage dialogStage = new javafx.stage.Stage();
            dialogStage.setTitle("Settings");
            dialogStage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            dialogStage.initOwner(magnetInput.getScene().getWindow());
            javafx.scene.Scene scene = new javafx.scene.Scene(page);
            scene.getStylesheets().add(getClass().getResource("/com/client/ui/styles.css").toExternalForm());
            dialogStage.setScene(scene);

            SettingsController controller = loader.getController();
            controller.setDialogStage(dialogStage);

            dialogStage.showAndWait();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    // ViewModel class for the TableView
    public static class TorrentViewModel {
        private String id;
        public final SimpleStringProperty name = new SimpleStringProperty("");
        public final SimpleStringProperty progress = new SimpleStringProperty("0%");
        public final SimpleStringProperty downSpeed = new SimpleStringProperty("0 B/s");
        public final SimpleStringProperty upSpeed = new SimpleStringProperty("0 B/s");
        public final SimpleStringProperty seeds = new SimpleStringProperty("0");
        public final SimpleStringProperty peers = new SimpleStringProperty("0");
        public final SimpleStringProperty eta = new SimpleStringProperty("∞");
        public final SimpleStringProperty status = new SimpleStringProperty("Initializing");
        public final SimpleStringProperty downloaded = new SimpleStringProperty("0 B");
        public final SimpleStringProperty uploaded = new SimpleStringProperty("0 B");
        public final SimpleStringProperty leechers = new SimpleStringProperty("0");
        public final SimpleStringProperty size = new SimpleStringProperty("0 B");
        public final SimpleStringProperty error = new SimpleStringProperty("");

        public void setId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        // Property accessors for TableView binding
        public SimpleStringProperty nameProperty() {
            return name;
        }

        public SimpleStringProperty progressProperty() {
            return progress;
        }

        public SimpleStringProperty downSpeedProperty() {
            return downSpeed;
        }

        public SimpleStringProperty upSpeedProperty() {
            return upSpeed;
        }

        public SimpleStringProperty seedsProperty() {
            return seeds;
        }

        public SimpleStringProperty peersProperty() {
            return peers;
        }

        public SimpleStringProperty etaProperty() {
            return eta;
        }

        public SimpleStringProperty statusProperty() {
            return status;
        }

        // Getters
        public String getName() {
            return name.get();
        }

        public String getProgress() {
            return progress.get();
        }

        public String getDownSpeed() {
            return downSpeed.get();
        }

        public String getUpSpeed() {
            return upSpeed.get();
        }

        public String getSeeds() {
            return seeds.get();
        }

        public String getPeers() {
            return peers.get();
        }

        public String getEta() {
            return eta.get();
        }

        public String getStatus() {
            return status.get();
        }

        public String getDownloaded() {
            return downloaded.get();
        }

        public String getUploaded() {
            return uploaded.get();
        }

        public String getLeechers() {
            return leechers.get();
        }

        public String getSize() {
            return size.get();
        }

        public String getError() {
            return error.get();
        }
    }
}
