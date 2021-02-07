package de.litona.youtubesync;

import javax.swing.*;
import java.util.stream.Stream;

public class SimpleSearchTitleFilter<M, I> extends RowFilter<M, I> {

	private final String[] input;

	public SimpleSearchTitleFilter(String input) {
		this.input = input.trim().toLowerCase().split("\\s+");
	}

	@Override
	public boolean include(Entry entry) {
		String title = GUI.songs.get((Integer) entry.getValue(0) - 1).getSimpleTitle().toLowerCase();
		return Stream.of(input).allMatch(title::contains);
	}
}
