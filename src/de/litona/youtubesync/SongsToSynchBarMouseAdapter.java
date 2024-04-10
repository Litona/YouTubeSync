package de.litona.youtubesync;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Queue;

public class SongsToSynchBarMouseAdapter extends MouseAdapter {

	private final JProgressBar progressBar;
	private final Queue<PreSynchedSong> songsToSynch;

	public SongsToSynchBarMouseAdapter(JProgressBar progressBar, Queue<PreSynchedSong> songsToSynch) {
		this.progressBar = progressBar;
		this.songsToSynch = songsToSynch;
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		progressBar.removeMouseListener(this);
		progressBar.setString("");
		progressBar.setValue(0);
		if(!songsToSynch.isEmpty())
			new Thread(() -> {
				SongGUI openGui;
				SongGUI nextGui;
				int numberOfSongs = songsToSynch.size();
				int myIndex = 0;
				nextGui = new SongGUI(songsToSynch.remove(), numberOfSongs, myIndex++); // prepare first GUI (means loading yt Information from URL)
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
			}).start();
	}
}
