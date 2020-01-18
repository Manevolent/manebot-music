package io.manebot.plugin.music.event.playlist;

import io.manebot.plugin.music.*;
import io.manebot.plugin.music.playlist.*;

public class PlaylistEndedEvent extends PlaylistEvent {
    public PlaylistEndedEvent(Object sender, Music music, Playlist playlist) {
	super(sender, music, playlist);
    }
}
