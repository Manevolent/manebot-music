package io.manebot.plugin.music.event.track;

import io.manebot.plugin.music.*;
import io.manebot.plugin.music.database.model.*;

public class TrackFadeEvent extends TrackEvent {
    public TrackFadeEvent(Object sender, Music music, Track track) {
	super(sender, music, track);
    }
}
