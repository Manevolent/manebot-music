package io.manebot.plugin.music.command.music;

import io.manebot.Bot;
import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentLabel;
import io.manebot.command.executor.chained.argument.CommandArgumentPage;
import io.manebot.command.executor.chained.argument.CommandArgumentString;
import io.manebot.platform.Platform;
import io.manebot.platform.PlatformConnection;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.CommunityAssociation;
import io.manebot.plugin.music.database.model.MusicManager;
import io.manebot.plugin.music.database.model.TrackRepository;

import java.util.stream.Collectors;

public class MusicCommunityCommand extends AnnotatedCommandExecutor {
    private final Music music;
    private final MusicManager musicManager;
    private final Bot bot;

    public MusicCommunityCommand(Music music, MusicManager musicManager, Bot bot) {
        this.music = music;
        this.musicManager = musicManager;
        this.bot = bot;
    }

    @Command(description = "Lists communities", permission = "music.community.list")
    public void list(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "list") String list,
                     @CommandArgumentPage.Argument() int page) throws CommandExecutionException {
        sender.sendList(
                Community.class,
                builder -> builder
                        .direct(musicManager.getCommunities())
                        .responder((textBuilder, community) -> textBuilder.append(community.getName()))
                        .page(page)
        );
    }

    @Command(description = "Deletes a community", permission = "music.community.delete")
    public void delete(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "delete") String delete,
                     @CommandArgumentString.Argument(label = "name") String name) throws CommandExecutionException {
        Community community = musicManager.getCommunityByName(name);
        if (community == null) throw new CommandArgumentException("Community not found.");

        for (CommunityAssociation association : community.getAssociations())
            association.delete();

        community.delete();
    }

    @Command(description = "Gets community information", permission = "music.community.info")
    public void info(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "info") String info) throws CommandExecutionException {
        Community community = music.getCommunity(sender);
        if (community == null) throw new CommandArgumentException("There is no community associated with this chat.");

        sender.sendDetails(builder -> builder
                .name("Community").key(community.getName())
                .item("Repository", community.getRepository() != null ? community.getRepository().getName() : "(none)")
                .item("Associations",
                        community.getAssociations().stream()
                                .map(assoc -> assoc.getPlatform().getId() + ":" + assoc.getId())
                                .collect(Collectors.toList())
                )
                .item("Tracks", community.countTracks())
                .item("Created", community.getCreatedDate())
                .item("Updated", community.getUpdatedDate())
        );
    }

    @Command(description = "Gets community information", permission = "music.community.info")
    public void info(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "info") String info,
                     @CommandArgumentString.Argument(label = "name") String name) throws CommandExecutionException {
        Community community = musicManager.getCommunityByName(name);
        if (community == null) throw new CommandArgumentException("Community not found.");

        sender.sendDetails(builder -> builder
                .name("Community").key(community.getName())
                .item("Repository", community.getRepository() != null ? community.getRepository().getName() : "(none)")
                .item("Associations",
                        community.getAssociations().stream()
                                .map(assoc -> assoc.getPlatform().getId() + ":" + assoc.getId())
                                .collect(Collectors.toList())
                )
                .item("Tracks", community.countTracks())
                .item("Created", community.getCreatedDate())
                .item("Updated", community.getUpdatedDate())
        );
    }

    @Command(description = "Renames a community", permission = "music.community.rename")
    public void rename(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "rename") String rename,
                       @CommandArgumentString.Argument(label = "name") String name,
                       @CommandArgumentString.Argument(label = "new") String newName) throws CommandExecutionException {
        Community community = musicManager.getCommunityByName(name);
        if (community == null) throw new CommandArgumentException("Community not found.");

        String oldName = community.getName();

        community.setName(newName);

        sender.sendMessage("Community \"" + oldName + "\" renamed to \"" + community.getName() + "\".");
    }

    @Command(description = "Sets a community's repository", permission = "music.repository.set")
    public void setRepository(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "set-repository") String setRepository,
                       @CommandArgumentString.Argument(label = "name") String name,
                       @CommandArgumentString.Argument(label = "repository") String newName) throws CommandExecutionException {
        Community community = musicManager.getCommunityByName(name);
        if (community == null) throw new CommandArgumentException("Community not found.");

        TrackRepository repository = musicManager.getTrackRepositoryByName(name);
        if (repository == null) throw new CommandArgumentException("Repository not found.");

        community.setRepository(repository);
        sender.sendMessage("Community \"" + community.getName() + "\" repository set to \"" + repository.getName() + "\".");
    }

    @Command(description = "Creates a community", permission = "music.community.create")
    public void create(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "create") String create,
                       @CommandArgumentString.Argument(label = "name") String name) throws CommandExecutionException {
        Community community = musicManager.getCommunityByName(name);
        if (community != null) throw new CommandArgumentException("A community already exists by that name.");

        community = musicManager.createCommunity(name);

        sender.sendMessage("Community \"" + community.getName() + "\" created.");
    }

    @Command(description = "Creates a community with a repository", permission = "music.community.create")
    public void create(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "create") String create,
                       @CommandArgumentString.Argument(label = "name") String name,
                       @CommandArgumentString.Argument(label = "repository") String repositoryId)
            throws CommandExecutionException {
        Community community = musicManager.getCommunityByName(name);
        if (community != null) throw new CommandArgumentException("A community already exists by that name.");

        TrackRepository repository = musicManager.getTrackRepositoryByName(repositoryId);
        if (repository == null) throw new CommandArgumentException("Repository not found.");

        community = musicManager.createCommunity(name, repository);

        sender.sendMessage("Community \"" + community.getName() + "\" created using repository \"" +
                repository.getName() + "\".");
    }

    @Command(description = "Associates a music community to a bot community",
            permission = "music.community.association.create")
    public void associate(CommandSender sender,
                          @CommandArgumentLabel.Argument(label = "associate") String associate,
                          @CommandArgumentString.Argument(label = "name") String communityName,
                          @CommandArgumentString.Argument(label = "platform") String platformId,
                          @CommandArgumentString.Argument(label = "id") String id)
            throws CommandExecutionException {
        Community community = musicManager.getCommunityByName(communityName);
        if (community == null) throw new CommandArgumentException("Community not found.");

        Platform platform = bot.getPlatformById(platformId);
        if (platform == null) throw new CommandArgumentException("Platform not found.");

        PlatformConnection connection = platform.getConnection();
        if (connection == null) throw new CommandArgumentException("Platform is not connected.");

        io.manebot.chat.Community platformCommunity = connection.getCommunity(id);
        if (platformCommunity == null) throw new CommandArgumentException("Community not found.");

        CommunityAssociation association = community.getAssociation(
                (io.manebot.database.model.Platform) platform,
                platformCommunity.getId()
        );
        if (association == null) {
            association = community.createAssociation(
                    (io.manebot.database.model.Platform) platform,
                    platformCommunity.getId()
            );
        }

        sender.sendMessage("Music community \"" + community.getName() + "\" associated to " +
                platform.getId() + " community \"" + platformCommunity.getId() + "\"."
        );
    }

    @Command(description = "Disassociates a music community from a bot community",
            permission = "music.community.association.remvove")
    public void disassociate(CommandSender sender,
                          @CommandArgumentLabel.Argument(label = "disassociate") String disassociate,
                          @CommandArgumentString.Argument(label = "name") String communityName,
                          @CommandArgumentString.Argument(label = "platform") String platformId,
                          @CommandArgumentString.Argument(label = "id") String id)
            throws CommandExecutionException {
        Community community = musicManager.getCommunityByName(communityName);
        if (community == null) throw new CommandArgumentException("Community not found.");

        Platform platform = bot.getPlatformById(platformId);
        if (platform == null) throw new CommandArgumentException("Platform not found.");

        CommunityAssociation association = community.getAssociation((io.manebot.database.model.Platform) platform, id);
        if (association == null) throw new CommandArgumentException("Association not found.");

        association.delete();

        sender.sendMessage("Music community \"" + community.getName() + "\" disassociated from " +
                platform.getId() + " community \"" + association.getId() + "\"."
        );
    }
}
