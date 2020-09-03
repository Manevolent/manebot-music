package io.manebot.plugin.music.event.track;

import io.manebot.plugin.music.*;
import io.manebot.plugin.music.database.model.*;

public class TrackFadeEvent extends TrackEvent {
    private Track nextTrack;

    public TrackFadeEvent(Object sender, Music music, Track track, Track nextTrack) {
	    super(sender, music, track);

	    this.nextTrack = nextTrack;
    }

    public Track getNextTrack() {
        return nextTrack;
    }

    public void setNextTrack(Track nextTrack) {
        this.nextTrack = nextTrack;
    }
}
