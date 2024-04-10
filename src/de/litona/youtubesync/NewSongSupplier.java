package de.litona.youtubesync;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;

public class NewSongSupplier {

	private PreSynchedSong song;
	private String url;

	public NewSongSupplier(PreSynchedSong song, boolean initialDownloadToTemp) throws FileNotFoundException {
		if(!initialDownloadToTemp) {
			this.song = song;
			try {
				Process p = Runtime.getRuntime().exec("yt-dlp --get-url -f b https://www.youtube.com/watch?v=" + song.getYtId());
				try(BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
					String line;
					while((line = reader.readLine()) != null)
						if(line.startsWith("http")) {
							url = new URL(line).toURI().toString();
							return; // if this line is reached, everything went well, backfall = download see 2 last lines
						}
				} catch(IOException | URISyntaxException e) {
					e.printStackTrace();
				}
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
		this.song = new SynchedSong(song.getYtId(), song.getYtTitle(), song.getTags(), song.getYtTitle(), "", "", 0, true);
		this.url = ((SynchedSong) this.song).getFile().toURI().toString();
	}

	public String startAndGetYtTitle() {
		if(url != null && !url.isEmpty()) {
			GUI.mediaPlayer = new MediaPlayer(new Media(url));
			GUI.mediaPlayer.setOnEndOfMedia(() -> {
				GUI.mediaPlayer.seek(javafx.util.Duration.millis(10));
				GUI.mediaPlayer.play();
			});
			GUI.mediaPlayer.play();
			return song.getYtTitle();
		}
		return "";
	}

	public boolean finishPlayingThenIsCancel() {
		try(WatchService watchService = FileSystems.getDefault().newWatchService()) {
			GUI.configurationFile.getParentFile().toPath().register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
			// Infinite loop to wait for events
			loop:
			while(true) {
				WatchKey key = watchService.take(); // Retrieves and removes next watch key, waiting if none are yet present.
				for(WatchEvent<?> event : key.pollEvents())
					if(event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
						File commandfile = ((Path) event.context()).toFile();
						switch(commandfile.getName()) {
							case "pauseplay.ytsc":
								if(GUI.mediaPlayer.getStatus() == MediaPlayer.Status.PAUSED)
									GUI.mediaPlayer.play();
								else
									GUI.mediaPlayer.pause();
								break;
							case "save.ytsc":
								GUI.getGui().addSongToSynch(song);
								GUI.getGui().prepareProgressBarForSynchingSongs();
								break;
							case "10forward.ytsc":
								Duration currentTime = GUI.mediaPlayer.getCurrentTime();
								System.out.println(
									GUI.mediaPlayer.getTotalDuration().toSeconds() + " " + GUI.mediaPlayer.getTotalDuration().subtract(currentTime)
										.toSeconds() + " " + (GUI.mediaPlayer.getTotalDuration().subtract(currentTime).toSeconds() < 30));
								if(GUI.mediaPlayer.getTotalDuration().subtract(currentTime).toSeconds() < 30) {
									GUI.mediaPlayer.seek(currentTime.add(Duration.seconds(30)));
									GUI.mediaPlayer.play();
								}
								break;
							case "discard.ytsc":
								GUI.mediaPlayer.stop();
								if(!GUI.getGui().containsSongToSynch(song)) {
									NewSongsChannel channel = song.getNewSongsChannel();
									if(channel != null)
										channel.songPlayed(song);
								}
								break loop;
							case "10reverse.ytsc":
								GUI.mediaPlayer.seek(GUI.mediaPlayer.getCurrentTime().subtract(Duration.seconds(10)));
								GUI.mediaPlayer.play();
								break;
							case "startover.ytsc":
								GUI.mediaPlayer.getOnEndOfMedia().run();
								break;
						}
					}
				if(!key.reset()) // Reset the key, if directory is deleted, we can abort here
					throw new IOException("Error with config directory where hotkeys are listened for");
			}
		} catch(IOException | InterruptedException e) {
			e.printStackTrace();
			GUI.mediaPlayer.stop();
		}
		return false;
	}
}
