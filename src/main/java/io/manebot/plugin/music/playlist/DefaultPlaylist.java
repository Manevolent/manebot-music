package io.manebot.plugin.music.playlist;

import io.manebot.conversation.Conversation;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.player.AudioPlayer;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.Play;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.Track;

import io.manebot.user.UserAssociation;

import java.util.*;

public class DefaultPlaylist implements Playlist {
    private final Music music;
    private final Community community;
    private final Conversation conversation;
    private final AudioChannel channel;
    private final TrackQueue queue;
    private final List<Listener> listeners;

    /**
     * A Set of active players on this playlist
     */
    private final Set<AudioPlayer> players = new HashSet<>();

    private UserAssociation userAssociation;
    private Track track;
    private boolean running = false;

    public DefaultPlaylist(Music music,
                           Community community,
                           Conversation conversation,
                           AudioChannel channel,
                           TrackQueue queue,
                           List<Listener> listeners) {
        this.music = music;
        this.community = community;
        this.conversation = conversation;
        this.channel = channel;
        this.queue = queue;
        this.listeners = listeners;
    }

    @Override
    public AudioChannel getChannel() {
        return channel;
    }

    @Override
    public Conversation getConversation() {
        return conversation;
    }

    @Override
    public Community getCommunity() {
        return community;
    }

    @Override
    public Track getCurrent() {
        return track;
    }

    @Override
    public TrackQueue getQueue() {
        return queue;
    }

    @Override
    public Collection<AudioPlayer> getPlayers() {
        return new ArrayList<>(players);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean setRunning(boolean running) {
        if (this.running != running) {
            this.running = running;

            if (running)
                listeners.forEach(listener -> listener.onStarted(this));
            else
                listeners.forEach(listener -> listener.onStopped(this));

            return true;
        }

        return false;
    }

    @Override
    public UserAssociation getUser() {
        return userAssociation;
    }

    @Override
    public void transferToUser(UserAssociation userAssociation) throws SecurityException {
        if (this.userAssociation != userAssociation) {
            this.userAssociation = userAssociation;
        }
    }

    @Override
    public Track next() throws NoSuchElementException, IllegalStateException {
        if (!isRunning())
            throw new IllegalStateException();

        if (!hasNext()) {
            setRunning(false);
            throw new NoSuchElementException();
        }

        try {
            this.track = Objects.requireNonNull(queue.next());

            Play play = music.play(getUser(), getConversation(), builder -> {
                builder.setExclusive(false); // important to not step on a fade-out
                builder.setFadeOut((track) -> next());
                builder.setTrack(selector -> selector.find(track));
            });

            players.add(play.getPlayer());
            play.getPlayer().getFuture().thenAccept(players::remove);

            listeners.forEach(listener -> listener.onTrackChanged(this, track));

            return play.getTrack();
        } catch (Exception e) {
            setRunning(false);
            throw new RuntimeException("Problem playing playlist track", e);
        }
    }

    @Override
    public boolean hasNext() {
        return queue.hasNext();
    }

    @Override
    public Track peek() {
        return queue.peek();
    }

    @Override
    public long size() {
        return queue.size();
    }
}
