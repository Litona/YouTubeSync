package de.litona.youtubesync;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Stream;

public class SongTagFilter<M, I> extends RowFilter<M, I> {

	protected final String[] input;
	protected final Collection<SynchedSong> filteredSongs = new HashSet<>();

	public SongTagFilter(String input) {
		this.input = input.trim().toLowerCase().split("\\s+"); // user input is split by space -> array of tags
	}

	@Override
	public boolean include(Entry entry) {
		SynchedSong song = GUI.songs.get((Integer) entry.getValue(0) - 1);
		// stream user input (array by space separation)
		// all tags entered by user must be found in song
		boolean out = Stream.of(input).allMatch(in -> {
			// second stream of the tags found in the given song "entry"
			Stream<String> stream = song.getTags().stream().map(String::toLowerCase);
			// if tag "in" is excluded, it mustn't be found anywhere in the second stream
			return in.startsWith("-") ? stream.noneMatch(s -> s.contains(in.substring(1))) : stream.anyMatch(s -> s.contains(in));
		});
		if(out)
			filteredSongs.add(song);
		return out;
	}

	public Collection<SynchedSong> getResults() {
		return Collections.unmodifiableCollection(filteredSongs);
	}
}