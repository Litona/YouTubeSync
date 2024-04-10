package de.litona.youtubesync;

import javax.swing.*;

public final class AddNewSongsChannelGUI extends JFrame {

	private JPanel basePanel;
	private JTextField nameField, urlField, fromField, toField;
	private JButton addNewSongsChannelButton;

	AddNewSongsChannelGUI(String name, String url, String from, String to) {
		this();
		nameField.setText(name);
		urlField.setText(url);
		fromField.setText(from);
		toField.setText(to);
	}

	AddNewSongsChannelGUI() {
		this.setSize(300, 175);
		this.setTitle("YouTubeSync: Add channel for new songs");
		this.setContentPane(basePanel);
		addNewSongsChannelButton.addActionListener(e -> {
			if(!nameField.getText().isEmpty() && !urlField.getText().isEmpty()) {
				GUI.getGui()
					.addNewSongsChannel(nameField.getText().trim(), urlField.getText().trim(), fromField.getText().trim(), toField.getText().trim());
				AddNewSongsChannelGUI.this.dispose();
			}
		});
		this.setVisible(true);
	}
}
