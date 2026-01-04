package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.config.ConfigManager;
import com.aquila.ibm.mq.gui.model.HierarchyConfig;
import com.aquila.ibm.mq.gui.model.HierarchyNode;
import com.aquila.ibm.mq.gui.model.QueueBrowserConfig;
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
public class HierarchyTreeViewer extends Composite {
    private static final Logger log = LoggerFactory.getLogger(HierarchyTreeViewer.class);

    private Tree tree;
    private HierarchyConfig hierarchyConfig;
    private final MQConnectionManager connectionManager;
    private final ConfigManager configManager;

    // Map TreeItem to HierarchyNode ID for quick lookup
    private final Map<TreeItem, String> treeItemToNodeId;
    private final Map<String, TreeItem> nodeIdToTreeItem;

    // Selection listener
    private Consumer<SelectionEvent> selectionListener;

    // Custom icons
    private Image folderIcon;
    private Image connectedIcon;
    private Image errorIcon;

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
        QUEUE_BROWSER,
        NONE
    }

    public HierarchyTreeViewer(Composite parent, int style,
                               MQConnectionManager connectionManager,
                               ConfigManager configManager) {
        super(parent, style);
        this.connectionManager = connectionManager;
        this.configManager = configManager;
        this.treeItemToNodeId = new HashMap<>();
        this.nodeIdToTreeItem = new HashMap<>();

        loadIcons();
        setLayout(new GridLayout());
        createToolbar();
        createTree();
        createContextMenu();
        setupDragAndDrop();
        setupKeyboardShortcuts();
    }

    /**
     * Load custom icons from resources.
     */
    private void loadIcons() {
        Display display = getDisplay();

        try {
            // Load folder icon
            folderIcon = new Image(display,
                    getClass().getResourceAsStream("/icons/folder.png"));
            log.debug("Loaded folder icon");
        } catch (Exception e) {
            log.warn("Failed to load folder icon, using system default", e);
            folderIcon = display.getSystemImage(SWT.ICON_QUESTION);
        }

        try {
            // Load connected icon
            connectedIcon = new Image(display,
                    getClass().getResourceAsStream("/icons/connected.png"));
            log.debug("Loaded connected icon");
        } catch (Exception e) {
            log.warn("Failed to load connected icon, using system default", e);
            connectedIcon = display.getSystemImage(SWT.ICON_WORKING);
        }

        try {
            // Load error icon
            errorIcon = new Image(display,
                    getClass().getResourceAsStream("/icons/error.png"));
            log.debug("Loaded error icon");
        } catch (Exception e) {
            log.warn("Failed to load error icon, using system default", e);
            errorIcon = display.getSystemImage(SWT.ICON_ERROR);
        }
    }

    private void createToolbar() {
        ToolBar toolbar = new ToolBar(this, SWT.FLAT | SWT.HORIZONTAL);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // Add Folder button
        ToolItem addFolderItem = new ToolItem(toolbar, SWT.PUSH);
        addFolderItem.setText("New Folder");
        addFolderItem.setToolTipText("Create a new folder");
        addFolderItem.addListener(SWT.Selection, e -> addFolder());

        // Separator
        new ToolItem(toolbar, SWT.SEPARATOR);

        // Add Queue Browser button
        ToolItem addQMItem = new ToolItem(toolbar, SWT.PUSH);
        addQMItem.setText("Add Queue Browser");
        addQMItem.setToolTipText("Add a queue browser to the hierarchy");
        addQMItem.addListener(SWT.Selection, e -> addQueueBrowser());

        // Separator
        new ToolItem(toolbar, SWT.SEPARATOR);

        // Edit button
        ToolItem editItem = new ToolItem(toolbar, SWT.PUSH);
        editItem.setText("Rename");
        editItem.setToolTipText("Rename selected item");
        editItem.addListener(SWT.Selection, e -> renameSelected());

        // Separator
        new ToolItem(toolbar, SWT.SEPARATOR);

        // Delete button
        ToolItem deleteItem = new ToolItem(toolbar, SWT.PUSH);
        deleteItem.setText("Delete");
        deleteItem.setToolTipText("Delete selected item");
        deleteItem.addListener(SWT.Selection, e -> deleteSelected());

        // Separator
        new ToolItem(toolbar, SWT.SEPARATOR);

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
            log.info("TREE selected: {}", event);
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
                createQueueBrowserContextMenu(menu, selected);
            }
        });

        log.debug("Context menu created");
    }

    private void createRootContextMenu(Menu menu) {
        MenuItem addFolderItem = new MenuItem(menu, SWT.PUSH);
        addFolderItem.setText("Add Folder...");
        addFolderItem.addListener(SWT.Selection, e -> addFolder());
    }

    private void createFolderContextMenu(Menu menu, HierarchyNode folder) {
        MenuItem addFolderItem = new MenuItem(menu, SWT.PUSH);
        addFolderItem.setText("Add Folder...");
        addFolderItem.addListener(SWT.Selection, e -> addFolder());

        MenuItem addQMItem = new MenuItem(menu, SWT.PUSH);
        addQMItem.setText("Add Queue Browser...");
        addQMItem.addListener(SWT.Selection, e -> addQueueBrowser());

        new MenuItem(menu, SWT.SEPARATOR);

        MenuItem renameItem = new MenuItem(menu, SWT.PUSH);
        renameItem.setText("Rename...");
        renameItem.addListener(SWT.Selection, e -> renameSelected());

        MenuItem deleteItem = new MenuItem(menu, SWT.PUSH);
        deleteItem.setText("Delete");
        deleteItem.addListener(SWT.Selection, e -> deleteSelected());

    }

    private void createQueueBrowserContextMenu(Menu menu, HierarchyNode hierarchyNode) {
        new MenuItem(menu, SWT.SEPARATOR);

        MenuItem editConnItem = new MenuItem(menu, SWT.PUSH);
        editConnItem.setText("Edit Queue Browser");
        editConnItem.addListener(SWT.Selection, e -> editQueueBrowser(hierarchyNode));

        MenuItem removeItem = new MenuItem(menu, SWT.PUSH);
        removeItem.setText("Remove");
        removeItem.addListener(SWT.Selection, e -> deleteSelected());
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
                HierarchyNode draggedNode = hierarchyConfig.getNode(draggedNodeId);

                if (draggedNode == null) {
                    showError("Drag-Drop Error", "Source node not found");
                    return;
                }

                String newParentId = null;

                if (event.item != null) {
                    TreeItem targetItem = (TreeItem) event.item;
                    String targetNodeId = treeItemToNodeId.get(targetItem);
                    HierarchyNode targetNode = hierarchyConfig.getNode(targetNodeId);

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
                boolean success = hierarchyConfig.moveNode(draggedNodeId, newParentId);

                if (success) {
                    configManager.saveHierarchy(hierarchyConfig);
                    refresh();

                    // Reselect the moved node
                    TreeItem item = nodeIdToTreeItem.get(draggedNodeId);
                    if (item != null) {
                        tree.setSelection(item);
                        tree.showItem(item);
                    }

                    log.info("Moved node {} to parent {}", draggedNode.getName(),
                            newParentId != null ? hierarchyConfig.getNode(newParentId).getName() : "root");
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
                    addQueueBrowser();
                }
            }
        });

        log.debug("Keyboard shortcuts configured");
    }

    /**
     * Set the hierarchy and render the tree.
     */
    public void setHierarchyConfig(HierarchyConfig hierarchyConfig) {
        this.hierarchyConfig = hierarchyConfig;
        refresh();
    }

    /**
     * Get the current hierarchy.
     */
    public HierarchyConfig getHierarchyConfig() {
        // Update expansion states before returning
        if (hierarchyConfig != null) {
            updateExpansionStates();
        }
        return hierarchyConfig;
    }

    /**
     * Refresh the tree from the hierarchy model.
     */
    public void refresh() {
        if (hierarchyConfig == null) {
            log.warn("Cannot refresh tree: hierarchy is null");
            return;
        }

        // Clear existing tree
        tree.removeAll();
        treeItemToNodeId.clear();
        nodeIdToTreeItem.clear();

        // Build tree from root nodes
        List<HierarchyNode> rootNodes = hierarchyConfig.getChildren(null);
        for (HierarchyNode node : rootNodes) {
            createTreeItem(null, node);
        }

        // Restore selection if available
        if (hierarchyConfig.getSelectedNodeId() != null) {
            TreeItem item = nodeIdToTreeItem.get(hierarchyConfig.getSelectedNodeId());
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

        if (node.isQueueBrowser()) {
            final QueueBrowserConfig queueBrowserConfig = node.getQueueBrowserConfig();
            final String description = queueBrowserConfig == null || queueBrowserConfig.getQueueManager() == null ? "?" : queueBrowserConfig.getQueueManager();
            item.setText(String.format("%s %s", description, node.getName()));
        } else
            item.setText(node.getName());
        item.setImage(getNodeIcon(node));

        // Store mappings
        treeItemToNodeId.put(item, node.getId());
        nodeIdToTreeItem.put(node.getId(), item);

        // Set expansion state
        item.setExpanded(node.isExpanded());

        // Recursively add children
        List<HierarchyNode> children = hierarchyConfig.getChildren(node.getId());
        for (HierarchyNode child : children) {
            createTreeItem(item, child);
        }

        return item;
    }

    /**
     * Get the appropriate icon for a node based on its type and connection status.
     */
    private Image getNodeIcon(HierarchyNode node) {
        if (node.isFolder()) {
            // Use folder icon
            return folderIcon;
        } else {
            return connectedIcon;
            // return errorIcon;
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

        HierarchyNode node = hierarchyConfig.getNode(nodeId);
        if (node == null) {
            fireSelectionEvent(null, SelectionType.NONE);
            return;
        }

        // Update hierarchy selection
        hierarchyConfig.setSelectedNodeId(nodeId);

        // Fire event based on node type
        if (node.isFolder()) {
            fireSelectionEvent(node, SelectionType.FOLDER);
        } else {
            fireSelectionEvent(node, SelectionType.QUEUE_BROWSER);
        }
    }

    /**
     * Update expansion states in the hierarchy model from the tree.
     */
    private void updateExpansionStates() {
        for (Map.Entry<TreeItem, String> entry : treeItemToNodeId.entrySet()) {
            TreeItem item = entry.getKey();
            String nodeId = entry.getValue();
            HierarchyNode node = hierarchyConfig.getNode(nodeId);
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
        return nodeId != null ? hierarchyConfig.getNode(nodeId) : null;
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
            HierarchyNode node = hierarchyConfig.getNode(nodeId);
            if (node != null) {
                item.setImage(getNodeIcon(node));
            }
        }
    }

    /**
     * Update all queue manager node icons based on current connection status.
     */
    public void updateAllConnectionIcons() {
        for (HierarchyNode node : hierarchyConfig.getAllQueueManagers()) {
            updateNodeIcon(node.getId());
        }
    }

    // Tree operation methods

    /**
     * Add a new folder to the hierarchy.
     */
    public void addFolder() {
        final HierarchyNode selectedNode = getSelectedNode();
        final String parentId = selectedNode != null && selectedNode.isFolder() ? selectedNode.getId() : null;
        final FolderDialog dialog = new FolderDialog(getShell());
        final String folderName = dialog.open();

        if (folderName != null) {
            final HierarchyNode newFolder = new HierarchyNode(HierarchyNode.NodeType.FOLDER, folderName);
            hierarchyConfig.addNode(newFolder, parentId);
            // Save and refresh
            configManager.saveHierarchy(hierarchyConfig);
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
     * Add a new queue browser to the hierarchy.
     */
    public void addQueueBrowser() {
        final HierarchyNode selectedNode = getSelectedNode();
        final String parentId = selectedNode != null && selectedNode.isFolder() ?
                selectedNode.getId() :
                null;
        final QueueBrowserDialog queueBrowserDialog = new QueueBrowserDialog(
                getShell(), configManager, null, false);
        final QueueBrowserConfig queueBrowserConfig = queueBrowserDialog.open();
        log.info("addQueueBrowser: {}", queueBrowserConfig);
        if (queueBrowserConfig != null) {
            final String displayName = queueBrowserConfig.getLabel();
            final HierarchyNode newNode = new HierarchyNode(HierarchyNode.NodeType.BROWSER, displayName);
            newNode.setQueueBrowserConfig(queueBrowserConfig);
            hierarchyConfig.addNode(newNode, parentId);
            configManager.saveHierarchy(hierarchyConfig);
            this.configManager.save(newNode.getId(), queueBrowserConfig);
            refresh();
            TreeItem item = nodeIdToTreeItem.get(newNode.getId());
            if (item != null) {
                tree.setSelection(item);
                tree.showItem(item);
            }
            log.info("Added queue manager: {}", displayName);
        }
    }

    public void editQueueBrowser(HierarchyNode hierarchyNode) {
        log.info("editQueueBrowser: {}", hierarchyNode);
        final QueueBrowserDialog queueBrowserDialog = new QueueBrowserDialog(
                getShell(), configManager, hierarchyNode, true);
        final QueueBrowserConfig queueBrowserConfig = queueBrowserDialog.open();
        log.info("editQueueBrowser: {}", queueBrowserConfig);
        if (queueBrowserConfig == null) return;
        hierarchyNode.setQueueBrowserConfig(queueBrowserConfig);
        this.configManager.save(hierarchyNode.getId(), queueBrowserConfig);
        refresh();
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
                configManager.saveHierarchy(hierarchyConfig);

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
            int childCount = hierarchyConfig.getChildren(node.getId()).size();
            if (childCount > 0) {
                confirmBox.setMessage(String.format(
                        "Delete folder '%s' and all its contents (%d items)?",
                        node.getName(), childCount));
            } else {
                confirmBox.setMessage(String.format("Delete folder '%s'?", node.getName()));
            }
        } else {
            confirmBox.setMessage(String.format(
                    "Remove queue browser '%s' from hierarchy?\n\n" +
                            "Note: This will not delete the connection configuration.",
                    node.getName()));
        }

        if (confirmBox.open() == SWT.YES) {
            // Remove from hierarchy
            hierarchyConfig.removeNode(node.getId());
            configManager.saveHierarchy(hierarchyConfig);
            refresh();

            log.info("Deleted node: {}", node.getName());
        }
    }

    @Override
    public void dispose() {
        // Dispose custom icons
        if (folderIcon != null && !folderIcon.isDisposed()) {
            folderIcon.dispose();
        }
        if (connectedIcon != null && !connectedIcon.isDisposed()) {
            connectedIcon.dispose();
        }
        if (errorIcon != null && !errorIcon.isDisposed()) {
            errorIcon.dispose();
        }

        // Dispose tree
        if (tree != null && !tree.isDisposed()) {
            tree.dispose();
        }

        super.dispose();
    }
}
