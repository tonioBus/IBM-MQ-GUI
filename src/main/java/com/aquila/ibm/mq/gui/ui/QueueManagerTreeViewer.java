package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.config.ConfigManager;
import com.aquila.ibm.mq.gui.model.ConnectionConfig;
import com.aquila.ibm.mq.gui.model.HierarchyConfig;
import com.aquila.ibm.mq.gui.model.HierarchyNode;
import com.aquila.ibm.mq.gui.mq.MQConnectionManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tree viewer for queue manager hierarchy with folders.
 * Supports selection, drag-drop, context menus, and toolbar operations.
 */
public class QueueManagerTreeViewer extends Composite {
    private static final Logger log = LoggerFactory.getLogger(QueueManagerTreeViewer.class);

    private Tree tree;
    private HierarchyConfig hierarchy;
    private final MQConnectionManager connectionManager;
    private final ConfigManager configManager;

    // Map TreeItem to HierarchyNode ID for quick lookup
    private final Map<TreeItem, String> treeItemToNodeId;
    private final Map<String, TreeItem> nodeIdToTreeItem;

    // Selection listener
    private Consumer<SelectionEvent> selectionListener;

    /**
     * Event fired when a tree node is selected.
     */
    public static class SelectionEvent {
        public final HierarchyNode node;
        public final SelectionType type;

        public SelectionEvent(HierarchyNode node, SelectionType type) {
            this.node = node;
            this.type = type;
        }
    }

    public enum SelectionType {
        FOLDER,
        QUEUE_MANAGER,
        NONE
    }

    public QueueManagerTreeViewer(Composite parent, int style,
                                   MQConnectionManager connectionManager,
                                   ConfigManager configManager) {
        super(parent, style);
        this.connectionManager = connectionManager;
        this.configManager = configManager;
        this.treeItemToNodeId = new HashMap<>();
        this.nodeIdToTreeItem = new HashMap<>();

        setLayout(new GridLayout());
        createToolbar();
        createTree();
        createContextMenu();
        setupDragAndDrop();
        setupKeyboardShortcuts();
    }

    private void createToolbar() {
        ToolBar toolbar = new ToolBar(this, SWT.FLAT | SWT.HORIZONTAL);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // Add Folder button
        ToolItem addFolderItem = new ToolItem(toolbar, SWT.PUSH);
        addFolderItem.setText("New Folder");
        addFolderItem.setToolTipText("Create a new folder");
        addFolderItem.addListener(SWT.Selection, e -> addFolder());

        // Add Queue Manager button
        ToolItem addQMItem = new ToolItem(toolbar, SWT.PUSH);
        addQMItem.setText("Add QM");
        addQMItem.setToolTipText("Add a queue manager to the hierarchy");
        addQMItem.addListener(SWT.Selection, e -> addQueueManager());

        // Separator
        new ToolItem(toolbar, SWT.SEPARATOR);

        // Edit button
        ToolItem editItem = new ToolItem(toolbar, SWT.PUSH);
        editItem.setText("Rename");
        editItem.setToolTipText("Rename selected item");
        editItem.addListener(SWT.Selection, e -> renameSelected());

        // Delete button
        ToolItem deleteItem = new ToolItem(toolbar, SWT.PUSH);
        deleteItem.setText("Delete");
        deleteItem.setToolTipText("Delete selected item");
        deleteItem.addListener(SWT.Selection, e -> deleteSelected());

        // Separator
        new ToolItem(toolbar, SWT.SEPARATOR);

        // Connect button
        ToolItem connectItem = new ToolItem(toolbar, SWT.PUSH);
        connectItem.setText("Connect");
        connectItem.setToolTipText("Connect to selected queue manager");
        connectItem.addListener(SWT.Selection, e -> connectSelected());

        // Disconnect button
        ToolItem disconnectItem = new ToolItem(toolbar, SWT.PUSH);
        disconnectItem.setText("Disconnect");
        disconnectItem.setToolTipText("Disconnect from selected queue manager");
        disconnectItem.addListener(SWT.Selection, e -> disconnectSelected());

        // Separator
        new ToolItem(toolbar, SWT.SEPARATOR);

        // Refresh button
        ToolItem refreshItem = new ToolItem(toolbar, SWT.PUSH);
        refreshItem.setText("Refresh");
        refreshItem.setToolTipText("Refresh connection status");
        refreshItem.addListener(SWT.Selection, e -> updateAllConnectionIcons());

        log.debug("Toolbar created");
    }

    private void createTree() {
        tree = new Tree(this, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
        tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // Add selection listener
        tree.addListener(SWT.Selection, event -> {
            TreeItem[] selection = tree.getSelection();
            if (selection.length > 0) {
                onTreeItemSelected(selection[0]);
            } else {
                fireSelectionEvent(null, SelectionType.NONE);
            }
        });

        log.debug("Tree widget created");
    }

    private void createContextMenu() {
        Menu menu = new Menu(tree);
        tree.setMenu(menu);

        // Dynamically build menu based on selection
        menu.addListener(SWT.Show, e -> {
            // Clear existing items
            for (MenuItem item : menu.getItems()) {
                item.dispose();
            }

            // Build menu based on selection
            HierarchyNode selected = getSelectedNode();
            if (selected == null) {
                createRootContextMenu(menu);
            } else if (selected.isFolder()) {
                createFolderContextMenu(menu, selected);
            } else {
                createQueueManagerContextMenu(menu, selected);
            }
        });

        log.debug("Context menu created");
    }

    private void createRootContextMenu(Menu menu) {
        MenuItem addFolderItem = new MenuItem(menu, SWT.PUSH);
        addFolderItem.setText("Add Folder...");
        addFolderItem.addListener(SWT.Selection, e -> addFolder());

        MenuItem addQMItem = new MenuItem(menu, SWT.PUSH);
        addQMItem.setText("Add Queue Manager...");
        addQMItem.addListener(SWT.Selection, e -> addQueueManager());
    }

    private void createFolderContextMenu(Menu menu, HierarchyNode folder) {
        MenuItem addFolderItem = new MenuItem(menu, SWT.PUSH);
        addFolderItem.setText("Add Folder...");
        addFolderItem.addListener(SWT.Selection, e -> addFolder());

        MenuItem addQMItem = new MenuItem(menu, SWT.PUSH);
        addQMItem.setText("Add Queue Manager...");
        addQMItem.addListener(SWT.Selection, e -> addQueueManager());

        new MenuItem(menu, SWT.SEPARATOR);

        MenuItem renameItem = new MenuItem(menu, SWT.PUSH);
        renameItem.setText("Rename...");
        renameItem.addListener(SWT.Selection, e -> renameSelected());

        MenuItem deleteItem = new MenuItem(menu, SWT.PUSH);
        deleteItem.setText("Delete");
        deleteItem.addListener(SWT.Selection, e -> deleteSelected());

        // Connect/Disconnect all (if folder has queue managers)
        List<HierarchyNode> children = hierarchy.getChildren(folder.getId());
        boolean hasQueueManagers = children.stream().anyMatch(HierarchyNode::isQueueManager);

        if (hasQueueManagers) {
            new MenuItem(menu, SWT.SEPARATOR);

            MenuItem connectAllItem = new MenuItem(menu, SWT.PUSH);
            connectAllItem.setText("Connect All");
            connectAllItem.addListener(SWT.Selection, e -> connectAllInFolder(folder));

            MenuItem disconnectAllItem = new MenuItem(menu, SWT.PUSH);
            disconnectAllItem.setText("Disconnect All");
            disconnectAllItem.addListener(SWT.Selection, e -> disconnectAllInFolder(folder));
        }
    }

    private void createQueueManagerContextMenu(Menu menu, HierarchyNode qm) {
        String connectionId = qm.getConnectionConfigId();
        boolean isConnected = connectionManager.isConnected(connectionId);

        if (isConnected) {
            MenuItem disconnectItem = new MenuItem(menu, SWT.PUSH);
            disconnectItem.setText("Disconnect");
            disconnectItem.addListener(SWT.Selection, e -> disconnectSelected());
        } else {
            MenuItem connectItem = new MenuItem(menu, SWT.PUSH);
            connectItem.setText("Connect");
            connectItem.addListener(SWT.Selection, e -> connectSelected());
        }

        new MenuItem(menu, SWT.SEPARATOR);

        MenuItem editConnItem = new MenuItem(menu, SWT.PUSH);
        editConnItem.setText("Edit Connection...");
        editConnItem.addListener(SWT.Selection, e -> editConnectionConfig(qm));

        MenuItem removeItem = new MenuItem(menu, SWT.PUSH);
        removeItem.setText("Remove from Hierarchy");
        removeItem.addListener(SWT.Selection, e -> deleteSelected());
    }

    private void connectAllInFolder(HierarchyNode folder) {
        List<HierarchyNode> children = hierarchy.getChildren(folder.getId());
        int connected = 0;

        for (HierarchyNode child : children) {
            if (child.isQueueManager()) {
                String connectionId = child.getConnectionConfigId();
                if (!connectionManager.isConnected(connectionId)) {
                    com.aquila.ibm.mq.gui.model.ConnectionConfig config =
                        configManager.loadConnections().stream()
                            .filter(c -> connectionId.equals(c.getName()))
                            .findFirst()
                            .orElse(null);

                    if (config != null) {
                        try {
                            connectionManager.connect(connectionId, config);
                            updateNodeIcon(child.getId());
                            connected++;
                        } catch (Exception e) {
                            log.error("Failed to connect to: {}", child.getName(), e);
                        }
                    }
                }
            }
        }

        MessageBox box = new MessageBox(getShell(), SWT.ICON_INFORMATION | SWT.OK);
        box.setText("Connect All");
        box.setMessage(String.format("Connected to %d queue manager(s)", connected));
        box.open();
    }

    private void disconnectAllInFolder(HierarchyNode folder) {
        List<HierarchyNode> children = hierarchy.getChildren(folder.getId());
        int disconnected = 0;

        for (HierarchyNode child : children) {
            if (child.isQueueManager()) {
                String connectionId = child.getConnectionConfigId();
                if (connectionManager.isConnected(connectionId)) {
                    connectionManager.disconnect(connectionId);
                    updateNodeIcon(child.getId());
                    disconnected++;
                }
            }
        }

        MessageBox box = new MessageBox(getShell(), SWT.ICON_INFORMATION | SWT.OK);
        box.setText("Disconnect All");
        box.setMessage(String.format("Disconnected from %d queue manager(s)", disconnected));
        box.open();
    }

    private void editConnectionConfig(HierarchyNode qm) {
        MessageBox box = new MessageBox(getShell(), SWT.ICON_INFORMATION | SWT.OK);
        box.setText("Edit Connection");
        box.setMessage("Please use the Connection menu to edit connection configurations.");
        box.open();
    }

    private void setupDragAndDrop() {
        // Drag source
        DragSource dragSource = new DragSource(tree, DND.DROP_MOVE);
        dragSource.setTransfer(new TextTransfer[]{TextTransfer.getInstance()});

        dragSource.addDragListener(new DragSourceAdapter() {
            @Override
            public void dragStart(DragSourceEvent event) {
                TreeItem[] selection = tree.getSelection();
                if (selection.length == 0) {
                    event.doit = false;
                    return;
                }

                String nodeId = treeItemToNodeId.get(selection[0]);
                if (nodeId == null) {
                    event.doit = false;
                    return;
                }

                event.doit = true;
            }

            @Override
            public void dragSetData(DragSourceEvent event) {
                TreeItem[] selection = tree.getSelection();
                if (selection.length > 0) {
                    String nodeId = treeItemToNodeId.get(selection[0]);
                    if (nodeId != null) {
                        event.data = nodeId;
                    }
                }
            }
        });

        // Drop target
        DropTarget dropTarget = new DropTarget(tree, DND.DROP_MOVE);
        dropTarget.setTransfer(new TextTransfer[]{TextTransfer.getInstance()});

        dropTarget.addDropListener(new DropTargetAdapter() {
            @Override
            public void dragOver(DropTargetEvent event) {
                event.feedback = DND.FEEDBACK_SELECT | DND.FEEDBACK_SCROLL;

                if (event.item != null) {
                    TreeItem item = (TreeItem) event.item;
                    String targetNodeId = treeItemToNodeId.get(item);

                    if (targetNodeId != null && event.data instanceof String) {
                        // Could validate here, but validation happens in drop()
                        event.detail = DND.DROP_MOVE;
                    }
                }
            }

            @Override
            public void drop(DropTargetEvent event) {
                if (event.data == null) {
                    event.detail = DND.DROP_NONE;
                    return;
                }

                String draggedNodeId = (String) event.data;
                HierarchyNode draggedNode = hierarchy.getNode(draggedNodeId);

                if (draggedNode == null) {
                    showError("Drag-Drop Error", "Source node not found");
                    return;
                }

                String newParentId = null;

                if (event.item != null) {
                    TreeItem targetItem = (TreeItem) event.item;
                    String targetNodeId = treeItemToNodeId.get(targetItem);
                    HierarchyNode targetNode = hierarchy.getNode(targetNodeId);

                    if (targetNode == null) {
                        showError("Drag-Drop Error", "Target node not found");
                        return;
                    }

                    // Only allow dropping into folders
                    if (!targetNode.isFolder()) {
                        showError("Invalid Drop Target", "Can only drop into folders");
                        return;
                    }

                    newParentId = targetNodeId;
                } else {
                    // Drop on empty area - move to root
                    newParentId = null;
                }

                // Perform the move
                boolean success = hierarchy.moveNode(draggedNodeId, newParentId);

                if (success) {
                    configManager.saveHierarchy(hierarchy);
                    refresh();

                    // Reselect the moved node
                    TreeItem item = nodeIdToTreeItem.get(draggedNodeId);
                    if (item != null) {
                        tree.setSelection(item);
                        tree.showItem(item);
                    }

                    log.info("Moved node {} to parent {}", draggedNode.getName(),
                               newParentId != null ? hierarchy.getNode(newParentId).getName() : "root");
                } else {
                    showError("Move Failed",
                             "Cannot move node: this would create a circular dependency");
                }
            }
        });

        log.debug("Drag-and-drop configured");
    }

    private void showError(String title, String message) {
        MessageBox box = new MessageBox(getShell(), SWT.ICON_ERROR | SWT.OK);
        box.setText(title);
        box.setMessage(message);
        box.open();
    }

    private void setupKeyboardShortcuts() {
        tree.addKeyListener(new org.eclipse.swt.events.KeyAdapter() {
            @Override
            public void keyPressed(org.eclipse.swt.events.KeyEvent e) {
                HierarchyNode selected = getSelectedNode();

                // F2 - Rename
                if (e.keyCode == SWT.F2) {
                    renameSelected();
                }
                // Delete - Delete node
                else if (e.keyCode == SWT.DEL) {
                    deleteSelected();
                }
                // Ctrl+N - New folder
                else if (e.stateMask == SWT.CTRL && e.keyCode == 'n') {
                    addFolder();
                }
                // Ctrl+Shift+N - New queue manager
                else if (e.stateMask == (SWT.CTRL | SWT.SHIFT) && e.keyCode == 'n') {
                    addQueueManager();
                }
                // Enter - Connect to queue manager
                else if (e.keyCode == SWT.CR && selected != null && selected.isQueueManager()) {
                    String connectionId = selected.getConnectionConfigId();
                    if (!connectionManager.isConnected(connectionId)) {
                        connectSelected();
                    }
                }
                // Ctrl+D - Disconnect
                else if (e.stateMask == SWT.CTRL && e.keyCode == 'd' &&
                        selected != null && selected.isQueueManager()) {
                    disconnectSelected();
                }
            }
        });

        log.debug("Keyboard shortcuts configured");
    }

    /**
     * Set the hierarchy and render the tree.
     */
    public void setHierarchy(HierarchyConfig hierarchy) {
        this.hierarchy = hierarchy;
        refresh();
    }

    /**
     * Get the current hierarchy.
     */
    public HierarchyConfig getHierarchy() {
        // Update expansion states before returning
        if (hierarchy != null) {
            updateExpansionStates();
        }
        return hierarchy;
    }

    /**
     * Refresh the tree from the hierarchy model.
     */
    public void refresh() {
        if (hierarchy == null) {
            log.warn("Cannot refresh tree: hierarchy is null");
            return;
        }

        // Clear existing tree
        tree.removeAll();
        treeItemToNodeId.clear();
        nodeIdToTreeItem.clear();

        // Build tree from root nodes
        List<HierarchyNode> rootNodes = hierarchy.getChildren(null);
        for (HierarchyNode node : rootNodes) {
            createTreeItem(null, node);
        }

        // Restore selection if available
        if (hierarchy.getSelectedNodeId() != null) {
            TreeItem item = nodeIdToTreeItem.get(hierarchy.getSelectedNodeId());
            if (item != null) {
                tree.setSelection(item);
                tree.showItem(item);
            }
        }

        log.debug("Tree refreshed with {} root nodes", rootNodes.size());
    }

    /**
     * Recursively create tree items from hierarchy nodes.
     */
    private TreeItem createTreeItem(TreeItem parent, HierarchyNode node) {
        TreeItem item;
        if (parent == null) {
            item = new TreeItem(tree, SWT.NONE);
        } else {
            item = new TreeItem(parent, SWT.NONE);
        }

        item.setText(node.getName());
        item.setImage(getNodeIcon(node));

        // Store mappings
        treeItemToNodeId.put(item, node.getId());
        nodeIdToTreeItem.put(node.getId(), item);

        // Set expansion state
        item.setExpanded(node.isExpanded());

        // Recursively add children
        List<HierarchyNode> children = hierarchy.getChildren(node.getId());
        for (HierarchyNode child : children) {
            createTreeItem(item, child);
        }

        return item;
    }

    /**
     * Get the appropriate icon for a node based on its type and connection status.
     */
    private Image getNodeIcon(HierarchyNode node) {
        Display display = getDisplay();

        if (node.isFolder()) {
            // Use folder icon
            return display.getSystemImage(SWT.ICON_INFORMATION);  // Placeholder
        } else {
            // Queue manager - check connection status
            String connectionId = node.getConnectionConfigId();
            if (connectionId != null && connectionManager.isConnected(connectionId)) {
                // Connected - use green/active icon
                return display.getSystemImage(SWT.ICON_INFORMATION);  // Placeholder
            } else {
                // Disconnected - use gray/inactive icon
                return display.getSystemImage(SWT.ICON_WARNING);  // Placeholder
            }
        }
    }

    /**
     * Handle tree item selection.
     */
    private void onTreeItemSelected(TreeItem item) {
        String nodeId = treeItemToNodeId.get(item);
        if (nodeId == null) {
            fireSelectionEvent(null, SelectionType.NONE);
            return;
        }

        HierarchyNode node = hierarchy.getNode(nodeId);
        if (node == null) {
            fireSelectionEvent(null, SelectionType.NONE);
            return;
        }

        // Update hierarchy selection
        hierarchy.setSelectedNodeId(nodeId);

        // Fire event based on node type
        if (node.isFolder()) {
            fireSelectionEvent(node, SelectionType.FOLDER);
        } else {
            fireSelectionEvent(node, SelectionType.QUEUE_MANAGER);
        }
    }

    /**
     * Update expansion states in the hierarchy model from the tree.
     */
    private void updateExpansionStates() {
        for (Map.Entry<TreeItem, String> entry : treeItemToNodeId.entrySet()) {
            TreeItem item = entry.getKey();
            String nodeId = entry.getValue();
            HierarchyNode node = hierarchy.getNode(nodeId);
            if (node != null) {
                node.setExpanded(item.getExpanded());
            }
        }
    }

    /**
     * Get the currently selected node.
     */
    public HierarchyNode getSelectedNode() {
        TreeItem[] selection = tree.getSelection();
        if (selection.length == 0) {
            return null;
        }

        String nodeId = treeItemToNodeId.get(selection[0]);
        return nodeId != null ? hierarchy.getNode(nodeId) : null;
    }

    /**
     * Add a selection listener.
     */
    public void addSelectionListener(Consumer<SelectionEvent> listener) {
        this.selectionListener = listener;
    }

    /**
     * Fire selection event to listener.
     */
    private void fireSelectionEvent(HierarchyNode node, SelectionType type) {
        if (selectionListener != null) {
            selectionListener.accept(new SelectionEvent(node, type));
        }
    }

    /**
     * Update node icon (e.g., when connection status changes).
     */
    public void updateNodeIcon(String nodeId) {
        TreeItem item = nodeIdToTreeItem.get(nodeId);
        if (item != null && !item.isDisposed()) {
            HierarchyNode node = hierarchy.getNode(nodeId);
            if (node != null) {
                item.setImage(getNodeIcon(node));
            }
        }
    }

    /**
     * Update all queue manager node icons based on current connection status.
     */
    public void updateAllConnectionIcons() {
        for (HierarchyNode node : hierarchy.getAllQueueManagers()) {
            updateNodeIcon(node.getId());
        }
    }

    // Tree operation methods

    /**
     * Add a new folder to the hierarchy.
     */
    public void addFolder() {
        HierarchyNode selectedNode = getSelectedNode();
        String parentId = null;

        // If a folder is selected, add as child; otherwise add to root
        if (selectedNode != null && selectedNode.isFolder()) {
            parentId = selectedNode.getId();
        }

        FolderDialog dialog = new FolderDialog(getShell());
        String folderName = dialog.open();

        if (folderName != null) {
            HierarchyNode newFolder = new HierarchyNode(HierarchyNode.NodeType.FOLDER, folderName);
            hierarchy.addNode(newFolder, parentId);

            // Save and refresh
            configManager.saveHierarchy(hierarchy);
            refresh();

            // Select the new folder
            TreeItem item = nodeIdToTreeItem.get(newFolder.getId());
            if (item != null) {
                tree.setSelection(item);
                tree.showItem(item);
            }

            log.info("Added folder: {}", folderName);
        }
    }

    /**
     * Add a new queue manager to the hierarchy.
     */
    public void addQueueManager() {
        HierarchyNode selectedNode = getSelectedNode();
        String parentId = null;

        // If a folder is selected, add as child; otherwise add to root
        if (selectedNode != null && selectedNode.isFolder()) {
            parentId = selectedNode.getId();
        }

        QueueManagerSelectionDialog dialog = new QueueManagerSelectionDialog(
            getShell(), configManager, hierarchy);
        ConnectionConfig config = dialog.open();

        if (config != null) {
            String displayName = config.getName() != null && !config.getName().isEmpty()
                ? config.getName()
                : config.getQueueManager() + "@" + config.getHost();

            HierarchyNode newNode = new HierarchyNode(
                HierarchyNode.NodeType.QUEUE_MANAGER,
                displayName,
                config.getName()
            );

            hierarchy.addNode(newNode, parentId);

            // Save and refresh
            configManager.saveHierarchy(hierarchy);
            refresh();

            // Select the new queue manager
            TreeItem item = nodeIdToTreeItem.get(newNode.getId());
            if (item != null) {
                tree.setSelection(item);
                tree.showItem(item);
            }

            log.info("Added queue manager: {}", displayName);
        }
    }

    /**
     * Rename the selected node.
     */
    public void renameSelected() {
        HierarchyNode node = getSelectedNode();
        if (node == null) {
            return;
        }

        if (node.isFolder()) {
            // Rename folder
            FolderDialog dialog = new FolderDialog(getShell(), true, node.getName());
            String newName = dialog.open();

            if (newName != null && !newName.equals(node.getName())) {
                node.setName(newName);
                configManager.saveHierarchy(hierarchy);

                // Update tree item
                TreeItem item = nodeIdToTreeItem.get(node.getId());
                if (item != null) {
                    item.setText(newName);
                }

                log.info("Renamed folder to: {}", newName);
            }
        } else {
            // For queue managers, show a message that they should edit the connection
            MessageBox box = new MessageBox(getShell(), SWT.ICON_INFORMATION | SWT.OK);
            box.setText("Rename Queue Manager");
            box.setMessage("To rename a queue manager, please edit its connection configuration.\n\n" +
                          "You can do this from the Connection menu or by right-clicking and selecting 'Edit Connection'.");
            box.open();
        }
    }

    /**
     * Delete the selected node.
     */
    public void deleteSelected() {
        HierarchyNode node = getSelectedNode();
        if (node == null) {
            return;
        }

        // Confirm deletion
        MessageBox confirmBox = new MessageBox(getShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO);
        confirmBox.setText("Confirm Delete");

        if (node.isFolder()) {
            int childCount = hierarchy.getChildren(node.getId()).size();
            if (childCount > 0) {
                confirmBox.setMessage(String.format(
                    "Delete folder '%s' and all its contents (%d items)?",
                    node.getName(), childCount));
            } else {
                confirmBox.setMessage(String.format("Delete folder '%s'?", node.getName()));
            }
        } else {
            confirmBox.setMessage(String.format(
                "Remove queue manager '%s' from hierarchy?\n\n" +
                "Note: This will not delete the connection configuration.",
                node.getName()));
        }

        if (confirmBox.open() == SWT.YES) {
            // Disconnect if connected
            if (node.isQueueManager() && node.getConnectionConfigId() != null) {
                if (connectionManager.isConnected(node.getConnectionConfigId())) {
                    connectionManager.disconnect(node.getConnectionConfigId());
                }
            }

            // Remove from hierarchy
            hierarchy.removeNode(node.getId());
            configManager.saveHierarchy(hierarchy);
            refresh();

            log.info("Deleted node: {}", node.getName());
        }
    }

    /**
     * Connect to the selected queue manager.
     */
    public void connectSelected() {
        HierarchyNode node = getSelectedNode();
        if (node == null || !node.isQueueManager()) {
            return;
        }

        String connectionId = node.getConnectionConfigId();
        if (connectionManager.isConnected(connectionId)) {
            MessageBox box = new MessageBox(getShell(), SWT.ICON_INFORMATION | SWT.OK);
            box.setText("Already Connected");
            box.setMessage("Already connected to " + node.getName());
            box.open();
            return;
        }

        // Find connection config
        com.aquila.ibm.mq.gui.model.ConnectionConfig config = configManager.loadConnections().stream()
            .filter(c -> connectionId.equals(c.getName()))
            .findFirst()
            .orElse(null);

        if (config == null) {
            MessageBox box = new MessageBox(getShell(), SWT.ICON_ERROR | SWT.OK);
            box.setText("Configuration Not Found");
            box.setMessage("Connection configuration not found for: " + connectionId);
            box.open();
            return;
        }

        try {
            connectionManager.connect(connectionId, config);
            updateNodeIcon(node.getId());

            MessageBox box = new MessageBox(getShell(), SWT.ICON_INFORMATION | SWT.OK);
            box.setText("Connected");
            box.setMessage("Successfully connected to " + node.getName());
            box.open();

            log.info("Connected to: {}", node.getName());
        } catch (Exception e) {
            MessageBox box = new MessageBox(getShell(), SWT.ICON_ERROR | SWT.OK);
            box.setText("Connection Failed");
            box.setMessage("Failed to connect: " + e.getMessage());
            box.open();

            log.error("Failed to connect to: {}", node.getName(), e);
        }
    }

    /**
     * Disconnect from the selected queue manager.
     */
    public void disconnectSelected() {
        HierarchyNode node = getSelectedNode();
        if (node == null || !node.isQueueManager()) {
            return;
        }

        String connectionId = node.getConnectionConfigId();
        if (!connectionManager.isConnected(connectionId)) {
            MessageBox box = new MessageBox(getShell(), SWT.ICON_INFORMATION | SWT.OK);
            box.setText("Not Connected");
            box.setMessage("Not connected to " + node.getName());
            box.open();
            return;
        }

        connectionManager.disconnect(connectionId);
        updateNodeIcon(node.getId());

        log.info("Disconnected from: {}", node.getName());
    }

    @Override
    public void dispose() {
        if (tree != null && !tree.isDisposed()) {
            tree.dispose();
        }
        super.dispose();
    }
}
