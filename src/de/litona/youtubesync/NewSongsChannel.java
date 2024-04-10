package de.litona.youtubesync;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;

public class NewSongsChannel {

	private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");

	private Process process;
	private final Collection<PreSynchedSong> songs = new LinkedHashSet<>();
	private final Collection<PreSynchedSong> playedSongs = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final String name;
	private String url, from, to;
	private long fromTimestamp, toTimestamp;
	boolean interrupt, interrupted, running;

	public NewSongsChannel(JSONObject json) throws ParseException {
		this(json.getString("name"), json.getString("url"), json.getString("from"), json.getString("to"));
		json.getJSONArray("playedSongs")
			.forEach(j -> playedSongs.add(new PreSynchedSong(((JSONObject) j).getString("ytId"), "null", Collections.emptySet())));
	}

	public NewSongsChannel(String name, String url, String from, String to) throws ParseException {
		this.name = name;
		this.url = url;
		setFrom(from);
		setTo(to);
	}

	public NewSongsChannel(String name, String url) throws ParseException {
		this(name, url, "start", "now");
	}

	public void refresh() {
		running = true;
		interrupt = false;
		interrupted = false;
		try(BufferedReader reader = new BufferedReader(new InputStreamReader((process = Runtime.getRuntime().exec(
			"yt-dlp " + url + " --dump-json --ignore-errors --age-limit 99 --dateafter " + dateFormat.format(fromTimestamp) + " --datebefore " + dateFormat
				.format(toTimestamp))).getInputStream()))) {
			String line;
			while((line = reader.readLine()) != null && !interrupt) {
				JSONObject json = new JSONObject(line);
				String ytId = json.getString("id");
				int duration = json.getInt("duration");
				boolean notInPlayedSongs;
				synchronized(this) {
					notInPlayedSongs = playedSongs.stream().noneMatch(s -> s.getYtId().equals(ytId));
				}
				if(!url.contains("https://www.youtube.com/shorts/") && duration > 90 && notInPlayedSongs && GUI.songs.stream()
					.noneMatch(s -> s.getYtId().equals(ytId))) {
					synchronized(this) {
						songs.add(new PreSynchedSong(ytId, json.getString("title"), this));
					}
					GUI.getGui().updateAddNewSongsChannelList(this);
				}
			}
			interrupted = interrupt;
		} catch(IOException ex) {
			ex.printStackTrace();
		}
		running = false;
	}

	public synchronized JSONObject toJson() {
		JSONObject json = new JSONObject();
		json.put("name", name);
		json.put("url", url);
		json.put("from", from);
		json.put("to", to);
		JSONArray array = new JSONArray();
		playedSongs.stream().map(s -> {
			JSONObject newJson = new JSONObject();
			newJson.put("ytId", s.getYtId());
			return newJson;
		}).forEach(array::put);
		json.put("playedSongs", array);
		return json;
	}

	long toTimestamp(String in) throws ParseException {
		if(in.equals("now"))
			return System.currentTimeMillis();
		if(in.equals("start"))
			return 0;
		return dateFormat.parse(in).getTime();
	}

	public synchronized void songPlayed(PreSynchedSong song) {
		songs.remove(song);
		if(playedSongs.add(song)) // return true if the collection changed
			GUI.changesToConfiguration();
	}

	public synchronized Collection<PreSynchedSong> getSongs() {
		return running ? null : new LinkedHashSet<>(songs);
	}

	public synchronized int getSize() {
		return songs.size();
	}

	public boolean isInterrupted() {
		return interrupted;
	}

	public void interrupt() {
		interrupt = true;
		process.destroy();
	}

	public boolean isRunning() {
		return running;
	}

	public String getName() {
		return name;
	}

	public String getUrl() {
		return url;
	}

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) throws ParseException {
		fromTimestamp = toTimestamp(from);
		this.from = from;
	}

	public String getTo() {
		return to;
	}

	public void setTo(String to) throws ParseException {
		toTimestamp = toTimestamp(to);
		this.to = to;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return o == this || o instanceof NewSongsChannel && name.equals(((NewSongsChannel) o).name);
	}
}
