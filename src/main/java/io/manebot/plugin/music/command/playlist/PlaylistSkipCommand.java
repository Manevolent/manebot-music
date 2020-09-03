package io.manebot.plugin.music.command.playlist;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandAccessException;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.CommandExecutor;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.player.AudioPlayer;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.playlist.Playlist;
import io.manebot.security.Permission;

public class PlaylistSkipCommand implements CommandExecutor {
    private final Music music;

    public PlaylistSkipCommand(Music music) {
        this.music = music;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) throws CommandExecutionException {
        Audio audio = music.getAudio();
        AudioChannel channel = audio.requireListening(sender);
        if (channel.isIdle()) throw new CommandArgumentException("Cannot skip this channel.");

        Playlist playlist = music.getPlaylist(channel);
        if (playlist == null) {
            if (music.getQueue(channel).isEmpty()) {
                throw new CommandArgumentException("There are no tracks in the queue.");
            }
        } else if (playlist.isRunning() && playlist.peek() == playlist.getCurrent()) {
            throw new CommandArgumentException("Cannot skip this playlist.");
        }

        boolean override = Permission.hasPermission("audio.stop.all");
        int stopped = 0;
        for (AudioPlayer player : channel.getPlayers()) {
            if (!player.isPlaying()) continue;

            if (player.getOwner() == null || player.getOwner().equals(sender.getUser()) || override)
                if (player.stop()) stopped++;
        }

        if (stopped <= 0)
            throw new CommandAccessException("None of your messages are playing.");
    }

    @Override
    public String getDescription() {
        return "Stops playing tracks";
    }
}
