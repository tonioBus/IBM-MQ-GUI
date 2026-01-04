package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.config.ConfigManager;
import com.aquila.ibm.mq.gui.model.QueueInfo;
import com.aquila.ibm.mq.gui.model.ThresholdConfig;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class ThresholdConfigDialog {
    private static final Logger logger = LoggerFactory.getLogger(ThresholdConfigDialog.class);
    private final Shell parent;
    private final ConfigManager configManager;
    private final List<QueueInfo> queues;
    private Shell shell;
    private Table table;
    private Map<String, ThresholdConfig> thresholds;

    public ThresholdConfigDialog(Shell parent, ConfigManager configManager, List<QueueInfo> queues) {
        this.parent = parent;
        this.configManager = configManager;
        this.queues = queues;
    }

    public void open() {
        shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        shell.setText("Configure Queue Thresholds");
        shell.setLayout(new GridLayout());
        shell.setSize(700, 500);

        thresholds = configManager.loadThresholds();

        createInstructions();
        createTable();
        createButtons();

        loadThresholds();

        shell.open();
        Display display = parent.getDisplay();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
    }

    private void createInstructions() {
        Label label = new Label(shell, SWT.WRAP);
        label.setText("Configure warning and critical thresholds for queue depths. Values can be absolute or percentage.");
        label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    private void createTable() {
        table = new Table(shell, SWT.BORDER | SWT.FULL_SELECTION);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        table.setHeaderVisible(true);
        table.setLinesVisible(true);

        TableColumn queueColumn = new TableColumn(table, SWT.LEFT);
        queueColumn.setText("Queue Name");
        queueColumn.setWidth(200);

        TableColumn enabledColumn = new TableColumn(table, SWT.CENTER);
        enabledColumn.setText("Enabled");
        enabledColumn.setWidth(70);

        TableColumn warningColumn = new TableColumn(table, SWT.RIGHT);
        warningColumn.setText("Warning");
        warningColumn.setWidth(100);

        TableColumn warnTypeColumn = new TableColumn(table, SWT.CENTER);
        warnTypeColumn.setText("Type");
        warnTypeColumn.setWidth(70);

        TableColumn criticalColumn = new TableColumn(table, SWT.RIGHT);
        criticalColumn.setText("Critical");
        criticalColumn.setWidth(100);

        TableColumn critTypeColumn = new TableColumn(table, SWT.CENTER);
        critTypeColumn.setText("Type");
        critTypeColumn.setWidth(70);

        TableEditor editor = new TableEditor(table);
        editor.horizontalAlignment = SWT.LEFT;
        editor.grabHorizontal = true;

        table.addListener(SWT.MouseDown, event -> {
            int index = table.getSelectionIndex();
            if (index < 0) return;

            TableItem item = table.getItem(index);
            for (int i = 0; i < table.getColumnCount(); i++) {
                if (item.getBounds(i).contains(event.x, event.y)) {
                    editCell(item, i);
                    break;
                }
            }
        });
    }

    private void editCell(TableItem item, int column) {
        String queueName = item.getText(0);
        ThresholdConfig configTmp = thresholds.get(queueName);
        if (configTmp == null) {
            configTmp = new ThresholdConfig();
            configTmp.setQueueName(queueName);
        }
        final ThresholdConfig config = configTmp;

        switch (column) {
            case 1:
                config.setEnabled(!config.isEnabled());
                updateTableItem(item, queueName, config);
                break;
            case 2:
                editNumericValue(item, column, value -> {
                    config.setWarningThreshold(value);
                    thresholds.put(queueName, config);
                });
                break;
            case 3:
                config.setWarningThresholdPercentage(!config.isWarningThresholdPercentage());
                updateTableItem(item, queueName, config);
                break;
            case 4:
                editNumericValue(item, column, value -> {
                    config.setCriticalThreshold(value);
                    thresholds.put(queueName, config);
                });
                break;
            case 5:
                config.setCriticalThresholdPercentage(!config.isCriticalThresholdPercentage());
                updateTableItem(item, queueName, config);
                break;
        }
    }

    private void editNumericValue(TableItem item, int column, java.util.function.Consumer<Integer> setter) {
        Text text = new Text(table, SWT.NONE);
        text.setText(item.getText(column));
        text.selectAll();
        text.setFocus();

        text.addListener(SWT.FocusOut, e -> {
            try {
                int value = Integer.parseInt(text.getText());
                setter.accept(value);
                item.setText(column, String.valueOf(value));
            } catch (NumberFormatException ex) {
                logger.warn("Invalid number format");
            }
            text.dispose();
        });

        text.addListener(SWT.Traverse, e -> {
            if (e.detail == SWT.TRAVERSE_RETURN) {
                try {
                    int value = Integer.parseInt(text.getText());
                    setter.accept(value);
                    item.setText(column, String.valueOf(value));
                } catch (NumberFormatException ex) {
                    logger.warn("Invalid number format");
                }
                text.dispose();
                e.doit = false;
            } else if (e.detail == SWT.TRAVERSE_ESCAPE) {
                text.dispose();
                e.doit = false;
            }
        });

        TableEditor editor = new TableEditor(table);
        editor.horizontalAlignment = SWT.LEFT;
        editor.grabHorizontal = true;
        editor.setEditor(text, item, column);
    }

    private void loadThresholds() {
        table.removeAll();

        for (QueueInfo queue : queues) {
            ThresholdConfig config = thresholds.get(queue.getQueue());
            if (config == null) {
                config = new ThresholdConfig();
                config.setQueueName(queue.getQueue());
            }

            TableItem item = new TableItem(table, SWT.NONE);
            updateTableItem(item, queue.getQueue(), config);
        }
    }

    private void updateTableItem(TableItem item, String queueName, ThresholdConfig config) {
        item.setText(0, queueName);
        item.setText(1, config.isEnabled() ? "Yes" : "No");
        item.setText(2, String.valueOf(config.getWarningThreshold()));
        item.setText(3, config.isWarningThresholdPercentage() ? "%" : "Abs");
        item.setText(4, String.valueOf(config.getCriticalThreshold()));
        item.setText(5, config.isCriticalThresholdPercentage() ? "%" : "Abs");
    }

    private void createButtons() {
        Composite buttonBar = new Composite(shell, SWT.NONE);
        buttonBar.setLayout(new GridLayout(3, false));
        buttonBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button setDefaultsButton = new Button(buttonBar, SWT.PUSH);
        setDefaultsButton.setText("Set Defaults");
        setDefaultsButton.addListener(SWT.Selection, e -> setDefaults());

        Button saveButton = new Button(buttonBar, SWT.PUSH);
        saveButton.setText("Save");
        saveButton.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
        saveButton.addListener(SWT.Selection, e -> save());

        Button cancelButton = new Button(buttonBar, SWT.PUSH);
        cancelButton.setText("Cancel");
        cancelButton.addListener(SWT.Selection, e -> shell.close());
    }

    private void setDefaults() {
        for (TableItem item : table.getItems()) {
            String queueName = item.getText(0);
            ThresholdConfig config = new ThresholdConfig();
            config.setQueueName(queueName);
            config.setWarningThreshold(70);
            config.setCriticalThreshold(90);
            config.setWarningThresholdPercentage(true);
            config.setCriticalThresholdPercentage(true);
            config.setEnabled(true);

            thresholds.put(queueName, config);
            updateTableItem(item, queueName, config);
        }
    }

    private void save() {
        configManager.saveThresholds(thresholds);
        showInfo("Thresholds saved successfully");
        shell.close();
    }

    private void showInfo(String message) {
        MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
        box.setText("Success");
        box.setMessage(message);
        box.open();
    }
}
