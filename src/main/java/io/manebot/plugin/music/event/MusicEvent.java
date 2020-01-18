package io.manebot.plugin.music.event;

import io.manebot.event.*;
import io.manebot.plugin.music.*;

public abstract class MusicEvent extends Event {
    private final Music music;
    
    protected MusicEvent(Object sender, Music music) {
        super(sender);
        
	this.music = music;
    }
    
    public Music getMusic() {
	return music;
    }
}
