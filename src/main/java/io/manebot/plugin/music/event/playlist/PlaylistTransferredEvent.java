package io.manebot.plugin.music.event.playlist;

import io.manebot.plugin.music.*;
import io.manebot.plugin.music.database.model.*;
import io.manebot.plugin.music.playlist.*;
import io.manebot.user.*;

public class PlaylistTransferredEvent extends PlaylistEvent {
    private final UserAssociation oldUser;
    private final UserAssociation newUser;
    
    public PlaylistTransferredEvent(Object sender, Music music, Playlist playlist, UserAssociation oldUser, UserAssociation newUser) {
	super(sender, music, playlist);
	this.oldUser = oldUser;
	this.newUser = newUser;
    }
    
    public UserAssociation getOldUser() {
	return oldUser;
    }
    
    public UserAssociation getNewUser() {
	return newUser;
    }
}
