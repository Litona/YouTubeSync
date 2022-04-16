package de.litona.youtubesync;

import java.io.*;
import java.util.Collection;
import java.util.Scanner;
import java.util.TreeSet;

public final class Script {

	private static int newIndexOnline = 1;

	public static void main(String... args) {
		System.out.println("YouTubeSync in " + new File("D:\\Playlists"));
		for(File plDirs : new File("D:\\Playlists").listFiles())
			if(plDirs.isDirectory()) {
				String url = "";
				int lastIndexSaved = 0;
				Collection<String> savedEntries = new TreeSet<>();
				for(File f : plDirs.listFiles())
					if(f.isFile() && f.getName().endsWith(".mp3")) {
						savedEntries.add(f.getName().substring(f.getName().length() - 15, f.getName().length() - 4));
						lastIndexSaved++;
					} else if(f.isFile() && f.getName().endsWith(".playlist"))
						try(Scanner s = new Scanner(f)) {
							url = s.nextLine();
						} catch(FileNotFoundException e) {
							e.printStackTrace();
						}
				if(!url.isEmpty()) {
					System.out.println("\n#####\nFound good URL " + url + " in directory " + plDirs.getName());
					try(BufferedReader reader = new BufferedReader(
						new InputStreamReader(Runtime.getRuntime().exec("youtube-dl " + url + " -j --flat-playlist").getInputStream()))) {
						reader.lines().map(s -> s.split("\"id\": \"")[1].substring(0, 11)).filter(savedEntries::contains).forEach(s -> newIndexOnline++);
						System.out.println("Next index online: " + newIndexOnline + "; Next index on disk: " + ++lastIndexSaved);
						if(newIndexOnline >= 0)
							new ProcessBuilder("youtube-dl", url, "-o", "\"" + plDirs.getName() + "\\%(autonumber)s#%(title)s.%(id)s.%(ext)s\"", "-x",
								"--audio-format", "mp3", "--restrict-filenames", "--playlist-start", newIndexOnline + "", "--autonumber-start",
								lastIndexSaved + "", "--exec", "\"mp3gain -r -c {}\"").inheritIO().start().waitFor();
					} catch(IOException | InterruptedException e) {
						e.printStackTrace();
					}
					newIndexOnline = 1;
				}
			}
	}
}
