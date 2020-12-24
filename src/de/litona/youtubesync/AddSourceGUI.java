package de.litona.youtubesync;

import javax.swing.*;

public final class AddSourceGUI extends JFrame {
	private JPanel basePanel;
	private JTextField nameField;
	private JTextField urlField;
	private JButton addSourceButton;

	AddSourceGUI() {
		this.setSize(300, 175);
		this.setTitle("YouTubeSync: Add Source");
		this.setContentPane(basePanel);
		addSourceButton.addActionListener(e -> {
			if(!nameField.getText().isEmpty() && !urlField.getText().isEmpty()) {
				GUI.getGui().addSource(nameField.getText().trim(), urlField.getText().trim());
				AddSourceGUI.this.dispose();
			}
		});
		this.setVisible(true);
	}
}
