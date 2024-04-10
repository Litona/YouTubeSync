package de.litona.youtubesync;

import org.json.JSONArray;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeSet;

public class PreSynchedSong {

	protected final String ytId;
	protected String ytTitle;
	protected Collection<String> tags = new TreeSet<>(Comparator.comparing(String::toLowerCase));
	protected NewSongsChannel channel;

	protected PreSynchedSong(String ytId, String ytTitle, Collection<String> tags) {
		this.ytId = ytId;
		this.ytTitle = ytTitle;
		this.tags.addAll(tags);
	}

	protected PreSynchedSong(String ytId, String ytTitle, NewSongsChannel channel) {
		this(ytId, ytTitle, Collections.emptySet());
		this.channel = channel;
	}

	protected PreSynchedSong(String ytId, String ytTitle, JSONArray tags) {
		this.ytId = ytId;
		this.ytTitle = ytTitle;
		tags.toList().stream().map(String.class::cast).forEach(this.tags::add);
	}

	public String getYtId() {
		return ytId;
	}

	public String getYtTitle() {
		return ytTitle;
	}

	public Collection<String> getTags() {
		return Collections.unmodifiableCollection(tags);
	}

	public void setTags(Collection<String> tags) {
		this.tags.clear();
		addTags(tags);
	}

	public void addTags(Collection<String> tags) {
		this.tags.addAll(tags);
	}

	public void addTag(String tag) {
		this.tags.add(tag);
	}

	public void removeTag(String tag) {
		this.tags.remove(tag);
	}

	public void removeTags(Collection<String> tags) {
		this.tags.removeAll(tags);
	}

	PreSynchedSong addTagQuietly(String tag) {
		tags.add(tag);
		return this;
	}

	public NewSongsChannel getNewSongsChannel() {
		return channel;
	}

	@Override
	public int hashCode() {
		return ytId.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return o == this || o instanceof PreSynchedSong && ytId.equals(((PreSynchedSong) o).ytId);
	}
}
