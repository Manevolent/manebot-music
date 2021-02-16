package io.manebot.plugin.music.database.model;

import io.manebot.chat.Chat;
import io.manebot.conversation.Conversation;
import io.manebot.database.Database;
import io.manebot.database.model.Platform;
import io.manebot.plugin.music.repository.NullRepository;
import io.manebot.plugin.music.repository.Repository;
import io.manebot.user.User;
import io.manebot.virtual.Virtual;

import java.net.URL;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class MusicManager {
    private final Database database;

    public MusicManager(Database database) {
        this.database = database;
    }

    public Collection<Track> getTracksByURL(URL url) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + Track.class.getName() + " x " +
                    "WHERE x.url = :url", Track.class)
                    .setParameter("url", url.toExternalForm())
                    .getResultList();
        });
    }

    public TrackRepository getTrackRepositoryByName(String name) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + TrackRepository.class.getName() + " x " +
                    "WHERE x.name = :name", TrackRepository.class)
                    .setParameter("name", name)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
        });
    }

    public TrackRepository createTrackRepository(String name) {
        try {
            return database.executeTransaction(s -> {
                TrackRepository community = new TrackRepository(
                        database,
                        name,
                        NullRepository.class.getName(),
                        null,
                        null
                );

                s.persist(community);
                return community;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Community getCommunityByPlatformSpecificId(Platform platform, String id) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT c FROM " + CommunityAssociation.class.getName() + " x " +
                            "INNER JOIN x.community c " +
                            "INNER JOIN x.platform p " +
                            "WHERE p.platformId = :platformId AND x.id = :id", Community.class)
                    .setParameter("platformId", platform.getPlatformId())
                    .setParameter("id", id)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
        });
    }

    public Community getCommunityByName(String name) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + Community.class.getName() + " x " +
                    "WHERE x.name = :name", Community.class)
                    .setParameter("name", name)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
        });
    }

    public Community createCommunity(String name) {
        return createCommunity(name, Objects.requireNonNull(getTrackRepositoryByName("default")));
    }

    public Community createCommunity(String name, TrackRepository repository) {
        try {
            return database.executeTransaction(s -> {
                Community community = new Community(
                        database,
                        name,
                        s.find(TrackRepository.class, repository.getRepositoryId())
                );

                s.persist(community);
                return community;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Community> getCommunities() {
        return database.execute(s -> {
            return s.createQuery("SELECT x FROM " + Community.class.getName() + " x", Community.class).getResultList();
        });
    }

    public List<TrackRepository> getRepositories() {
        return database.execute(s -> {
            return s.createQuery("SELECT x FROM " + TrackRepository.class.getName() + " x", TrackRepository.class)
                    .getResultList();
        });
    }

    public Track getLastPlayed(Conversation conversation) {
        return getLastPlayed(conversation, 1, 300).stream().findFirst().orElse(null);
    }

    public List<Track> getLastPlayed(Conversation conversation, int maximum, int seconds) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT t FROM " + TrackPlay.class.getName() + " x " +
                            "INNER JOIN x.track t " +
                            "INNER JOIN x.conversation c " +
                            "WHERE c.conversationId = :conversationId AND x.created > :oldest " +
                            "ORDER BY x.end DESC", Track.class)
                    .setParameter(
                            "conversationId",
                            ((io.manebot.database.model.Conversation)conversation).getConversationId()
                    )
                    .setParameter(
                            "oldest",
                            Math.max(0, (int)((System.currentTimeMillis() - (seconds * 1000L)) / 1000))
                    )
                    .setMaxResults(maximum)
                    .getResultStream()
                    .collect(Collectors.toList());
        });
    }

    public Set<Tag> getOrCreateTags(Set<String> tagNames) {
        try {
            return database.executeTransaction(s -> {
                return tagNames.stream().map(tagName -> {
                    Tag tag = s.createQuery(
                            "SELECT x FROM " + Tag.class.getName() + " x " +
                                    "WHERE x.name = :name",
                            Tag.class
                    ).setParameter("name", tagName).setMaxResults(1)
                            .getResultStream().findFirst().orElse(null);

                    if (tag == null) {
                        tag = new Tag(database, tagName, Virtual.getInstance().currentUser());
                        s.persist(tag);
                    }

                    return tag;
                }).collect(Collectors.toSet());
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Set<Tag> getTags(Set<String> tagNames) {
        try {
            return database.executeTransaction(s -> {
                return tagNames.stream().map(tagName -> s.createQuery(
                        "SELECT x FROM " + Tag.class.getName() + " x " +
                                "WHERE x.name = :name",
                        Tag.class
                ).setParameter("name", tagName).setMaxResults(1).getResultStream().findFirst().orElse(null))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
