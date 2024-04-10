package de.litona.youtubesync;

import com.bulenkov.darcula.DarculaLaf;
import javafx.embed.swing.JFXPanel;
import javafx.scene.media.MediaPlayer;
import org.json.JSONArray;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.List;
import java.util.Queue;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class GUI extends JFrame {

	static final File configurationFile = new File(System.getenv("APPDATA") + "//Litona//YouTubeSync//configuration.json");
	static final File songsFile = new File(System.getenv("APPDATA") + "//Litona//YouTubeSync//songs.json");

	private static GUI gui;
	private static Queue<PreSynchedSong> songsToSynch = new ConcurrentLinkedQueue<PreSynchedSong>() {
		@Override
		public boolean add(PreSynchedSong o) {
			return !contains(o) && super.add(o);
		}
	};
	private static Queue<PreSynchedSong> songsToPlay = new ConcurrentLinkedQueue<PreSynchedSong>() {
		@Override
		public boolean add(PreSynchedSong o) {
			return !contains(o) && super.add(o);
		}
	};
	static List<SynchedSong> songs = Collections.emptyList();
	static final Map<String, String> ytPlaylistSources = new ConcurrentHashMap<>();
	static final Collection<NewSongsChannel> ytNewSongsChannels = new LinkedHashSet<>();
	static MediaPlayer mediaPlayer;
	private static File songsFolder, tempFolder;
	private static boolean changesToSongs, changesToConfiguration, ytPlaylistSourcesCrawlingDone;
	private static boolean setThumbnails = true;

	static GUI getGui() {
		return gui;
	}

	static void changesToConfiguration() {
		changesToConfiguration = true;
	}

	public static void main(String... args) {
		configurationFile.getParentFile().mkdirs();
		Stream.of(configurationFile.getParentFile().listFiles()).filter(f -> f.getName().endsWith(".ytsc")).forEach(File::delete);
		// Deserializing Configuration
		try(Stream<String> configurationJsonStream = Files.lines(configurationFile.toPath())) {
			JSONObject configuration = new JSONObject(configurationJsonStream.collect(Collectors.joining(" ")));
			if(configuration.has("songsFolder"))
				songsFolder = new File(configuration.getString("songsFolder"));
			else
				setSongsFolder();
			tempFolder = new File(songsFolder, "temp");
			tempFolder.mkdir();
			Arrays.stream(tempFolder.listFiles()).forEach(File::delete);
			if(configuration.has("sources"))
				configuration.getJSONArray("sources")
					.forEach(j -> ytPlaylistSources.put(((JSONObject) j).getString("name"), ((JSONObject) j).getString("url")));
			if(configuration.has("newSongsChannels"))
				configuration.getJSONArray("newSongsChannels").forEach(j -> {
					try {
						ytNewSongsChannels.add(new NewSongsChannel((JSONObject) j));
					} catch(ParseException e) {
						e.printStackTrace();
						System.out.println("Error parsing newSongsChannel List");
					}
				});
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
		new JFXPanel();
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

	static File getTempFolder() {
		return tempFolder;
	}

	private JPanel basePanel;
	private JButton addSongsButton, classifyButton, addSourceButton, playButton, exportButton, addNewSongsChannelButton, playNewSongsButton, refreshButton,
		interruptButton;
	private JProgressBar progressBar;
	private SongsToSynchBarMouseAdapter songsToSynchBarMouseAdapter;
	private JTable songlistTable;
	private JList<String> tagsList, sourceList, newSongsChannelList;
	private JTextField searchTagsField, searchElseField;
	private JLabel countLabel;
	private JCheckBox strictModeField;
	private JLabel newSongLabel;

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
		Collection<String> already = new HashSet<>();
		songs.forEach(one -> {
			songs.forEach(s -> {
				if(!one.ytId.equals(s.getYtId()) && !already.contains(one.getYtId() + s.getYtId())) {
					double per = Math.round(s.similarTo(one) * 1000d) / 10d;
					if(per > 70) {
						already.add(s.getYtId() + one.getYtId());
						System.out.println(
							per + "% " + one.getInterpret() + " - " + one.getSimpleTitle() + " ### " + s.getInterpret() + " - " + s.getSimpleTitle());
					}
				}
			});
		});
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

					// standard export options
					File nowSongsFolder = new File("D:\\Playlists\\now");
					File EDMSongsFolder = new File("D:\\Playlists\\EDM");
					File allSongsFolder = new File("D:\\Playlists\\export");
					if(nowSongsFolder.exists()) {
						Arrays.stream(nowSongsFolder.listFiles()).filter(File::isFile).forEach(File::delete);
						exportSongs(nowSongsFolder, songs.stream().filter(s -> s.getTags().contains("XXnow")).collect(Collectors.toSet()), false);
					}
					if(EDMSongsFolder.exists())
						exportSongs(EDMSongsFolder,
							songs.stream().filter(s -> s.getTags().contains("EDM") && !s.getTags().contains("XMAS") && !s.getTags().contains("Shit"))
								.collect(Collectors.toSet()), false);
					if(allSongsFolder.exists())
						exportSongs(allSongsFolder, songs.stream().filter(s -> !s.getTags().contains("XXdelete")).collect(Collectors.toSet()), false);
				}
				if(changesToConfiguration) { // only if changes were applied
					// Serializing songs
					JSONObject json = new JSONObject();
					if(songsFolder != null)
						json.put("songsFolder", songsFolder);

					JSONArray arrayPl = new JSONArray(); // array for ytplaylist sources with name and url fields
					ytPlaylistSources.forEach((n, u) -> {
						JSONObject source = new JSONObject();
						source.put("name", n);
						source.put("url", u);
						arrayPl.put(source);
					});
					json.put("sources", arrayPl);
					JSONArray arrayCh = new JSONArray(); // array for yt new songs channels with name and url fields
					ytNewSongsChannels.stream().map(NewSongsChannel::toJson).forEach(arrayCh::put);
					json.put("newSongsChannels", arrayCh);

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
		newSongsChannelList.setModel(new DefaultListModel<>());
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
		// Filling newSongsChannelsPanel
		ytNewSongsChannels.forEach(c -> {
			((DefaultListModel<String>) newSongsChannelList.getModel()).addElement(c.getName() + " from ");
			updateAddNewSongsChannelList(c);
		});
		newSongsChannelList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				if(SwingUtilities.isRightMouseButton(e)) {
					ytNewSongsChannels.forEach(NewSongsChannel::interrupt);
					String tagToInput = newSongsChannelList.getModel().getElementAt(newSongsChannelList.locationToIndex(e.getPoint()));
					String[] split;
					new AddNewSongsChannelGUI((split = tagToInput.split(" from "))[0], "unchanged", (split = split[1].split(" to "))[0],
						split[1].split(" = ")[0]);
				}
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
		addNewSongsChannelButton.addActionListener(e -> new AddNewSongsChannelGUI());

		playButton.addActionListener(e -> {
			if(new File("C:\\Program Files\\VideoLAN\\VLC\\vlc.exe").exists()) {
				File tmp = new File(System.getProperty("user.dir"), "tmpVLCstart" + System.currentTimeMillis() + ".bat");
				try {
					try(PrintWriter out = new PrintWriter(tmp)) {
						for(int i : songlistTable.getSelectedRows())
							out.println("start \"\" \"C:\\Program Files\\VideoLAN\\VLC\\vlc.exe\" \"" + songs.get(songlistTable.convertRowIndexToModel(i))
								.getFile().getPath() + "\" --one-instance --playlist-enqueue");
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
			// Generate a stream of sequential integers; then parallely iterate over it to set the Thumbnail for each indexed song
			IntStream.range(0, songlistTable.getModel().getRowCount()).boxed().parallel().forEach(i -> {
				ImageIcon thumbnail = songs.get(i).getScaledThumbnail();
				if(thumbnail != null && setThumbnails)
					songlistTable.getModel().setValueAt(thumbnail, i, 1);
			});
		}).start();

		refreshAddNewSongsChannelList();

		new Thread(() -> {
			progressBar.setMaximum(ytPlaylistSources.size());
			progressBar.setString("Synching songs...");
			AtomicInteger threadIndex = new AtomicInteger();
			ytPlaylistSources.forEach((n, u) -> {
				try {
					Process p = Runtime.getRuntime().exec("yt-dlp " + u + " --dump-json --ignore-errors --flat-playlist --age-limit 99");
					try(BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
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
				} catch(IOException e) {
					e.printStackTrace();
				}
			});
			progressBar.setValue(progressBar.getMaximum());
			ytPlaylistSourcesCrawlingDone = true;
			prepareProgressBarForSynchingSongs();
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
				Queue<SynchedSong> songsToClassify = songs.stream().filter(
					s -> s.getClassificationLevel() < 4 && (s.getTags().contains("Pop") || s.getTags().contains("Rock")) && !s.getTags().contains("XMAS")
						&& !s.getTags().contains("Oldies") && s.getAdded() < 1615486751913l).collect(Collectors.toCollection(LinkedList::new));
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
					if(SwingUtilities.isRightMouseButton(e)) {
						SynchedSong selected = songs.get(songlistTable.convertRowIndexToModel(songlistTable.getSelectedRow()));
						new Thread(() -> {
							SongGUI cGui = new SongGUI(selected, 1, 1);
							cGui.frame.setVisible(true);
							successClassifyGui(cGui);
						}).start();
					} else if(SwingUtilities.isLeftMouseButton(e)) {
						if(new File("C:\\Program Files\\VideoLAN\\VLC\\vlc.exe").exists()) {
							File tmp = new File(System.getProperty("user.dir"), "tmpVLCstart" + System.currentTimeMillis() + ".bat");
							try {
								try(PrintWriter out = new PrintWriter(tmp)) {
									for(int i : songlistTable.getSelectedRows())
										out.println(
											"start \"C:\\Program Files\\VideoLAN\\VLC\\vlc.exe\" \"" + songs.get(songlistTable.convertRowIndexToModel(i))
												.getFile().getPath() + "\"");
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
				}
			}
		});
		exportButton.addActionListener(e -> {
			JFileChooser fc = new JFileChooser();
			fc.setDragEnabled(false);
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if(fc.showOpenDialog(fc) == JFileChooser.APPROVE_OPTION)
				exportSongs(fc.getSelectedFile(),
					IntStream.of(songlistTable.getSelectedRows()).mapToObj(i -> songs.get(songlistTable.convertRowIndexToModel(i)))
						.collect(Collectors.toSet()), true);
		});
		refreshButton.addActionListener(e -> refreshAddNewSongsChannelList());
		interruptButton.addActionListener(e -> {
			interruptButton.setEnabled(false);
			ytNewSongsChannels.forEach(NewSongsChannel::interrupt);
		});
		playNewSongsButton.addActionListener(e -> {
			if(ytNewSongsChannels.stream().noneMatch(NewSongsChannel::isRunning))
				new Thread(() -> {
					playNewSongsButton.setEnabled(false);
					ytNewSongsChannels.stream().map(NewSongsChannel::getSongs).flatMap(Collection::stream).forEach(songsToPlay::add);
					if(!songsToPlay.isEmpty()) { // code analog to songgui downloading
						NewSongSupplier playingSong;
						NewSongSupplier nextSong = null;
						do {
							playingSong = nextSong;
							if(playingSong != null)
								newSongLabel.setText(playingSong
									.startAndGetYtTitle()); // open first GUI, so user can input information while next GUI is prepared in next line
							try {
								PreSynchedSong next = songsToPlay.remove();
								// TODO: replace nextSong = songsToSynch.contains(next) ? null : new NewSongSupplier(next); // prepare next GUI
								nextSong = new NewSongSupplier(next, false);
							} catch(FileNotFoundException fileNotFoundException) {
								fileNotFoundException.printStackTrace();
								nextSong = null;
							}
							if(playingSong == null)
								continue;
							if(playingSong.finishPlayingThenIsCancel()) {
								playNewSongsButton.setEnabled(true);
								newSongLabel.setText("");
								return;
							}
						} while(!songsToPlay.isEmpty());
						// last song is only prepared in GUI, never downloaded. Here comes the code for downloading this song.
						// second usage for this code piece: If only one song is to be synched, loop will never be executed. First song needs to be downloaded as well!
						if(nextSong != null) {
							newSongLabel.setText(nextSong.startAndGetYtTitle());
							nextSong.finishPlayingThenIsCancel();
						}
						newSongLabel.setText("");
					}
				}).start();
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

	void addNewSongsChannel(String name, String url, String from, String to) {
		if(!name.isEmpty() && !url.isEmpty()) {
			if(ytNewSongsChannels.stream().noneMatch(c -> c.getName().equals(name))) {
				try {
					NewSongsChannel channel = new NewSongsChannel(name, url);
					ytNewSongsChannels.add(channel);
					((DefaultListModel<String>) newSongsChannelList.getModel()).addElement(name);
					changesToConfiguration = true;
					updateAddNewSongsChannelList(channel);
				} catch(ParseException e) {
					e.printStackTrace();
				}
			} else
				ytNewSongsChannels.stream().filter(channel -> channel.getName().equals(name)).findAny().ifPresent(channel -> {
					try {
						channel.setFrom(from);
						channel.setTo(to);
						updateAddNewSongsChannelList(channel);
					} catch(ParseException e) {
						e.printStackTrace();
					}
				});
			ytNewSongsChannels.stream().filter(NewSongsChannel::isRunning).forEach(NewSongsChannel::interrupt);
			refreshButton.setFont(refreshButton.getFont().deriveFont(Font.ITALIC));
		} else
			System.out.println("New songs channel already listed?");
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
			classifiedSong.setClassificationLevel(4);
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

	private void exportSongs(File folder, Collection<SynchedSong> possibleExportSongs, boolean threadded) {
		progressBar.setValue(0);
		progressBar.setString("Exporting songs...");
		progressBar.setMaximum(songlistTable.getSelectedRowCount());
		File fcDirJson = new File(folder, "songs.json");
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
		possibleExportSongs.forEach(toCopy -> {
			try {
				array.put(toCopy.toJson(true));
				if(!fcDirSongs.contains(toCopy))
					copyList.add(toCopy);
			} catch(FileNotFoundException e) {
				e.printStackTrace();
			}
		});
		json.put("songs", array);
		System.out.println("Writing export songs file, songs are BEING copied");
		if(threadded)
			new Thread(() -> {
				exportSongsCopySongs(folder, copyList);
			}).start();
		else
			exportSongsCopySongs(folder, copyList);
		try(PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(folder, "songs.json")), StandardCharsets.UTF_8))) {
			out.println(json.toString());
		} catch(FileNotFoundException e1) {
			e1.printStackTrace();
		}
	}

	private void exportSongsCopySongs(File folder, Collection<SynchedSong> copyList) {
		AtomicInteger integer = new AtomicInteger();
		copyList.parallelStream().forEach(toCopy -> {
			try {
				progressBar.setValue(integer.incrementAndGet());
				Files.copy(toCopy.getFile().toPath(), new File(folder, toCopy.getFile().getName()).toPath());
			} catch(IOException ioException) {
				ioException.printStackTrace();
			}
		});
		progressBar.setString("Finished export!");
		progressBar.setValue(progressBar.getMaximum());
		System.out.println("Export finished");
	}

	void addSongToSynch(PreSynchedSong song) {
		songsToSynch.add(song);
	}

	boolean containsSongToSynch(PreSynchedSong song) {
		return songsToSynch.contains(song);
	}

	void prepareProgressBarForSynchingSongs() {
		if(ytPlaylistSourcesCrawlingDone) {
			progressBar.setString(songsToSynch.isEmpty() ? "Finished synching songs" : "Click to synch " + songsToSynch.size() + " songs");
			if(songsToSynchBarMouseAdapter == null || !Arrays.asList(progressBar.getMouseListeners()).contains(songsToSynchBarMouseAdapter))
				progressBar.addMouseListener(songsToSynchBarMouseAdapter = new SongsToSynchBarMouseAdapter(progressBar, songsToSynch));
		} else
			progressBar.setString("Added to synching queue, please wait for indexing YouTube Playlist Sources...");
	}

	void updateAddNewSongsChannelList(NewSongsChannel c) {
		DefaultListModel<String> model = (DefaultListModel<String>) newSongsChannelList.getModel();
		for(int i = 0; i < model.size(); i++)
			if(model.getElementAt(i).startsWith(c.getName() + " from "))
				model.set(i, c.getName() + " from " + c.getFrom() + " to " + c.getTo() + " = " + c.getSize());
	}

	void refreshAddNewSongsChannelList() {
		if(addNewSongsChannelButton.isEnabled())
			new Thread(() -> {
				refreshButton.setEnabled(false);
				addNewSongsChannelButton.setEnabled(false);
				playNewSongsButton.setEnabled(false);
				interruptButton.setEnabled(true);
				refreshButton.setFont(refreshButton.getFont().deriveFont(Font.PLAIN));
				ytNewSongsChannels.parallelStream().forEach(NewSongsChannel::refresh);
				if(ytNewSongsChannels.stream().anyMatch(NewSongsChannel::isInterrupted))
					refreshButton.setFont(refreshButton.getFont().deriveFont(Font.ITALIC));
				addNewSongsChannelButton.setEnabled(true);
				refreshButton.setEnabled(true);
				playNewSongsButton.setEnabled(true);
				interruptButton.setEnabled(false);
			}).start();
	}
}
