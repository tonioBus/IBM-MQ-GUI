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
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;

import java.io.IOException;
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
    private QueueListViewer4QueueBrowser queueListViewer4QueueBrowser;

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
        SashForm sashForm = new SashForm(shell, SWT.HORIZONTAL);
        sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        Composite leftComposite = new Composite(sashForm, SWT.NONE);
        leftComposite.setLayout(new GridLayout(1, true));
        Composite rightComposite = new Composite(sashForm, SWT.NONE);
        rightComposite.setLayout(new GridLayout(1, true));
        createLabelField(leftComposite);
        createQueueManagerSection(leftComposite);
        createRegularExpressionField(leftComposite);
        createQueueListSection(rightComposite);
        createButtons(rightComposite);
        createButtomButtons(shell);
        leftComposite.pack();
        // shell.pack();
        sashForm.setWeights(new int[]{40, 50});
        label.setSize(200, 30);
        regularExpression.setSize(200, 30);
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
        queueManagerGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        final AlertManager alertManager = new AlertManager(configManager);
        this.queueListViewer4QueueBrowser = new QueueListViewer4QueueBrowser(queueManagerGroup, SWT.READ_ONLY | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL, alertManager);
        queueListViewer4QueueBrowser.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        queueListViewer4QueueBrowser.setSize(500,500);
        queueListViewer4QueueBrowser.addListener(SWT.Selection, e -> {
            log.info("selected: {}", e);
        });
    }

    private void createLabelField(Composite parent) {
        final Group labelGroup = new Group(parent, SWT.NONE);
        labelGroup.setText("Label");
        labelGroup.setLayout(new GridLayout(2, false));
        labelGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        label = new Text(labelGroup, SWT.BORDER);
        label.setText(edit ? hierarchyNode.getQueueBrowserConfig().getLabel() : "DEFAULT");
    }

    private void createRegularExpressionField(Composite parent) {
        final Group regularexpressionGroup = new Group(parent, SWT.NONE);
        regularexpressionGroup.setText("Regular Expression");
        regularexpressionGroup.setLayout(new GridLayout(2, false));
        regularexpressionGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        regularExpression = new Text(regularexpressionGroup, SWT.BORDER);
        regularExpression.setText(edit ? hierarchyNode.getQueueBrowserConfig().getRegularExpression() : "*");
    }

    private void createButtons(Composite parent) {
        Composite buttonBar = new Composite(parent, SWT.NONE);
        buttonBar.setLayout(new GridLayout(4, false));
        buttonBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button clear = new Button(buttonBar, SWT.PUSH);
        clear.setText("Clear");
        clear.addListener(SWT.Selection, e -> clear());

        Button fill = new Button(buttonBar, SWT.PUSH);
        fill.setText("Fill");
        fill.addListener(SWT.Selection, e -> {
            fill(e);
        });

        Button save = new Button(buttonBar, SWT.PUSH);
        save.setText("Save");
        save.addListener(SWT.Selection, e -> {
            log.info("Save: {}", e);
        });

        Button reload = new Button(buttonBar, SWT.PUSH);
        reload.setText("Reload");
        reload.addListener(SWT.Selection, e -> {
            log.info("Reload: {}", e);
        });
    }

    private void fill(Event e) {
        int index = this.queueManagerList.getSelectionIndex();
        String selection = this.queueManagerList.getItem(index);
        log.info("Fill: {}", this.queueManagerList.getSelectionIndex());
        
        MQConnectionManager connectionManager = new MQConnectionManager();
        QueueManagerConfig queueManagerConfig = this.connections.get(selection.split(" ")[1]);
        try {
            connectionManager.connect(queueManagerConfig);
            QueueService queueService = new QueueService(connectionManager);
            java.util.List<QueueInfo> queues = queueService.getAllQueues();
            log.info("queues:\n{}", queues);
            queues.forEach(q -> {
                this.queueListViewer4QueueBrowser.setQueues(queues);
            });
        } catch (MQException | IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void createButtomButtons(Composite parent) {
        Composite buttonBar = new Composite(parent, SWT.NONE);
        buttonBar.setLayout(new GridLayout(4, false));
        buttonBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Button add = new Button(buttonBar, SWT.PUSH);
        add.setText(edit ? "Modify" : "Add");
        add.addListener(SWT.Selection, e -> {
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
        final QueueBrowserConfig queueBrowserConfig = QueueBrowserConfig.builder()
                .label(this.label.getText().trim())
                .regularExpression(this.regularExpression.getText().trim())
                .queueManager(queueManagerKey)
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
            if(edit && this.hierarchyNode.getQueueBrowserConfig().getQueueManager().equals(q.getLabel()))
                queueManagerCombo.select(index.get());
            index.incrementAndGet();
        });
    }

}
