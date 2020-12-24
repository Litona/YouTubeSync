package de.litona.youtubesync;

import com.mpatric.mp3agic.*;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;

public class SynchedSong extends PreSynchedSong implements Comparable<SynchedSong> {

	private static final SimpleDateFormat yearDisplayFormat = new SimpleDateFormat("yyyy");

	private File file;
	private final long added;
	private final long uploaded;
	private String simpleTitle;
	private String interpret;
	private String year;
	private boolean hasThumbnail;
	private ImageIcon scaledThumbnail;
	private int classificationLevel = 0;

	/* Used for internal purposes
	SynchedSong(Elevator.DownloadedSong d) {
		super(d.ytId, d.nameCropped, d.playlists);
		file = d.file;
		added = d.addedArchive;
		if(added < 100)
			System.out.println(ytId + "." + ytTitle);
		uploaded = d.uploaded;
		downloadThumbnail();
	}*/

	public SynchedSong(JSONObject json) {
		super(json.getString("ytId"),
			json.has("ytTitle") ? json.getString("ytTitle") : (json.has("nameCropped") ? json.getString("nameCropped") : cropName(json.getString("file"))),
			json.getJSONArray("tags"));
		file = new File(json.getString("file"));
		added = json.getLong("added");
		uploaded = json.getLong("uploaded");
		hasThumbnail = json.getBoolean("hasThumbnail");
		if(json.has("simpleTitle"))
			simpleTitle = json.getString("simpleTitle");
		if(json.has("interpret"))
			interpret = json.getString("interpret");
		if(json.has("year"))
			year = json.getString("year");
		if(json.has("classificationLevel"))
			classificationLevel = json.getInt("classificationLevel");
	}

	public SynchedSong(String ytId, String ytTitle, Collection<String> tags, String simpleTitle, String interpret, String year, long uploaded)
		throws FileNotFoundException {
		super(ytId, ytTitle, tags);
		this.added = System.currentTimeMillis();
		this.uploaded = uploaded;
		this.simpleTitle = simpleTitle;
		this.interpret = interpret;
		this.year = year;
		try {
			new ProcessBuilder("youtube-dl", "https://www.youtube.com/watch?v=" + ytId, "-o",
				"\"" + GUI.getSongsFolder().getPath() + "\\pre" + added + "#%(title)s.%(id)s.%(ext)s\"", "-x", "--audio-format", "mp3",
				"--restrict-filenames", "--age-limit", "99", "--exec", "\"mp3gain -r -c {}\"").inheritIO().start().waitFor();
			file = Arrays.stream(GUI.getSongsFolder().listFiles())
				.filter(f -> f.getName().startsWith("pre" + added) && f.getName().endsWith(ytId + ".mp3")).findAny()
				.orElseThrow(FileNotFoundException::new);
			downloadThumbnail();
		} catch(InterruptedException | IOException e) {
			e.printStackTrace();
		}
	}

	public File getFile() {
		return file;
	}

	public long getAdded() {
		return added;
	}

	public long getUploaded() {
		return uploaded;
	}

	public boolean hasSimpleTitle() {
		return simpleTitle != null && !simpleTitle.isEmpty();
	}

	public String getSimpleTitle() {
		return hasSimpleTitle() ? simpleTitle : getYtTitle();
	}

	public void setSimpleTitle(String simpleTitle) {
		this.simpleTitle = simpleTitle;
	}

	public String getInterpret() {
		return interpret == null ? "" : interpret;
	}

	public void setInterpret(String interpret) {
		this.interpret = interpret;
	}

	public boolean hasYear() {
		return year != null && !year.isEmpty();
	}

	public String getYear() {
		return hasYear() ? year : (uploaded > 0 ? yearDisplayFormat.format(uploaded) : "");
	}

	public void setYear(String year) {
		this.year = year;
	}

	public int getClassificationLevel() {
		return classificationLevel;
	}

	public void setClassificationLevel(int classificationLevel) {
		this.classificationLevel = classificationLevel;
	}

	public ImageIcon getThumbnail() {
		if(hasThumbnail)
			try {
				Mp3File mp3File = new Mp3File(file);
				if(mp3File.hasId3v2Tag())
					return new ImageIcon(mp3File.getId3v2Tag().getAlbumImage());
			} catch(IOException | UnsupportedTagException | InvalidDataException e) {
				e.printStackTrace();
			}
		return null;
	}

	public ImageIcon getScaledThumbnail() {
		return (scaledThumbnail == null && hasThumbnail) ?
			(scaledThumbnail = new ImageIcon(getThumbnail().getImage().getScaledInstance(112, 63, Image.SCALE_FAST))) :
			null;
	}

	private void downloadThumbnail() {
		File downloadedThumbnail = new File(System.getProperty("user.dir"), ytId + ".jpeg");
		File old = file;
		File out = new File(file.getParentFile(), file.getName().substring(3));
		try {
			System.out.println("+++Searching Thumbnail for " + ytTitle + "." + ytId);
			Process p = new ProcessBuilder("youtube-dl", "https://www.youtube.com/watch?v=" + ytId, "--get-thumbnail", "--age-limit", "99").start();
			try(BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
				String line;
				boolean first = true;
				while((line = in.readLine()) != null) {
					System.out.println(line);
					if(first) {
						ImageIO.write(ImageIO.read(new URL(line)), "JPEG", downloadedThumbnail);
						first = false;
					}
				}
			}
			p.waitFor();
			if(downloadedThumbnail.exists())
				try(RandomAccessFile raf = new RandomAccessFile(downloadedThumbnail, "r")) {
					System.out.println("+++Adding Thumbnail for " + ytTitle + "." + ytId);
					Mp3File mp3File = new Mp3File(file);
					ID3v2 tag;
					if(mp3File.hasId3v2Tag())
						tag = mp3File.getId3v2Tag();
					else
						mp3File.setId3v2Tag(tag = new ID3v24Tag());
					byte[] bytes = new byte[(int) raf.length()];
					raf.read(bytes);
					tag.setAlbumImage(bytes, "image/jpeg");
					mp3File.save((file = out).toString());
					hasThumbnail = true;
				}
			else
				try {
					System.out.println("-!-!-!-NO Thumbnail for " + ytTitle + "." + ytId);
					Files.copy(file.toPath(), (file = out).toPath());
				} catch(IOException e) {
					e.printStackTrace();
				}
		} catch(InterruptedException | IOException | InvalidDataException | UnsupportedTagException | NotSupportedException e) {
			e.printStackTrace();
		} finally {
			if(downloadedThumbnail != null && downloadedThumbnail.exists())
				downloadedThumbnail.delete();
			if(old != null && old.exists())
				old.delete();
		}
	}

	public Object[] getJTableData(int index, boolean thumbnail) {
		return new Object[] {index, // index
			thumbnail ? getScaledThumbnail() : new ImageIcon(), // thumbnail
			getInterpret(), //interpret
			getSimpleTitle().replaceAll("_", " "), // title
			String.join(", ", getTags()), // tags
			getYear()}; // year
	}

	public JSONObject toJson(boolean relativeFilePath) throws FileNotFoundException {
		JSONObject json = new JSONObject();
		if(file == null)
			throw new FileNotFoundException();
		json.put("file", relativeFilePath ? file.getName() : file.getPath());
		json.put("ytTitle", ytTitle);
		json.put("ytId", ytId);
		json.put("simpleTitle", simpleTitle);
		json.put("interpret", interpret);
		json.put("year", year);
		json.put("tags", tags);
		json.put("added", added);
		json.put("uploaded", uploaded);
		json.put("hasThumbnail", hasThumbnail);
		json.put("classificationLevel", classificationLevel);
		return json;
	}

	@Override
	public int compareTo(SynchedSong o) {
		if(ytId.compareTo(o.ytId) == 0)
			return 0;
		else
			return Long.compare(added, o.added);
	}

	public double similarTo(SynchedSong o) {
		return similarTo(o.getInterpret(), o.getSimpleTitle());
	}

	public double similarTo(String interpret, String title) {
		return similarTo(this.getInterpret().trim().toLowerCase(), this.getSimpleTitle().trim().toLowerCase().replaceAll("(remix)|(bootleg)|(mashup)", ""),
			interpret.trim().toLowerCase(), title.trim().toLowerCase().replaceAll("(remix)|(bootleg)|(mashup)", ""));
	}

	public static double similarTo(String interpret1, String title1, String interpret2, String title2) {
		double simTitles = diceCoefficientOptimized(title1, title2);
		double simInterpret = diceCoefficientOptimized(interpret1, interpret2);
		double simBoth = diceCoefficientOptimized(interpret1 + "@" + title1, interpret2 + "@" + title2);
		return ((simTitles * 2d) + simInterpret + simBoth) / 4d;
	}

	/**
	 * Here's an optimized version of the dice coefficient calculation. It takes
	 * advantage of the fact that a bigram of 2 chars can be stored in 1 int, and
	 * applies a matching algorithm of O(n*log(n)) instead of O(n*n).
	 * <p>Note that, at the time of writing, this implementation differs from the
	 * other implementations on this page. Where the other algorithms incorrectly
	 * store the generated bigrams in a set (discarding duplicates), this
	 * implementation actually treats multiple occurrences of a bigram as unique.
	 * The correctness of this behavior is most easily seen when getting the
	 * similarity between "GG" and "GGGGGGGG", which should obviously not be 1.
	 *
	 * @param s The first string
	 * @param t The second String
	 * @return The dice coefficient between the two input strings. Returns 0 if one
	 * 	or both of the strings are {@code null}. Also returns 0 if one or both
	 * 	of the strings contain less than 2 characters and are not equal.
	 * @author Jelle Fresen
	 */
	static double diceCoefficientOptimized(String s, String t) {
		// Verifying the input:
		if(s == null || t == null)
			return 0;
		// Quick check to catch identical objects:
		if(s == t)
			return 1;
		// avoid exception for single character searches
		if(s.length() < 2 || t.length() < 2)
			return 0;

		// Create the bigrams for string s:
		final int n = s.length() - 1;
		final int[] sPairs = new int[n];
		for(int i = 0; i <= n; i++)
			if(i == 0)
				sPairs[i] = s.charAt(i) << 16;
			else if(i == n)
				sPairs[i - 1] |= s.charAt(i);
			else
				sPairs[i] = (sPairs[i - 1] |= s.charAt(i)) << 16;

		// Create the bigrams for string t:
		final int m = t.length() - 1;
		final int[] tPairs = new int[m];
		for(int i = 0; i <= m; i++)
			if(i == 0)
				tPairs[i] = t.charAt(i) << 16;
			else if(i == m)
				tPairs[i - 1] |= t.charAt(i);
			else
				tPairs[i] = (tPairs[i - 1] |= t.charAt(i)) << 16;

		// Sort the bigram lists:
		Arrays.sort(sPairs);
		Arrays.sort(tPairs);

		// Count the matches:
		int matches = 0, i = 0, j = 0;
		while(i < n && j < m) {
			if(sPairs[i] == tPairs[j]) {
				matches += 2;
				i++;
				j++;
			} else if(sPairs[i] < tPairs[j])
				i++;
			else
				j++;
		}
		return (double) matches / (n + m);
	}

	private static String cropName(String path) {
		String fileName = new File(path).getName();
		System.out.println("Cropped name for " + fileName);
		return fileName.substring(14, fileName.length() - 16);
	}
}