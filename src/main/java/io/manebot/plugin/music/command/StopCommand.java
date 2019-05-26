package io.manebot.plugin.music.command;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.CommandExecutor;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginRegistration;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.channel.AudioChannel;

public class StopCommand implements CommandExecutor {
    private final PluginRegistration pluginRegistration;

    public StopCommand(Plugin.Future future) {
        this.pluginRegistration = future.getRegistration();
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) throws CommandExecutionException {
        Audio audio = pluginRegistration.getInstance().getInstance(Audio.class);
        AudioChannel channel = audio.requireListening(sender);

        audio.requireListening(sender);

        if (channel.isIdle()) throw new CommandArgumentException("Cannot stop this channel.");
    }

    @Override
    public String getDescription() {
        return "Stops playing tracks";
    }
}
