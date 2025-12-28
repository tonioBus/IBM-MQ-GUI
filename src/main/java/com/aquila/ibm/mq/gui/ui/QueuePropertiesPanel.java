package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.model.QueueInfo;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.util.Map;

public class QueuePropertiesPanel extends Composite {
    private Table propertiesTable;
    private QueueInfo currentQueue;

    public QueuePropertiesPanel(Composite parent, int style) {
        super(parent, style);
        setLayout(new GridLayout());

        Label label = new Label(this, SWT.NONE);
        label.setText("Queue Properties:");

        propertiesTable = new Table(this, SWT.BORDER | SWT.FULL_SELECTION);
        propertiesTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        propertiesTable.setHeaderVisible(true);
        propertiesTable.setLinesVisible(true);

        TableColumn propertyColumn = new TableColumn(propertiesTable, SWT.LEFT);
        propertyColumn.setText("Property");
        propertyColumn.setWidth(250);

        TableColumn valueColumn = new TableColumn(propertiesTable, SWT.LEFT);
        valueColumn.setText("Value");
        valueColumn.setWidth(350);
    }

    public void setQueue(QueueInfo queue) {
        this.currentQueue = queue;
        refresh();
    }

    public void refresh() {
        propertiesTable.removeAll();

        if (currentQueue == null) {
            return;
        }

        addProperty("Queue Name", currentQueue.getName());
        addProperty("Current Depth", String.valueOf(currentQueue.getCurrentDepth()));
        addProperty("Max Depth", String.valueOf(currentQueue.getMaxDepth()));
        addProperty("Depth Percentage", String.format("%.2f%%", currentQueue.getDepthPercentage()));
        addProperty("Open Input Count", String.valueOf(currentQueue.getOpenInputCount()));
        addProperty("Open Output Count", String.valueOf(currentQueue.getOpenOutputCount()));
        addProperty("Queue Type", getQueueTypeName(currentQueue.getQueueType()));
        addProperty("Description", currentQueue.getDescription());

        addSeparator();

        for (Map.Entry<String, Object> entry : currentQueue.getAttributes().entrySet()) {
            addProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
    }

    private void addProperty(String name, String value) {
        TableItem item = new TableItem(propertiesTable, SWT.NONE);
        item.setText(0, name);
        item.setText(1, value != null ? value : "");
    }

    private void addSeparator() {
        TableItem item = new TableItem(propertiesTable, SWT.NONE);
        item.setText(0, "--- Additional Attributes ---");
        item.setText(1, "");
    }

    private String getQueueTypeName(int queueType) {
        switch (queueType) {
            case 1: return "Local Queue";
            case 2: return "Model Queue";
            case 3: return "Alias Queue";
            case 6: return "Remote Queue";
            case 7: return "Cluster Queue";
            default: return "Unknown (" + queueType + ")";
        }
    }
}
