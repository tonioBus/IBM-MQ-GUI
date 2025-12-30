package com.aquila.ibm.mq.gui.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

/**
 * Dialog for creating or renaming folders in the queue manager hierarchy.
 */
public class FolderDialog {
    private final Shell parentShell;
    private Shell shell;
    private Text nameText;
    private String folderName;
    private boolean isRename;

    public FolderDialog(Shell parent) {
        this(parent, false, null);
    }

    public FolderDialog(Shell parent, boolean isRename, String currentName) {
        this.parentShell = parent;
        this.isRename = isRename;
        this.folderName = currentName;
    }

    /**
     * Open the dialog and return the folder name, or null if cancelled.
     */
    public String open() {
        createShell();
        createContents();

        shell.pack();
        shell.setSize(400, 150);

        // Center on parent
        shell.setLocation(
            parentShell.getLocation().x + (parentShell.getSize().x - shell.getSize().x) / 2,
            parentShell.getLocation().y + (parentShell.getSize().y - shell.getSize().y) / 2
        );

        shell.open();

        Display display = parentShell.getDisplay();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        return folderName;
    }

    private void createShell() {
        shell = new Shell(parentShell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        shell.setText(isRename ? "Rename Folder" : "New Folder");
        shell.setLayout(new GridLayout());
    }

    private void createContents() {
        // Name field
        Composite nameComposite = new Composite(shell, SWT.NONE);
        nameComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout nameLayout = new GridLayout(2, false);
        nameComposite.setLayout(nameLayout);

        Label nameLabel = new Label(nameComposite, SWT.NONE);
        nameLabel.setText("Folder Name:");

        nameText = new Text(nameComposite, SWT.BORDER);
        nameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        if (folderName != null) {
            nameText.setText(folderName);
            nameText.selectAll();
        }

        // Button bar
        Composite buttonBar = new Composite(shell, SWT.NONE);
        buttonBar.setLayoutData(new GridData(SWT.END, SWT.BOTTOM, true, true));
        GridLayout buttonLayout = new GridLayout(2, true);
        buttonLayout.marginWidth = 0;
        buttonBar.setLayout(buttonLayout);

        Button okButton = new Button(buttonBar, SWT.PUSH);
        okButton.setText("OK");
        okButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        okButton.addListener(SWT.Selection, e -> onOk());

        Button cancelButton = new Button(buttonBar, SWT.PUSH);
        cancelButton.setText("Cancel");
        cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        cancelButton.addListener(SWT.Selection, e -> onCancel());

        // Default button
        shell.setDefaultButton(okButton);

        // Enter key listener
        nameText.addListener(SWT.Traverse, e -> {
            if (e.detail == SWT.TRAVERSE_RETURN) {
                onOk();
            }
        });
    }

    private void onOk() {
        String name = nameText.getText().trim();

        if (name.isEmpty()) {
            MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
            box.setText("Invalid Name");
            box.setMessage("Folder name cannot be empty.");
            box.open();
            nameText.setFocus();
            return;
        }

        folderName = name;
        shell.dispose();
    }

    private void onCancel() {
        folderName = null;
        shell.dispose();
    }
}
