package io.manebot.plugin.music.command.track;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentString;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.database.model.Track;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class TrackPlayCommand extends AnnotatedCommandExecutor {
    private final Music music;

    public TrackPlayCommand(Music music) {
        this.music = music;
    }

    @Command(description = "Plays a track by its URL")
    public void play(CommandSender sender,
                     @CommandArgumentString.Argument(label = "URL") String urlString) throws CommandExecutionException {
        URL url;

        try {
            url = new URI(urlString).toURL();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new CommandArgumentException("The provided URL is malformed");
        }

        try {
            Track track = music.play(sender, null, url);
            sender.sendMessage("(Playing \"" + track.getName() + "\")");
        } catch (Exception e) {
            throw new CommandExecutionException(e);
        }
    }
}
