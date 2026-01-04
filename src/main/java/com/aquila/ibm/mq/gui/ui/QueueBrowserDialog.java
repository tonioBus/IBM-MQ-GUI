package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.config.AlertManager;
import com.aquila.ibm.mq.gui.config.ConfigManager;
import com.aquila.ibm.mq.gui.model.HierarchyNode;
import com.aquila.ibm.mq.gui.model.QueueBrowserConfig;
import com.aquila.ibm.mq.gui.model.QueueInfo;
import com.aquila.ibm.mq.gui.model.QueueManagerConfig;
import com.aquila.ibm.mq.gui.mq.MQConnectionManager;
import com.aquila.ibm.mq.gui.mq.QueueService;
import com.ibm.mq.MQException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class QueueBrowserDialog {
    private final Shell parent;
    private final ConfigManager configManager;
    private final HierarchyNode hierarchyNode;
    private final boolean edit;
    private Shell shell;
    private QueueBrowserConfig result;
    private Text label;
    private Text regularExpression;
    private List queueManagerList;
    private Map<String, QueueManagerConfig> connections;
    private InspectedQueueViewer availableQueuesViewer;
    private SelectedQueuesViewer selectedQueuesViewer;

    public QueueBrowserDialog(Shell parent, ConfigManager configManager, HierarchyNode hierarchyNode, boolean edit) {
        this.parent = parent;
        this.configManager = configManager;
        this.hierarchyNode = hierarchyNode;
        this.edit = edit;
    }

    public QueueBrowserConfig open() {
        shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        shell.setText("Queue Browser");
        shell.setLayout(new GridLayout(1, true));
        shell.setSize(1600, 800);
        final SashForm sashForm = new SashForm(shell, SWT.HORIZONTAL);
        sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        sashForm.setVisible(true);
        final Composite leftComposite = new Composite(sashForm, SWT.NONE);
        leftComposite.setLayout(new GridLayout(1, true));
        final Composite rightComposite = new Composite(sashForm, SWT.NONE);
        rightComposite.setLayout(new GridLayout(1, true));
        createLabelField(leftComposite);
        createQueueManagerSection(leftComposite);
        createRegularExpressionField(leftComposite);
        createQueueListSection(rightComposite);
        createButtons(rightComposite);
        createButtomButtons(shell);
        sashForm.setWeights(new int[]{20, 80});
        shell.open();
        Display display = parent.getDisplay();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        return result;
    }

    private void createQueueManagerSection(Composite composite) {
        final Group queueManagerGroup = new Group(composite, SWT.NONE);
        queueManagerGroup.setText("Queue Managers");
        queueManagerGroup.setLayout(new GridLayout(3, false));
        queueManagerGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        this.queueManagerList = new List(queueManagerGroup, SWT.READ_ONLY | SWT.BORDER | SWT.H_SCROLL);
        queueManagerList.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        queueManagerList.addListener(SWT.Selection, e -> {
            log.info("selection: {}", e);
        });
        loadQueueManager(queueManagerList);
    }

    private void createQueueListSection(Composite composite) {
        Group queueManagerGroup = new Group(composite, SWT.NONE);
        queueManagerGroup.setText("Queues");
        queueManagerGroup.setLayout(new GridLayout(3, false));
        queueManagerGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        final AlertManager alertManager = new AlertManager(configManager);

        // Available queues viewer (left)
        Composite leftQueueComposite = new Composite(queueManagerGroup, SWT.NONE);
        leftQueueComposite.setLayout(new GridLayout(1, false));
        leftQueueComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Label availableLabel = new Label(leftQueueComposite, SWT.NONE);
        availableLabel.setText("Available Queues");
        availableLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        this.availableQueuesViewer = new InspectedQueueViewer(leftQueueComposite, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI, alertManager);
        availableQueuesViewer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        availableQueuesViewer.addListener(SWT.Selection, e -> {
            log.info("available queue selected: {}", e);
        });

        // Middle buttons
        createQueueTransferButtons(queueManagerGroup);

        // Selected queues viewer (right)
        Composite rightQueueComposite = new Composite(queueManagerGroup, SWT.NONE);
        rightQueueComposite.setLayout(new GridLayout(1, false));
        rightQueueComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Label selectedLabel = new Label(rightQueueComposite, SWT.NONE);
        selectedLabel.setText("Selected Queues");
        selectedLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        this.selectedQueuesViewer = new SelectedQueuesViewer(rightQueueComposite, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
        selectedQueuesViewer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        java.util.List<QueueInfo> queues = new ArrayList<>();
        this.hierarchyNode.getQueueBrowserConfig().getDescriptions().entrySet().forEach(entry -> {
            queues.add(QueueInfo.builder()
                    .queue(entry.getKey())
                    .label(entry.getValue().label())
                    .build());
        });
        selectedQueuesViewer.setQueues(queues);
    }

    private void createLabelField(Composite parent) {
        final Group labelGroup = new Group(parent, SWT.NONE);
        labelGroup.setText("Label");
        labelGroup.setLayout(new GridLayout(1, false));
        labelGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        label = new Text(labelGroup, SWT.BORDER);
        label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        label.setText(edit && hierarchyNode.getQueueBrowserConfig() != null ? hierarchyNode.getQueueBrowserConfig().getLabel() : "DEFAULT");
    }

    private void createRegularExpressionField(Composite parent) {
        final Group regularexpressionGroup = new Group(parent, SWT.NONE);
        regularexpressionGroup.setText("Regular Expression");
        regularexpressionGroup.setLayout(new GridLayout(1, false));
        regularexpressionGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        regularExpression = new Text(regularexpressionGroup, SWT.BORDER);
        regularExpression.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        regularExpression.setText(edit ? hierarchyNode.getQueueBrowserConfig().getRegularExpression() : "*");
    }

    private void createQueueTransferButtons(Composite parent) {
        Composite buttonBar = new Composite(parent, SWT.NONE);
        buttonBar.setLayout(new GridLayout(1, false));
        buttonBar.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

        Button addButton = new Button(buttonBar, SWT.PUSH);
        addButton.setText("Add >");
        addButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        addButton.addListener(SWT.Selection, e -> addSelectedQueues());

        Button removeButton = new Button(buttonBar, SWT.PUSH);
        removeButton.setText("< Remove");
        removeButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        removeButton.addListener(SWT.Selection, e -> removeSelectedQueues());

        Button clearButton = new Button(buttonBar, SWT.PUSH);
        clearButton.setText("Clear All");
        clearButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        clearButton.addListener(SWT.Selection, e -> clearSelectedQueues());
    }

    private void createButtons(Composite parent) {
        Composite buttonBar = new Composite(parent, SWT.NONE);
        buttonBar.setLayout(new GridLayout(4, false));
        buttonBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button fill = new Button(buttonBar, SWT.PUSH);
        fill.setText("Fill");
        fill.addListener(SWT.Selection, e -> {
            fill(e);
        });
    }

    private void fill(Event e) {
        int index = this.queueManagerList.getSelectionIndex();
        String selection = this.queueManagerList.getItem(index);
        log.info("Fill: {}", this.queueManagerList.getSelectionIndex());

        availableQueuesViewer.showProgress("Connecting to Queue Manager...");

        MQConnectionManager connectionManager = new MQConnectionManager();
        QueueManagerConfig queueManagerConfig = this.connections.get(selection.split(" ")[1]);
        try {
            connectionManager.connect(queueManagerConfig);
            availableQueuesViewer.updateProgress("Retrieving queues...");
            QueueService queueService = new QueueService(connectionManager);
            java.util.List<QueueInfo> queues = queueService.getAllQueues();
            log.info("queues:\n{}", queues);
            this.availableQueuesViewer.setQueues(queues);
            availableQueuesViewer.hideProgress();
            connectionManager.disconnect();
        } catch (MQException | IOException ex) {
            availableQueuesViewer.hideProgress();
        }
    }

    private void addSelectedQueues() {
        java.util.List<QueueInfo> selectedQueues = availableQueuesViewer.getSelectedQueues();
        if (selectedQueues.isEmpty()) {
            log.info("No queues selected to add");
            return;
        }
        log.info("Adding {} queues to selected list", selectedQueues.size());
        // Get current selected queues
        java.util.List<QueueInfo> currentSelected = new java.util.ArrayList<>(selectedQueuesViewer.getQueues());
        // Add new queues (avoid duplicates)
        for (QueueInfo queue : selectedQueues) {
            if (!currentSelected.stream().anyMatch(q -> q.getQueue().equals(queue.getQueue()))) {
                currentSelected.add(queue);
            }
        }
        // Update selected queues viewer
        selectedQueuesViewer.setQueues(currentSelected);
    }

    private void removeSelectedQueues() {
        java.util.List<QueueInfo> queuesToRemove = selectedQueuesViewer.getSelectedQueues();
        if (queuesToRemove.isEmpty()) {
            log.info("No queues selected to remove");
            return;
        }

        log.info("Removing {} queues from selected list", queuesToRemove.size());

        // Get current selected queues
        java.util.List<QueueInfo> currentSelected = new java.util.ArrayList<>(selectedQueuesViewer.getQueues());

        // Remove selected queues
        currentSelected.removeIf(queue ->
                queuesToRemove.stream().anyMatch(q -> q.getQueue().equals(queue.getQueue()))
        );

        // Update selected queues viewer
        selectedQueuesViewer.setQueues(currentSelected);
    }

    private void clearSelectedQueues() {
        log.info("Clearing all selected queues");
        selectedQueuesViewer.clearQueues();
    }

    private void createButtomButtons(Composite parent) {
        Composite buttonBar = new Composite(parent, SWT.NONE);
        buttonBar.setLayout(new GridLayout(4, false));
        buttonBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Button addOrModify = new Button(buttonBar, SWT.PUSH);
        addOrModify.setText(edit ? "Modify" : "Add");
        addOrModify.addListener(SWT.Selection, e -> {
            result = getQueueBrowserConfig();
            shell.close();
        });
        Button cancel = new Button(buttonBar, SWT.PUSH);
        cancel.setText("Cancel");
        cancel.addListener(SWT.Selection, e -> {
            result = null;
            shell.close();
        });
    }

    private QueueBrowserConfig getQueueBrowserConfig() {
        final int selectionIndex = this.queueManagerList.getSelectionIndex();
        final String queueManagerKey = this.queueManagerList.getItem(selectionIndex).split(" ")[1];

        final Map<String, QueueBrowserConfig.QueueDescription> descriptions = new HashMap<>();
        this.selectedQueuesViewer.getQueues().forEach(queueInfo -> {
            String label = queueInfo.getLabel() == null ? queueInfo.getQueue() : queueInfo.getLabel();
            descriptions.put(queueInfo.getQueue(), new QueueBrowserConfig.QueueDescription(label));
        });
        final QueueBrowserConfig queueBrowserConfig = QueueBrowserConfig.builder()
                .label(this.label.getText().trim())
                .regularExpression(this.regularExpression.getText().trim())
                .queueManager(queueManagerKey)
                .descriptions(descriptions)
                .build();
        log.info("getQueueBrowserConfig return:\n{}", queueBrowserConfig);
        return queueBrowserConfig;
    }

    private void clear() {
        log.info("clear");
    }

    private void loadQueueManager(List queueManagerCombo) {
        this.connections = configManager.loadConnections();
        queueManagerCombo.removeAll();
        final AtomicInteger index = new AtomicInteger(0);
        connections.values().forEach(q -> {
            final String label = String.format("%s %s", q.getName(), q.getLabel());
            queueManagerCombo.add(label);
            if (edit && this.hierarchyNode.getQueueBrowserConfig().getQueueManager().equals(q.getLabel()))
                queueManagerCombo.select(index.get());
            index.incrementAndGet();
        });
    }

}
