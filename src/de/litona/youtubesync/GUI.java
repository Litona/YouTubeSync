package de.litona.youtubesync;

import com.bulenkov.darcula.DarculaLaf;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class GUI extends JFrame {

	private static final File configurationFile = new File(System.getenv("APPDATA") + "//Litona//YouTubeSync//configuration.json");
	private static final File songsFile = new File(System.getenv("APPDATA") + "//Litona//YouTubeSync//songs.json");

	private static GUI gui;
	private static Queue<PreSynchedSong> songsToSynch = new ConcurrentLinkedQueue<PreSynchedSong>() {
		@Override
		public boolean add(PreSynchedSong o) {
			return !contains(o) && super.add(o);
		}
	};
	static List<SynchedSong> songs = Collections.emptyList();
	static final Map<String, String> ytPlaylistSources = new ConcurrentHashMap<>();
	private static File songsFolder;
	private static boolean changesToSongs = false;
	private static boolean changesToConfiguration = false;
	private static boolean setThumbnails = true;

	static GUI getGui() {
		return gui;
	}

	public static void main(String... args) {
		configurationFile.getParentFile().mkdirs();
		// Deserializing Configuration
		try(Stream<String> configurationJsonStream = Files.lines(configurationFile.toPath())) {
			JSONObject configuration = new JSONObject(configurationJsonStream.collect(Collectors.joining(" ")));
			if(configuration.has("songsFolder"))
				songsFolder = new File(configuration.getString("songsFolder"));
			else
				setSongsFolder();
			configuration.getJSONArray("sources")
				.forEach(j -> ytPlaylistSources.put(((JSONObject) j).getString("name"), ((JSONObject) j).getString("url")));
		} catch(IOException e) {
			System.out.println("Corrupt Configuration File?");
			e.printStackTrace();
			setSongsFolder();
		}
		// Deserializing Songs
		try(Stream<String> songsJsonStream = Files.lines(songsFile.toPath(), StandardCharsets.UTF_8)) {
			JSONArray array = new JSONObject(songsJsonStream.collect(Collectors.joining(" "))).getJSONArray("songs");
			songs = new ArrayList<>(array.length());
			for(int i = 0; i < array.length(); i++)
				songs.add(new SynchedSong(array.getJSONObject(i)));
		} catch(IOException e) {
			System.out.println("No Songs File");
			e.printStackTrace();
		}

		// Set Dracula Look
		try {
			javax.swing.UIManager.setLookAndFeel(new DarculaLaf());
		} catch(UnsupportedLookAndFeelException e) {
			System.out.println("Could not set Dark Look");
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch(ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException classNotFoundException) {
				System.out.println("Could not set Windows Look");
				classNotFoundException.printStackTrace();
			}
			e.printStackTrace();
		}

		gui = new GUI();
	}

	private static void setSongsFolder() {
		JFileChooser fc = new JFileChooser();
		fc.setDragEnabled(false);
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if(fc.showOpenDialog(fc) == JFileChooser.APPROVE_OPTION) {
			songsFolder = fc.getSelectedFile();
			changesToConfiguration = true;
		}
	}

	static File getSongsFolder() {
		return songsFolder;
	}

	private JPanel basePanel;
	private JButton addSongsButton;
	private JButton classifyButton;
	private JProgressBar progressBar;
	private JTable songlistTable;
	private JList<String> tagsList;
	private JList<String> sourceList;
	private JButton addSourceButton;
	private JTextField searchTagsField;
	private JTextField searchElseField;
	private JButton playButton;
	private JLabel countLabel;
	private JButton exportButton;
	private JCheckBox strictModeField;

	private TableRowSorter<DefaultTableModel> songlistSorter;

	JProgressBar getProgressBar() {
		return progressBar;
	}

	private void createUIComponents() {
		Object[][] songlistTableData = new Object[songs.size()][];
		AtomicInteger index = new AtomicInteger();
		// Fill array with data from songs collection; ordered by Added -> Index
		songs.forEach(s -> songlistTableData[index.get()] = s.getJTableData(index.incrementAndGet(), false));
		songlistTable = new JTable( //
			new DefaultTableModel( // modified model; see comments below
				songlistTableData, //
				new Object[] {"Index", "Thumbnail", "Interpret", "Title", "Tags", "Year"} // headlines for collumns
			) {
				@Override
				public boolean isCellEditable(int row, int column) {
					return false; // Prevent any modifications to songlistTable by user
				}

				@Override
				public Class<?> getColumnClass(int columnIndex) {
					return getValueAt(0, columnIndex).getClass(); // correct rendering (mainly for thumbnails)
				}
			});
	}

	private GUI() {
		//just for testing purposes
		/*
		System.out.println("start");
		AtomicInteger ites = new AtomicInteger(0);
		System.out.println(songs.parallelStream().mapToLong(s -> {
			try {
				System.out.println(ites.incrementAndGet());
				Mp3File mp3File = new Mp3File(s.getFile());
				long l = mp3File.getLengthInSeconds();
				if(l < 960)
					return l;
			} catch(IOException e) {
				e.printStackTrace();
			} catch(UnsupportedTagException e) {
				e.printStackTrace();
			} catch(InvalidDataException e) {
				e.printStackTrace();
			}
			return 0;
		}).sum() + "sek");
		System.out.println("end");

		class Occurrence {
			String firstInterpret;
			Collection<String> others = new HashSet<>();

			Occurrence(String s) {
				firstInterpret = s;
			}

			@Override
			public String toString() {
				return firstInterpret + " (" + String.join(", ", others) + ")";
			}
		}
		Map<Occurrence, Integer> occurrences = new HashMap<>();
		songs.stream().map(SynchedSong::getInterpret)
			.flatMap(s -> Stream.of(s.split("(\\s+(([Ff][Tt])|([Vv][Ss])|([Ff][Ee][Aa][Tt])|([Xx&?/-]))\\.?\\s+)|(,\\s*)"))).forEach(s -> {
			if(!s.isEmpty() && s.length() > 1) {
				Occurrence found = occurrences.keySet().stream()
					.filter(o -> SynchedSong.diceCoefficientOptimized(o.firstInterpret.toLowerCase(), s.toLowerCase()) > 0.8).findAny()
					.orElseGet(() -> null);
				Occurrence toAdd = found != null ? found : new Occurrence(s);
				if(found != null && !s.equals(found.firstInterpret)) {
					found.others.add(s);
				}
				occurrences.put(toAdd, occurrences.getOrDefault(toAdd, 0) + 1);
			}
		});
		occurrences.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(e -> System.out.println(e.getKey() + "	" + e.getValue()));

		try {
			SynchedSong test = songs.get(2);
			Mp3File mp3File = new Mp3File(test.getFile());
			ID3v2 tag;
			if(mp3File.hasId3v2Tag())
				tag = mp3File.getId3v2Tag();
			else
				mp3File.setId3v2Tag(tag = new ID3v24Tag());
			tag.setArtist(test.getInterpret());
			tag.setTitle(test.getSimpleTitle());
			tag.setYear(test.getYear());
			tag.setGenreDescription(String.join(", ", test.getTags()));
			tag.setUrl(test.getYtId());
			mp3File.save("D:\\test.mp3");
		} catch(UnsupportedTagException e) {
			e.printStackTrace();
		} catch(IOException e) {
			e.printStackTrace();
		} catch(InvalidDataException e) {
			e.printStackTrace();
		} catch(NotSupportedException e) {
			e.printStackTrace();
		}
		*/
		//testing ends

		songs = new CopyOnWriteArrayList<>(songs);
		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) { // onClose:
				if(changesToSongs) { // only if changes were applied
					// Serializing songs
					JSONObject json = new JSONObject();
					JSONArray array = new JSONArray();
					songs.stream().map(s -> {
						JSONObject newJson = null;
						try {
							newJson = s.toJson(false);
						} catch(FileNotFoundException e) {
							e.printStackTrace();
						}
						return newJson;
					}).forEach(array::put); // fill array with serialized(json) songs
					json.put("songs", array);

					System.out.println("Archiving old songs file");
					File[] old = new File[] {new File(songsFile.getParentFile(), "songs.old0"), new File(songsFile.getParentFile(), "songs.old1"),
						new File(songsFile.getParentFile(), "songs.old2")};
					old[2].delete();
					old[1].renameTo(old[2]);
					old[0].renameTo(old[1]);
					songsFile.renameTo(old[0]);
					System.out.println("Writing new songs file");
					try(PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(songsFile), StandardCharsets.UTF_8))) {
						out.println(json.toString());
					} catch(FileNotFoundException e) {
						e.printStackTrace();
					}
				}
				if(changesToConfiguration) { // only if changes were applied
					// Serializing songs
					JSONObject json = new JSONObject();
					if(songsFolder != null)
						json.put("songsFolder", songsFolder);

					JSONArray array = new JSONArray(); // array for ytplaylist sources with name and url fields
					ytPlaylistSources.forEach((n, u) -> {
						JSONObject source = new JSONObject();
						source.put("name", n);
						source.put("url", u);
						array.put(source);
					});
					json.put("sources", array);

					System.out.println("Deleting old config");
					configurationFile.delete();
					System.out.println("Writing new config");
					try(PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(configurationFile), StandardCharsets.UTF_8))) {
						out.println(json.toString());
					} catch(FileNotFoundException e) {
						e.printStackTrace();
					}
				}

				System.exit(0); // exit on close window
			}
		});
		this.setSize(1400, 700);
		this.setTitle("YouTubeSync GUI");
		this.setContentPane(basePanel);

		songlistTable.getTableHeader().setReorderingAllowed(false);
		songlistTable.getColumnModel().getColumn(0).setPreferredWidth(40);
		songlistTable.getColumnModel().getColumn(1).setPreferredWidth(112);
		for(int column = 2; column < songlistTable.getColumnCount(); column++) {
			int width = 1; // Min width
			for(int row = 0; row < songlistTable.getRowCount(); row++)
				width = Math.max(songlistTable.prepareRenderer(songlistTable.getCellRenderer(row, column), row, column).getPreferredSize().width, width);
			songlistTable.getColumnModel().getColumn(column).setPreferredWidth(width);
		}
		songlistTable.setRowHeight(63);
		songlistTable.setRowSorter(songlistSorter = new TableRowSorter<>((DefaultTableModel) songlistTable.getModel()));

		sourceList.setModel(new DefaultListModel<>());
		tagsList.setModel(new DefaultListModel<>()); // will be filled in DocumentListener
		// Filling SourcePanel
		ytPlaylistSources.keySet().stream().sorted(Comparator.comparing(String::toLowerCase))
			.forEach(name -> ((DefaultListModel<String>) sourceList.getModel()).addElement(name));
		sourceList.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if(e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE && sourceList.getSelectedIndices().length > 0) {
					sourceList.getSelectedValuesList().forEach(v -> {
						ytPlaylistSources.remove(v);
						((DefaultListModel<String>) sourceList.getModel()).removeElement(v);
					});
					changesToConfiguration = true;
				}
			}
		});
		tagsList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				String tagToInput = tagsList.getModel().getElementAt(tagsList.locationToIndex(e.getPoint()));
				if(tagToInput.startsWith("✓")) {
					if(SwingUtilities.isRightMouseButton(e))
						searchTagsField.setText(searchTagsField.getText().replaceAll(" " + tagToInput.substring(1) + " ", ""));
				} else
					searchTagsField.setText(searchTagsField.getText() + (SwingUtilities.isRightMouseButton(e) ? " -" : " ") + tagToInput + " ");
			}
		});

		DocumentListener filterListener = new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				updateSonglistTableView();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				updateSonglistTableView();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				updateSonglistTableView();
			}

			private void updateSonglistTableView() {
				SongTagFilter<Object, Object> tagFilter = strictModeField.isSelected() ?
					new StrictSongTagFilter<>(searchTagsField.getText()) :
					new SongTagFilter<>(searchTagsField.getText());
				songlistSorter.setRowFilter(RowFilter.andFilter(Arrays.asList(RowFilter.orFilter(
					Arrays.asList(new SimpleSearchTitleFilter<>(searchElseField.getText()), new SimpleSearchInterpretFilter<>(searchElseField.getText()))),
					tagFilter)));
				countLabel.setText(songlistTable.getRowCount() + " songs found with following tags:");
				((DefaultListModel<String>) tagsList.getModel()).removeAllElements();
				tagFilter.getResults().stream().map(SynchedSong::getTags).flatMap(Collection::stream).distinct()
					.sorted(Comparator.comparing(String::toLowerCase)).forEach(t -> ((DefaultListModel<String>) tagsList.getModel())
					.addElement(searchTagsField.getText().toLowerCase().contains(t.toLowerCase()) ? "✓" + t : t));
			}
		};
		searchElseField.getDocument().addDocumentListener(filterListener);
		searchTagsField.getDocument().addDocumentListener(filterListener);
		strictModeField.addActionListener(e -> filterListener.changedUpdate(null));
		filterListener.changedUpdate(null);

		addSourceButton.addActionListener(e -> new AddSourceGUI());

		playButton.addActionListener(e -> {
			if(new File("C:\\Program Files\\VideoLAN\\VLC\\vlc.exe").exists()) {
				File tmp = new File(System.getProperty("user.dir"), "tmpVLCstart" + System.currentTimeMillis() + ".bat");
				try {
					try(PrintWriter out = new PrintWriter(tmp)) {
						for(int i : songlistTable.getSelectedRows())
							out.println(
								"start \"C:\\Program Files\\VideoLAN\\VLC\\vlc.exe\" \"" + songs.get(songlistTable.convertRowIndexToModel(i)).getFile()
									.getPath() + "\"");
					}
					new ProcessBuilder(tmp.getPath()).inheritIO().start().waitFor();
				} catch(IOException | InterruptedException exception) {
					exception.printStackTrace();
				} finally {
					tmp.delete();
				}
			} else
				System.out.println("VLC not installed!");
		});

		new Thread(() -> {
			AtomicInteger threadIndex = new AtomicInteger();
			songs.forEach(s -> {
				ImageIcon thumbnail = s.getScaledThumbnail();
				if(thumbnail != null && setThumbnails)
					songlistTable.getModel().setValueAt(thumbnail, threadIndex.get(), 1);
				threadIndex.incrementAndGet();
			});
		}).start();

		new Thread(() -> {
			progressBar.setMaximum(ytPlaylistSources.size());
			progressBar.setString("Synching songs...");
			AtomicInteger threadIndex = new AtomicInteger();
			ytPlaylistSources.forEach((n, u) -> {
				try(BufferedReader reader = new BufferedReader(new InputStreamReader(
					new ProcessBuilder("youtube-dl", u, "--dump-json", "--flat-playlist", "--age-limit", "99").start().getInputStream()))) {
					progressBar.setValue(threadIndex.incrementAndGet());
					reader.lines().map(JSONObject::new).forEach(json -> {
						String ytId = json.getString("id");
						if(songs.stream().map(SynchedSong::getYtId).noneMatch(ytId::equals))
							songsToSynch.add(songsToSynch.stream().filter(alreadyIn -> alreadyIn.getYtId().equals(ytId)).findAny()
								.orElseGet(() -> new PreSynchedSong(ytId, json.getString("title"), Collections.emptySet())).addTagQuietly(n));
					});
				} catch(IOException e) {
					e.printStackTrace();
				}
			});
			progressBar.setString(songsToSynch.isEmpty() ? "Finished synching songs" : "Click to synch " + songsToSynch.size() + " songs");
			progressBar.setValue(progressBar.getMaximum());
			progressBar.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseReleased(MouseEvent e) {
					progressBar.removeMouseListener(this);
					progressBar.setString("");
					progressBar.setValue(0);
					new Thread(() -> {
						if(!songsToSynch.isEmpty()) {
							SongGUI openGui;
							SongGUI nextGui;
							int numberOfSongs = songsToSynch.size();
							int myIndex = 0;
							nextGui = new SongGUI(songsToSynch.remove(), numberOfSongs,
								myIndex++); // prepare first GUI (means loading yt Information from URL)
							while(!songsToSynch.isEmpty()) {
								openGui = nextGui;
								openGui.frame.setVisible(true); // open first GUI, so user can input information while next GUI is prepared in next line
								nextGui = new SongGUI(songsToSynch.remove(), numberOfSongs, myIndex++); // prepare next GUI
								if(!DownloadGuiFinalizer.singleton().successGui(openGui))
									return;
							}
							// last song is only prepared in GUI, never downloaded. Here comes the code for downloading this song.
							// second usage for this code piece: If only one song is to be synched, loop will never be executed. First song needs to be downloaded as well!
							nextGui.frame.setVisible(true);
							DownloadGuiFinalizer.singleton().successGui(nextGui);
						}
					}).start();
				}
			});
		}).start();
		addSongsButton.addActionListener(e -> {
			new Thread(() -> {
				try {
					SongGUI gui = new SongGUI();
					gui.frame.setVisible(true);
					SynchedSong newSynchedSong = gui.getNewSynchedSong();
					if(newSynchedSong != null && newSynchedSong.getFile() != null)
						addSong(newSynchedSong);
				} catch(InterruptedException | FileNotFoundException interruptedException) {
					interruptedException.printStackTrace();
				}
			}).start();
		});
		classifyButton.addActionListener(e -> {
			new Thread(() -> {
				Queue<SynchedSong> songsToClassify = songs.stream().filter(s -> s.getClassificationLevel() < 2 && s.getAdded() < 1600184826488l)
					.collect(Collectors.toCollection(LinkedList::new));
				if(!songsToClassify.isEmpty()) {
					SongGUI openGui;
					SongGUI nextGui;
					int numberOfSongs = songsToClassify.size();
					int myIndex = 0;
					nextGui = new SongGUI(songsToClassify.remove(), numberOfSongs, myIndex++); // prepare first GUI (means loading yt Information from URL)
					while(!songsToClassify.isEmpty()) {
						openGui = nextGui;
						openGui.frame.setVisible(true); // open first GUI, so user can input information while next GUI is prepared in next line
						openGui.tmp();
						nextGui = new SongGUI(songsToClassify.remove(), numberOfSongs, myIndex++); // prepare next GUI
						if(!successClassifyGui(openGui))
							return;
					}
					// last song is only prepared in GUI, never downloaded. Here comes the code for downloading this song.
					// second usage for this code piece: If only one song is to be synched, loop will never be executed. First song needs to be downloaded as well!
					nextGui.frame.setVisible(true);
					nextGui.tmp();
					successClassifyGui(nextGui);
				}
			}).start();
		});
		songlistTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if(e.getClickCount() == 2) {
					SynchedSong selected = songs.get(songlistTable.convertRowIndexToModel(songlistTable.getSelectedRow()));
					new Thread(() -> {
						SongGUI cGui = new SongGUI(selected, 1, 1);
						cGui.frame.setVisible(true);
						successClassifyGui(cGui);
					}).start();
				}
			}
		});
		exportButton.addActionListener(e -> {
			JFileChooser fc = new JFileChooser();
			fc.setDragEnabled(false);
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if(fc.showOpenDialog(fc) == JFileChooser.APPROVE_OPTION) {
				progressBar.setValue(0);
				progressBar.setString("Exporting songs...");
				progressBar.setMaximum(songlistTable.getSelectedRowCount());
				File fcDir = fc.getSelectedFile();
				File fcDirJson = new File(fcDir, "songs.json");
				Collection<SynchedSong> fcDirSongs = new TreeSet<>();
				if(fcDirJson.exists())
					try(Stream<String> songsJsonStream = Files.lines(fcDirJson.toPath(), StandardCharsets.UTF_8)) {
						JSONArray array = new JSONObject(songsJsonStream.collect(Collectors.joining(" "))).getJSONArray("songs");
						for(int i = 0; i < array.length(); i++)
							fcDirSongs.add(new SynchedSong(array.getJSONObject(i)));
					} catch(IOException | JSONException e2) {
						System.out.println("No fcSongs File");
						e2.printStackTrace();
					}

				// Serializing songs
				JSONObject json = new JSONObject();
				JSONArray array = new JSONArray();
				Collection<SynchedSong> copyList = new HashSet<>();
				for(int i : songlistTable.getSelectedRows())
					try {
						SynchedSong toCopy = songs.get(songlistTable.convertRowIndexToModel(i));
						array.put(toCopy.toJson(true));
						if(!fcDirSongs.contains(toCopy))
							copyList.add(toCopy);
					} catch(IOException ioException) {
						ioException.printStackTrace();
					}
				json.put("songs", array);
				AtomicInteger integer = new AtomicInteger();
				new Thread(() -> {
					copyList.parallelStream().forEach(toCopy -> {
						try {
							progressBar.setValue(integer.incrementAndGet());
							Files.copy(toCopy.getFile().toPath(), new File(fcDir, toCopy.getFile().getName()).toPath());
						} catch(IOException ioException) {
							ioException.printStackTrace();
						}
					});
					progressBar.setString("Finished export!");
					progressBar.setValue(progressBar.getMaximum());
					System.out.println("Export finished");
				}).start();
				System.out.println("Writing export songs file, songs are BEING copied");
				try(PrintWriter out = new PrintWriter(
					new OutputStreamWriter(new FileOutputStream(new File(fcDir, "songs.json")), StandardCharsets.UTF_8))) {
					out.println(json.toString());
				} catch(FileNotFoundException e1) {
					e1.printStackTrace();
				}
			}
		});

		this.setVisible(true);
	}

	void addSource(String name, String url) {
		if(!name.isEmpty() && !url.isEmpty() && !ytPlaylistSources.containsValue(url)) {
			ytPlaylistSources.put(name, url);
			changesToConfiguration = true;
			((DefaultListModel<String>) sourceList.getModel()).addElement(name);
		} else
			System.out.println("Source already listed?");
	}

	void addSong(SynchedSong newSong) {
		songs.add(newSong);
		changesToSongs = true;
		((DefaultTableModel) songlistTable.getModel()).addRow(newSong.getJTableData(songs.size(), true));
	}

	private boolean successClassifyGui(SongGUI gui) {
		try {
			SynchedSong classifiedSong = gui.updateSynchedSong(); // finish user information and download song
			if(classifiedSong == null) // means user has cancelled synching songs
				return false;
			classifiedSong.setClassificationLevel(2);
			changesToSongs = true;
			int row = songs.indexOf(classifiedSong);
			Object[] data = classifiedSong.getJTableData(row + 1, true);
			for(int i = 2; i < data.length; i++)
				songlistTable.getModel().setValueAt(data[i], row, i);
		} catch(InterruptedException interruptedException) {
			interruptedException.printStackTrace();
			return false;
		}
		return true;
	}
}
