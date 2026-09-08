package net.kcundercover.spectral_analyzer.controllers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.kcundercover.spectral_analyzer.rest.RestHelper;

/**
 * Controller for managing and selecting saved non-local REST servers.
 */
public class ServerListDialogController {
    private static final Logger SLDC_LOGGER = LoggerFactory.getLogger(ServerListDialogController.class);

    @FXML private ListView<ServerItem> serverListView;

    private final ObservableList<ServerItem> serverItems = FXCollections.observableArrayList();
    private final List<String> connectedServers = new ArrayList<>();

    @FXML
    public void initialize() {
        List<String> savedServers = RestHelper.getSavedServers();
        for (String url : savedServers) {
            serverItems.add(new ServerItem(url, false)); // Initialized to not load by default
        }

        serverListView.setItems(serverItems);
        serverListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ServerItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    CheckBox checkBox = new CheckBox(item.getUrl());
                    checkBox.selectedProperty().bindBidirectional(item.selectedProperty());
                    HBox box = new HBox(checkBox);
                    setGraphic(box);
                }
            }
        });
    }

    @FXML
    private void handleSelectAll(ActionEvent event) {
        for (ServerItem item : serverItems) {
            item.setSelected(true);
        }
    }

    @FXML
    private void handleDeselectAll(ActionEvent event) {
        for (ServerItem item : serverItems) {
            item.setSelected(false);
        }
    }

    @FXML
    private void handleRemoveSelected(ActionEvent event) {
        List<ServerItem> toRemove = new ArrayList<>();
        for (ServerItem item : serverItems) {
            if (item.isSelected()) {
                toRemove.add(item);
                RestHelper.removeServerUrl(item.getUrl());
                SLDC_LOGGER.info("Removed server URL from AppData: {}", item.getUrl());
            }
        }
        serverItems.removeAll(toRemove);
    }

    @FXML
    private void handleConnect(ActionEvent event) {
        connectedServers.clear();
        for (ServerItem item : serverItems) {
            if (item.isSelected()) {
                connectedServers.add(item.getUrl());
            }
        }
        closeStage(event);
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        connectedServers.clear();
        closeStage(event);
    }

    private void closeStage(ActionEvent event) {
        Node source = (Node) event.getSource();
        Stage stage = (Stage) source.getScene().getWindow();
        stage.close();
    }

    public List<String> getConnectedServers() {
        // return connectedServers;
        return Collections.unmodifiableList(this.connectedServers);

    }
}

class ServerItem {
    private final String url;
    private final javafx.beans.property.BooleanProperty selected;

    public ServerItem(String url, boolean selected) {
        this.url = url;
        this.selected = new javafx.beans.property.SimpleBooleanProperty(selected);
    }

    public String getUrl() {
        return url;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }

    public javafx.beans.property.BooleanProperty selectedProperty() {
        return selected;
    }
}
