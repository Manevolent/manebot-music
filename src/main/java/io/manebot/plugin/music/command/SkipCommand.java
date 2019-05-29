package io.manebot.plugin.music.command;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandAccessException;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.CommandExecutor;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.music.Music;

public class SkipCommand implements CommandExecutor {
    private final Music music;

    public SkipCommand(Music music) {
        this.music = music;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) throws CommandExecutionException {
        Audio audio = music.getAudio();
        AudioChannel channel = audio.requireListening(sender);
        if (channel.isIdle()) throw new CommandArgumentException("Cannot stop this channel.");

        int stopped = music.stop(sender.getPlatformUser().getAssociation(), channel);
        if (stopped <= 0) {
            throw new CommandAccessException("None of your messages are playing.");
        } else {
            if (stopped == 1)
                sender.sendMessage("Stopped " + stopped + " track.");
            else
                sender.sendMessage("Stopped " + stopped + " tracks.");
        }
    }

    @Override
    public String getDescription() {
        return "Stops playing tracks";
    }
}
