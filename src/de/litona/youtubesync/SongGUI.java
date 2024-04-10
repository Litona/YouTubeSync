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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SongGUI {

	private static final SimpleDateFormat ytUploadDateFormat = new SimpleDateFormat("yyyyMMdd");

	public final JFrame frame;
	private JPanel basePanel;
	private JTextField urlField, tagsField, interpretField, simpleTitleField, yearField;
	private JLabel ytTitleLabel, yearLabel;
	private JProgressBar progressBar1;
	private JButton okButton, cancelButton;
	private JList<String> tagsList;
	private JScrollPane existingTagsPane;
	private JSpinner duplicatesSpinner;
	private JSlider duplicatesSlider;
	private JTable duplicatesTable;
	private JButton retryThumbnailButton;
	private JPanel existingTagsPanel = new JPanel(new GridLayout(0, 7));

	private DocumentListener duplicatesListener;
	TableRowSorter<DefaultTableModel> duplicatesSorter;

	private String ytId, ytTitle, ytUploadDate;
	private SynchedSong existingSong;
	private boolean cancel, ok, skip;

	public SongGUI() {
		frame = new JFrame();
		frame.setSize(1200, 500);
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
						tagsField.setText(tagsField.getText() + " " + t + " ");
					else
						tagsField.setText(tagsField.getText().replaceAll(" " + t + " ", ""));
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

			private void urlFound() {
				String url = urlField.getText().trim();
				if(url.toLowerCase().contains("youtube.com/watch?v=")) {
					try {
						Process p = Runtime.getRuntime().exec("yt-dlp " + url + " --dump-json --age-limit 99");
						try(BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
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

							// regex Interpret and Title processing
							String futureInterpret, futureTitle;
							ytTitle = ytTitle.replaceAll("–", "-"); // first dash is a so called "em-dash"
							if(ytTitle.contains(" - ")) { // ytTitle format: interpret - title
								String[] splitTitle = ytTitle.split(" - ", 2);
								futureInterpret = splitTitle[0];
								futureTitle = splitTitle[1];
							} else { // else use yt uploader and whole ytTitle
								futureInterpret = json.getString("uploader").replaceAll("\\s+[-–]\\s+((Topic)|(Thema))\\s*$", "");
								/**
								 * Regex explanation for "\\s+[-–]\\s+((Topic)|(Thema))\\s*$"
								 * \\s+ one or more whitespaces
								 * [-–] (class of) dash or em-dash. So basically one of those characters follows
								 * \\s+ one or more whitespaces
								 * ((Topic)|(Thema)) "Topic" or "Thema" follows
								 * \\s* zero or more whitespaces at the
								 * $ END OF STRING
								 */
								futureTitle = ytTitle;
							}
							Function<String, String> removeAppendices = (in) -> {
								String[] appendicesVideo = in
									.split("(?i)\\s+[(\\[]?((\\s)|(Official)|(HD)|(4K)|(Music)|(Lyrics?))*Video(clip)?((\\s)|(HD)|(4K))*[)\\]]?\\s*$");
								/**
								 * Regex explanation for "(?i)\\s+[(\\[]?((\\s)|(Official)|(HD)|(4K)|(Music)|(Lyric))*Video(clip)?((\\s)|(HD)|(4K))*[)\\]]?\\s*$"
								 * (?i) regex is case-insensitive
								 * \\s+ one or more whitespaces
								 * [(\\[]? optional class of "(" and "[". So maybe there's "(" or "["
								 * ((\\s)|(Official)|(HD)|(4K)|(Music)|(Lyrics?))* class of whitespace, "Official", "HD", "4K", "Music" and "Lyric" or "Lyric*s*". * means those words occur zero or more times
								 * Video mandatory
								 * (clip)? maybe its Videoclip
								 * ((\\s)|(HD)|(4K))* class of whitespace, "HD" and "4K". * means those words occur zero or more times
								 * [)\\]]? optional class of ")" and "]". So basically maybe the word is in braces
								 * \\s* zero or more whitespaces at the
								 * $ END OF STRING
								 */
								in = appendicesVideo[0];
								String[] appendicesLyrics = in.split("(?i)\\s+[(\\[]?\\s*(with\\s+)?Lyrics(\\s+on\\s+screen)?\\s*[)\\]]?\\s*$");
								/**
								 * Regex explanation for "(?i)\\s+[(\\[]?(with\\s*)?Lyrics(\\s+on\\s+screen)?[)\\]]?\\s*$"
								 * (?i) regex is case-insensitive
								 * \\s+ one or more whitespaces
								 * [(\\[]? optional class of "(" and "[". So maybe there's "(" or "["
								 * \\s* zero or more whitespaces
								 * (with\\s+)? maybe "with" stands (with one or more whitespaces) before
								 * Lyrics mandatory
								 * (\\s+on\\s+screen)? maybe its followed by "on screen"
								 * \\s* zero or more whitespaces
								 * [)\\]]? optional class of ")" and "]". So basically maybe the word is in braces
								 * \\s* zero or more whitespaces at the
								 * $ END OF STRING
								 */
								in = appendicesLyrics[0];
								String[] appendicesAudio = in.split("(?i)\\s+[(\\[]\\s*(Official\\s*)?Audio\\s*[)\\]]\\s*$");
								/**
								 * Regex explanation for "(?i)\\s+[(\\[]\\s*(Official\\s*)?Audio\\s*[)\\]]\\s*$"
								 * (?i) regex is case-insensitive
								 * \\s+ one or more whitespaces
								 * [(\\[] mandatory class of "(" and "[". So there MUST be "(" or "["
								 * \\s* zero or more whitespaces
								 * (Official\\s*)? Optional "Official" and zero or more whitespaces
								 * Audio mandatory
								 * \\s* zero or more whitespaces
								 * [(\\]] mandatory class of ")" and "]". So there MUST be ")" or "]"
								 * \\s* zero or more whitespaces at the
								 * $ END OF STRING
								 */
								in = appendicesAudio[0];
								return in;
							};
							futureTitle = removeAppendices.apply(
								futureTitle); // searching for ftInterprets in the middle of a String is hard, so let's first try to remove the appendices
							String[] ftInterprets = futureTitle.split("(?i)\\s+[(\\[]?f(ea)?t\\.?\\s+"); // then check for ft Interprets
							/**
							 * Regex explanation for "(?i)\\s+[(\\[]?f(ea)?t\\.?\\s+"
							 * (?i) regex is case-insensitive
							 * \\s+ one or more whitespaces
							 * [(\\[]? optional class of "(" and "[". So maybe there's "(" or "["
							 * f(ea)?t mandatory ft, but maybe "ea" is inbetween, so ft or feat
							 * \\.? maybe theres a "." afterwards
							 * \\s+ one or more whitespaces
							 * the regex has ended without closing braces. This is because now the interpret follows. The (optional) ending braces will be removed later
							 */
							if(ftInterprets.length > 1 && !ftInterprets[1].isEmpty()) {
								futureTitle = ftInterprets[0];
								futureInterpret = futureInterpret + ", " + ftInterprets[1].replaceAll("\\s*[)\\]]\\s*$", "");
								/**
								 * Regex explanation for "\\s*[)\\]]\\s*$"
								 * \\s* zero or more whitespaces
								 * [)\\]] class of ending braces
								 * \\s* one or more whitespaces before
								 * $ END OF THE STRING
								 *
								 * (Optional) ending braces of a ft. Interpret are removed
								 */
							}
							futureTitle = removeAppendices.apply(
								futureTitle); // and if the Interprets were actually at the end, we can remove the appendices now, since the new string has the appendices at the end
							if(interpretField.getText().trim().isEmpty())
								interpretField.setText(futureInterpret.trim());
							if(simpleTitleField.getText().trim().isEmpty())
								simpleTitleField.setText(futureTitle.trim());

							duplicatesListener.insertUpdate(null); // call to check for duplicates
						} catch(IOException e) {
							e.printStackTrace();
						} catch(JSONException e) {
							System.out.println(url + " no longer online");
							ytId = url.split("watch\\?v=")[1].split("&")[0];
							e.printStackTrace();
						}
					} catch(IOException e) {
						e.printStackTrace();
					}
				}
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
						/**
						 * Regex explanation for "[\\s&]+"
						 * + one or more characters from the class []
						 * [\\s&] class of whitespace and "&"
						 *
						 * Google url uses "+" instead of whitespace
						 * and & is a special character in the url, so we replace that
						 */
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
		tagsField.setText(" " + String.join("  ", preSong.getTags()) + " ");
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

		if(!existingSong.hasThumbnail()) {
			retryThumbnailButton.setVisible(true);
			retryThumbnailButton.addActionListener(e -> {
				retryThumbnailButton.setVisible(false);
				existingSong.retryThumbnail();
			});
		}

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
				interpretField.getText().trim(), yearField.getText().trim(), ytUploadDate == null ? -1 : ytUploadDateFormat.parse(ytUploadDate).getTime(),
				false);
		} catch(ParseException e) {
			e.printStackTrace();
			throw new InterruptedException("the show must go on; ignore Interrupted Exception and fix ParseException!");
		}
	}
}
