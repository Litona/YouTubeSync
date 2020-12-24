package de.litona.youtubesync;

import java.util.stream.Stream;

public class StrictSongTagFilter<M, I> extends SongTagFilter<M, I> {

	public StrictSongTagFilter(String input) {
		super(input);
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
			return in.startsWith("-") ? stream.noneMatch(s -> s.equals(in.substring(1))) : stream.anyMatch(s -> s.equals(in));
		});
		if(out)
			filteredSongs.add(song);
		return out;
	}
}
