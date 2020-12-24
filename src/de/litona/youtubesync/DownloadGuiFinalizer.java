package de.litona.youtubesync;

import java.io.FileNotFoundException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

final class DownloadGuiFinalizer extends Thread {

	private static DownloadGuiFinalizer singleton;

	static DownloadGuiFinalizer singleton() {
		return singleton == null ? (singleton = new DownloadGuiFinalizer()) : singleton;
	}

	private final Queue<SongGUI> guis = new ConcurrentLinkedQueue<>();
	private int barMax = 0;
	private int barIndex = 0;

	private DownloadGuiFinalizer() {
		start();
	}

	@Override
	public void run() {
		while(true) {
			SongGUI gui;
			while((gui = guis.poll()) != null)
				try {
					GUI.getGui().getProgressBar().setValue(++barIndex);
					GUI.getGui().getProgressBar().setString("Downloading songs(" + barIndex + "/" + barMax + "), WAIT...");
					SynchedSong newSynchedSong = gui.getNewSynchedSong(); // finish user information and download song
					if(newSynchedSong != null && newSynchedSong.getFile() != null) // check if error occurred while downloading
						GUI.getGui().addSong(newSynchedSong);
				} catch(InterruptedException | FileNotFoundException e) {
					e.printStackTrace();
				}
			synchronized(this) {
				try {
					if(GUI.getGui().getProgressBar().getString().startsWith("Downloading"))
						GUI.getGui().getProgressBar().setString("Finished downloading songs!");
					wait();
				} catch(InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public boolean successGui(SongGUI gui) {
		try {
			if(!gui.isSuccess())
				return false;
			GUI.getGui().getProgressBar().setMaximum(++barMax);
			GUI.getGui().getProgressBar().setString("Downloading songs(" + barIndex + "/" + barMax + "), WAIT...");
			synchronized(this) {
				guis.add(gui);
				notifyAll();
			}
		} catch(InterruptedException interruptedException) {
			interruptedException.printStackTrace();
			return false;
		}
		return true;
	}
}
