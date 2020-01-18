package io.manebot.plugin.music.event.playlist;

import io.manebot.plugin.music.*;
import io.manebot.plugin.music.event.*;
import io.manebot.plugin.music.playlist.*;

public abstract class PlaylistEvent extends MusicEvent {
    private final Playlist playlist;
    
    protected PlaylistEvent(Object sender, Music music, Playlist playlist) {
	super(sender, music);
	this.playlist = playlist;
    }
    
    public Playlist getPlaylist() {
	return playlist;
    }
}
