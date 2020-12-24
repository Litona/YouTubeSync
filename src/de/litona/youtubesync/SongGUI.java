package de.litona.youtubesync;

import org.json.JSONException;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SongGUI {

	private static final SimpleDateFormat ytUploadDateFormat = new SimpleDateFormat("yyyyMMdd");

	public final JFrame frame;
	private JPanel basePanel;
	private JTextField urlField;
	private JLabel ytTitleLabel;
	private JTextField tagsField;
	private JTextField interpretField;
	private JTextField simpleTitleField;
	private JTextField yearField;
	private JProgressBar progressBar1;
	private JButton okButton;
	private JList<String> tagsList;
	private JButton cancelButton;
	private JLabel yearLabel;
	private JScrollPane existingTagsPane;
	private JSpinner duplicatesSpinner;
	private JSlider duplicatesSlider;
	private JTable duplicatesTable;
	private JPanel existingTagsPanel = new JPanel(new GridLayout(0, 7));

	private DocumentListener duplicatesListener;
	TableRowSorter<DefaultTableModel> duplicatesSorter;

	private String ytId;
	private String ytTitle;
	private String ytUploadDate;
	private SynchedSong existingSong;
	private boolean cancel = false;
	private boolean ok = false;
	private boolean skip = false;

	public SongGUI() {
		frame = new JFrame();
		frame.setSize(1200, 460);
		frame.setTitle("YouTubeSync: Specify Song");
		frame.setContentPane(basePanel);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				skip = true;
				synchronized(SongGUI.this) {
					SongGUI.this.notifyAll();
				}
				frame.dispose();
			}
		});
		tagsList.setModel(new DefaultListModel<>());
		okButton.addActionListener(e -> {
			if(!urlField.getText().isEmpty() && !tagsField.getText().isEmpty()) {
				ok = true;
				synchronized(SongGUI.this) {
					SongGUI.this.notifyAll();
				}
				frame.dispose();
			}
		});
		cancelButton.addActionListener(e -> {
			cancel = true;
			synchronized(SongGUI.this) {
				SongGUI.this.notifyAll();
			}
			frame.dispose();
		});
		tagsField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				updateTagsList();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				updateTagsList();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				updateTagsList();
			}
		});
		GUI.songs.stream().map(SynchedSong::getTags).flatMap(Collection::stream).distinct().sorted(Comparator.comparing(String::toLowerCase))
			.forEach(t -> existingTagsPanel.add(new JCheckBox(new AbstractAction(t) {
				@Override
				public void actionPerformed(ActionEvent e) {
					if(((JCheckBox) e.getSource()).isSelected())
						tagsField.setText(tagsField.getText() + " " + t);
					else
						tagsField.setText(tagsField.getText().replaceAll(t, ""));
					updateTagsList();
				}
			})));
		existingTagsPane.setViewportView(existingTagsPanel);
		urlField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				urlFound();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				urlFound();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				urlFound();
			}
		});
		yearLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				if(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
					try {
						Desktop.getDesktop().browse(new URI("https://www.google.com/search?q=" + (
							interpretField.getText() != null && !interpretField.getText().isEmpty() && simpleTitleField.getText() != null
								&& !simpleTitleField.getText().isEmpty() ?
								interpretField.getText().trim().replaceAll("[\\s&]+", "+") + "+" + simpleTitleField.getText().trim().replaceAll(" ", "+") :
								(ytTitle != null ? ytTitle.trim().replaceAll("[\\s&]+", "+") : ""))));
					} catch(IOException | URISyntaxException ioException) {
						ioException.printStackTrace();
					}
			}
		});
		duplicatesListener = new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				update();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				update();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				update();
			}

			public void update() {
				((DefaultTableModel) duplicatesTable.getModel()).setRowCount(0);
				GUI.songs.forEach(s -> {
					if(ytId != null && !ytId.equals(s.getYtId()))
						((DefaultTableModel) duplicatesTable.getModel()).addRow(
							new Object[] {Math.round(s.similarTo(interpretField.getText(), simpleTitleField.getText()) * 1000d) / 10d, s.getInterpret(),
								s.getSimpleTitle()});
				});
				for(int column = 0; column < duplicatesTable.getColumnCount(); column++) {
					int width = 1; // Min width
					for(int row = 0; row < duplicatesTable.getRowCount(); row++)
						width = Math
							.max(duplicatesTable.prepareRenderer(duplicatesTable.getCellRenderer(row, column), row, column).getPreferredSize().width,
								width);
					duplicatesTable.getColumnModel().getColumn(column).setPreferredWidth(width);
				}
			}
		};
		interpretField.getDocument().addDocumentListener(duplicatesListener);
		simpleTitleField.getDocument().addDocumentListener(duplicatesListener);
		duplicatesTable.setRowSorter(duplicatesSorter = new TableRowSorter<>((DefaultTableModel) duplicatesTable.getModel()));
		duplicatesSpinner.addChangeListener(e -> {
			duplicatesSlider.setValue((Integer) duplicatesSpinner.getValue());
			setDuplicatesFilter();
		});
		duplicatesSlider.addChangeListener(e -> {
			duplicatesSpinner.setValue(duplicatesSlider.getValue());
			setDuplicatesFilter();
		});
		duplicatesSpinner.setModel(new SpinnerNumberModel(44, 20, 100, 1));
		duplicatesSlider.setModel(new DefaultBoundedRangeModel(44, 0, 20, 100));
		duplicatesSorter.setComparator(0, Comparator.comparingDouble(Double.class::cast));
		setDuplicatesFilter();
		progressBar1.setVisible(false);
	}

	SongGUI(PreSynchedSong preSong, int numberOfSongs, int indexOfSongs) {
		this();
		urlField.setText("https://www.youtube.com/watch?v=" + preSong.getYtId());
		//urlFound();
		tagsField.setText(String.join(" ", preSong.getTags()));
		updateTagsList();
		progressBar1.setMaximum(numberOfSongs);
		progressBar1.setValue(indexOfSongs + 1);
		progressBar1.setString("Classify " + indexOfSongs + "/" + numberOfSongs + " songs");
		progressBar1.setVisible(true);
		frame.setTitle("YouTubeSync: Add Song");
	}

	SongGUI(SynchedSong existingSong, int numberOfSongs, int indexOfSongs) {
		this((PreSynchedSong) existingSong, numberOfSongs, indexOfSongs);
		this.existingSong = existingSong;
		if(existingSong.hasSimpleTitle())
			simpleTitleField.setText(existingSong.getSimpleTitle());
		else if(ytTitleLabel.getText() == null || ytTitleLabel.getText().isEmpty())
			simpleTitleField.setText(existingSong.getYtTitle());
		if(existingSong.getInterpret() != null && !existingSong.getInterpret().isEmpty())
			interpretField.setText(existingSong.getInterpret());
		if(existingSong.hasYear())
			yearField.setText(existingSong.getYear());
		frame.setTitle("YouTubeSync: Classify Song");
		duplicatesListener.insertUpdate(null); // call for checking duplicates after title and interpret fill
	}

	private void createUIComponents() {
		duplicatesTable = new JTable( //
			new DefaultTableModel( // modified model; see comments below
				null, //
				new Object[] {"%", "Interpret", "Title"} // headlines for collumns
			) {
				@Override
				public boolean isCellEditable(int row, int column) {
					return false; // Prevent any modifications to songlistTable by user
				}
			});
		duplicatesTable.getTableHeader().setReorderingAllowed(false);
	}

	void tmp() {
		if(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
			try {
				Desktop.getDesktop().browse(new URI("https://www.google.com/search?q=" + (
					interpretField.getText() != null && !interpretField.getText().isEmpty() && simpleTitleField.getText() != null && !simpleTitleField
						.getText().isEmpty() ?
						interpretField.getText().trim().replaceAll(" ", "+") + "+" + simpleTitleField.getText().trim().replaceAll(" ", "+") :
						(ytTitle != null ? ytTitle.trim().replaceAll(" ", "+") : ""))));
			} catch(IOException | URISyntaxException ioException) {
				ioException.printStackTrace();
			}
		if(new File("C:\\Program Files\\VideoLAN\\VLC\\vlc.exe").exists()) {
			File tmp = new File(System.getProperty("user.dir"), "tmpVLCstart" + System.currentTimeMillis() + ".bat");
			try {
				try(PrintWriter out = new PrintWriter(tmp)) {
					out.println("start \"C:\\Program Files\\VideoLAN\\VLC\\vlc.exe\" \"" + existingSong.getFile().getPath() + "\"");
				}
				new ProcessBuilder(tmp.getPath()).inheritIO().start().waitFor();
			} catch(IOException | InterruptedException exception) {
				exception.printStackTrace();
			} finally {
				tmp.delete();
			}
		} else
			System.out.println("VLC not installed!");
	}

	private void updateTagsList() {
		DefaultListModel<String> tagsListModel = (DefaultListModel<String>) tagsList.getModel();
		tagsListModel.removeAllElements();
		Stream.of(existingTagsPanel.getComponents()).filter(JCheckBox.class::isInstance).map(JCheckBox.class::cast).forEach(cb -> cb.setSelected(false));
		Arrays.stream(tagsField.getText().trim().split("\\s+").clone()).sorted(Comparator.comparing(String::toLowerCase)).distinct().forEach(s -> {
			tagsListModel.addElement(s);
			Stream.of(existingTagsPanel.getComponents()).filter(JCheckBox.class::isInstance).map(JCheckBox.class::cast)
				.filter(cb -> cb.getText().equals(s)).forEach(cb -> cb.setSelected(true));
		});
	}

	private void urlFound() {
		String url = urlField.getText().trim();
		if(url.toLowerCase().contains("youtube.com/watch?v="))
			try(BufferedReader reader = new BufferedReader(
				new InputStreamReader(new ProcessBuilder("youtube-dl", url, "--dump-json", "--age-limit", "99").start().getInputStream()))) {
				JSONObject json = new JSONObject(reader.lines().collect(Collectors.joining(" ")));
				ytId = json.getString("id");
				ytTitle = json.getString("title");
				ytUploadDate = json.getString("upload_date");
				ytTitleLabel.setText("Title found: " + ytTitle);
				Stream.of(ytTitleLabel.getMouseListeners()).forEach(ytTitleLabel::removeMouseListener);
				ytTitleLabel.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseReleased(MouseEvent e) {
						if(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
							try {
								Desktop.getDesktop().browse(new URI(url));
							} catch(IOException | URISyntaxException ioException) {
								ioException.printStackTrace();
							}
					}
				});
				if(yearField.getText().trim().isEmpty())
					yearField.setText(ytUploadDate.substring(0, 4));
				if(ytTitle.contains(" - ")) {
					String[] splitTitle = ytTitle.split(" - ", 2);
					if(interpretField.getText().trim().isEmpty())
						interpretField.setText(splitTitle[0]);
					if(simpleTitleField.getText().trim().isEmpty())
						simpleTitleField.setText(splitTitle[1]);
				} else {
					if(interpretField.getText().trim().isEmpty())
						interpretField.setText(json.getString("uploader"));
					if(simpleTitleField.getText().trim().isEmpty())
						simpleTitleField.setText(ytTitle);
				}
				duplicatesListener.insertUpdate(null); // call to check for duplicates
			} catch(IOException e) {
				e.printStackTrace();
			} catch(JSONException e) {
				System.out.println(url + " no longer online");
				ytId = url.split("watch\\?v=")[1].split("&")[0];
			}
	}

	private void setDuplicatesFilter() {
		duplicatesSorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
			@Override
			public boolean include(Entry entry) {
				return Double.parseDouble(entry.getStringValue(0).replace("%", "")) >= duplicatesSlider.getValue();
			}
		});
	}

	public synchronized boolean isSuccess() throws InterruptedException {
		if(ok)
			return true;
		if(cancel)
			return false;
		this.wait();
		return !cancel;
	}

	public synchronized SynchedSong updateSynchedSong() throws InterruptedException {
		if(cancel)
			return null;
		if(!ok)
			this.wait();
		if(cancel)
			return null;
		if(skip)
			throw new InterruptedException();
		existingSong.setTags(Arrays.asList(tagsField.getText().trim().split("\\s+").clone()));
		existingSong.setSimpleTitle(simpleTitleField.getText().trim());
		existingSong.setInterpret(interpretField.getText().trim());
		existingSong.setYear(yearField.getText().trim());
		return existingSong;
	}

	public synchronized SynchedSong getNewSynchedSong() throws InterruptedException, FileNotFoundException {
		if(cancel)
			return null;
		if(!ok)
			this.wait();
		if(cancel)
			return null;
		if(skip)
			throw new InterruptedException();
		try {
			return new SynchedSong(ytId, ytTitle, Arrays.asList(tagsField.getText().trim().split("\\s+").clone()), simpleTitleField.getText().trim(),
				interpretField.getText().trim(), yearField.getText().trim(), ytUploadDate == null ? -1 : ytUploadDateFormat.parse(ytUploadDate).getTime());
		} catch(ParseException e) {
			e.printStackTrace();
			throw new InterruptedException("the show must go on; ignore Interrupted Exception and fix ParseException!");
		}
	}
}
