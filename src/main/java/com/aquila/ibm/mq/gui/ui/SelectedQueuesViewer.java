package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.model.QueueInfo;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectedQueuesViewer extends Composite {
    private final Table table;
    private final List<QueueInfo> queues;
    private final Map<TableItem, TableEditor> editors;

    // Sorting state
    private int sortColumn = 1; // Default sort by Queue Name
    private boolean sortAscending = true;

    public SelectedQueuesViewer(Composite parent, int style) {
        super(parent, style);
        this.queues = new ArrayList<>();
        this.editors = new HashMap<>();

        setLayout(new GridLayout());

        table = new Table(this, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        table.setHeaderVisible(true);
        table.setLinesVisible(true);

        // Label column (editable)
        TableColumn labelColumn = new TableColumn(table, SWT.LEFT);
        labelColumn.setText("Label");
        labelColumn.setWidth(150);
        labelColumn.addListener(SWT.Selection, e -> sortBy(0));

        // Queue Name column
        TableColumn nameColumn = new TableColumn(table, SWT.LEFT);
        nameColumn.setText("Queue Name");
        nameColumn.setWidth(250);
        nameColumn.addListener(SWT.Selection, e -> sortBy(1));

        // Depth column
        TableColumn depthColumn = new TableColumn(table, SWT.RIGHT);
        depthColumn.setText("Depth");
        depthColumn.setWidth(80);
        depthColumn.addListener(SWT.Selection, e -> sortBy(2));

        // Max Depth column
        TableColumn maxDepthColumn = new TableColumn(table, SWT.RIGHT);
        maxDepthColumn.setText("Max Depth");
        maxDepthColumn.setWidth(80);
        maxDepthColumn.addListener(SWT.Selection, e -> sortBy(3));

        // Percent column
        TableColumn percentColumn = new TableColumn(table, SWT.RIGHT);
        percentColumn.setText("% Full");
        percentColumn.setWidth(70);
        percentColumn.addListener(SWT.Selection, e -> sortBy(4));

        addDisposeListener(e -> {
            // Dispose all editors
            editors.values().forEach(TableEditor::dispose);
            editors.clear();
        });
    }

    public void setQueues(List<QueueInfo> queues) {
        this.queues.clear();
        this.queues.addAll(queues);
        sortQueues();
        refresh();
    }

    public void refresh() {
        // Dispose existing editors
        editors.values().forEach(TableEditor::dispose);
        editors.clear();

        table.removeAll();

        for (QueueInfo queue : queues) {
            TableItem item = new TableItem(table, SWT.NONE);
            updateTableItem(item, queue);
            createLabelEditor(item, queue);
        }
    }

    private void updateTableItem(TableItem item, QueueInfo queue) {
        // Set label (or empty if null)
        item.setText(0, queue.getLabel() != null ? queue.getLabel() : "");
        item.setText(1, queue.getQueue());
        item.setText(2, String.valueOf(queue.getCurrentDepth()));
        item.setText(3, String.valueOf(queue.getMaxDepth()));
        item.setText(4, String.format("%.1f%%", queue.getDepthPercentage()));
    }

    private void createLabelEditor(TableItem item, QueueInfo queue) {
        TableEditor editor = new TableEditor(table);
        editor.horizontalAlignment = SWT.LEFT;
        editor.grabHorizontal = true;
        editor.minimumWidth = 50;

        Text text = new Text(table, SWT.NONE);
        text.setText(queue.getLabel() != null ? queue.getLabel() : "");

        text.addListener(SWT.FocusOut, e -> {
            String newLabel = text.getText();
            queue.setLabel(newLabel);
            if(!item.isDisposed()) item.setText(0, newLabel);
        });

        text.addListener(SWT.Traverse, e -> {
            if (e.detail == SWT.TRAVERSE_RETURN || e.detail == SWT.TRAVERSE_TAB_NEXT) {
                String newLabel = text.getText();
                queue.setLabel(newLabel);
                item.setText(0, newLabel);
                e.doit = false;
            }
        });

        editor.setEditor(text, item, 0);
        editors.put(item, editor);
    }

    public void clearQueues() {
        queues.clear();
        editors.values().forEach(TableEditor::dispose);
        editors.clear();
        table.removeAll();
    }

    public List<QueueInfo> getQueues() {
        return new ArrayList<>(queues);
    }

    public List<QueueInfo> getSelectedQueues() {
        List<QueueInfo> selectedQueues = new ArrayList<>();
        int[] selectionIndices = table.getSelectionIndices();

        for (int index : selectionIndices) {
            if (index >= 0 && index < queues.size()) {
                selectedQueues.add(queues.get(index));
            }
        }

        return selectedQueues;
    }

    public void addListener(int eventType, Listener listener) {
        table.addListener(eventType, listener);
    }

    public void setLayoutData(Object layoutData) {
        super.setLayoutData(layoutData);
    }

    private void sortBy(int columnIndex) {
        if (sortColumn == columnIndex) {
            // Toggle sort direction
            sortAscending = !sortAscending;
        } else {
            // New column, default to ascending
            sortColumn = columnIndex;
            sortAscending = true;
        }

        // Update sort indicator
        table.setSortColumn(table.getColumn(columnIndex));
        table.setSortDirection(sortAscending ? SWT.UP : SWT.DOWN);

        // Apply sorting
        sortQueues();
        refresh();
    }

    private void sortQueues() {
        Comparator<QueueInfo> comparator = switch (sortColumn) {
            case 0 -> Comparator.comparing(q -> q.getLabel() != null ? q.getLabel() : "", String.CASE_INSENSITIVE_ORDER);
            case 1 -> Comparator.comparing(QueueInfo::getQueue);
            case 2 -> Comparator.comparingInt(QueueInfo::getCurrentDepth);
            case 3 -> Comparator.comparingInt(QueueInfo::getMaxDepth);
            case 4 -> Comparator.comparingDouble(QueueInfo::getDepthPercentage);
            default -> Comparator.comparing(QueueInfo::getQueue);
        };

        if (!sortAscending) {
            comparator = comparator.reversed();
        }

        queues.sort(comparator);
    }
}
