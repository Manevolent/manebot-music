package io.manebot.plugin.music.database.model;

import io.manebot.database.Database;
import io.manebot.database.model.Platform;
import io.manebot.database.model.TimedRow;
import io.manebot.database.model.User;

import javax.persistence.*;
import java.net.URL;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@javax.persistence.Entity
@Table(
        uniqueConstraints = {@UniqueConstraint(columnNames ={"name"})}
)
public class Community extends TimedRow {
    @Transient
    private final Database database;

    public Community(Database database) {
        this.database = database;
    }

    public Community(Database database, String name, TrackRepository trackRepository) {
       this(database);

       this.name = name;
       this.repository = trackRepository;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int communityId;

    @Column(length = 64, nullable = false)
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "repositoryId")
    private TrackRepository repository;

    public int getCommunityId() {
        return communityId;
    }

    public long countTracks() {
        return database.execute(s -> (Long) s.createQuery(
                "SELECT COUNT(x) FROM " + Track.class.getName() + " x " +
                        "INNER JOIN x.community c " +
                        "WHERE c.communityId = :communityId"
        ).setParameter("communityId", getCommunityId()).getSingleResult());
    }

    /**
     * Finds a track, allowing for a slash to be contained within the URL.
     * @param url URL to find.
     * @return Track, if one is available.
     */
    public Track findTrack(URL url) {
        return findTrack(url.toExternalForm());
    }

    /**
     * Finds a track, allowing for a slash to be contained within the URL.
     * @param urlString URL string to find.
     * @return Track, if one is available.
     */
    public Track findTrack(String urlString) {
        Set<String> urlStrings = Stream.of(urlString)
                .flatMap(x -> Stream.of(x, trimTrailingSlashes(x), appendTrailingSlash(x)))
                .collect(Collectors.toSet());
        if (urlStrings.size() <= 0) {
            return null;
        }

        Track track = getFirstTrack(urlStrings);
        if (track != null && track.isDeleted()) {
            throw new IllegalArgumentException("Track was deleted.");
        }

        return track;
    }

    public Track getFirstTrack(Set<String> urlStrings) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + Track.class.getName() + " x " +
                            "INNER JOIN x.community c " +
                            "WHERE c.communityId = :communityId AND x.url IN :urls",
                    Track.class
            ).setParameter("communityId", communityId).setParameter("urls", urlStrings)
                    .getResultList()
                    .stream()
                    .findFirst()
                    .orElse(null);
        });
    }

    public Track getOrCreateTrack(URL url, Consumer<Track.Builder> constructor) {
        Track track = findTrack(url);
        if (track != null) {
            return track; // shortcut
        } else {
            Track.Builder builder = new Track.DefaultBuilder(this, null, url);
            constructor.accept(builder);

            try {
                track = database.executeTransaction(s -> {
                    Track newTrack = new Track(
                            database,
                            builder.getUrl(),
                            builder.getCommunity(),
                            builder.getLength(),
                            builder.getName(),
                            builder.getUser()
                    );

                    s.persist(newTrack);

                    for (String tagName : builder.getTags()) {
                        Tag tag = s.createQuery(
                                "SELECT x FROM " + Tag.class.getName() + " x " +
                                "WHERE x.name = :name",
                                Tag.class
                        ).setParameter("name", tagName).setMaxResults(1)
                                .getResultStream().findFirst().orElse(null);

                        if (tag == null) {
                            tag = new Tag(database, tagName, builder.getUser());
                            s.persist(tag);
                        }

                        s.persist(new TrackTag(database, newTrack, tag, builder.getUser()));
                    }

                    return newTrack;
                });
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            return track;
        }
    }

    public Collection<CommunityAssociation> getAssociations() {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + CommunityAssociation.class.getName() + " x " +
                            "INNER JOIN x.community c " +
                            "WHERE c.communityId = :communityId", CommunityAssociation.class)
                    .setParameter("communityId", getCommunityId())
                    .getResultList();
        });
    }

    public CommunityAssociation getAssociation(Platform platform, String id) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + CommunityAssociation.class.getName() + " x " +
                            "INNER JOIN x.platform p " +
                            "WHERE p.platformId = :platformId AND x.id = :id", CommunityAssociation.class)
                    .setParameter("platformId", platform.getPlatformId())
                    .setParameter("id", id)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
        });
    }

    public CommunityAssociation createAssociation(Platform platform, String id) {
        try {
            return database.executeTransaction(s -> {
                CommunityAssociation association = new CommunityAssociation(database, this, platform, id);
                s.persist(association);
                return association;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public TrackRepository getRepository() {
        return repository;
    }

    public void setRepository(TrackRepository repository) {
        try {
            this.repository = database.executeTransaction(s -> {
                Community community = s.find(Community.class, getCommunityId());
                community.repository = repository;
                community.setUpdated(System.currentTimeMillis());
                return repository;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        try {
            this.name = database.executeTransaction(s -> {
                Community community = s.find(Community.class, getCommunityId());
                community.name = name;
                community.setUpdated(System.currentTimeMillis());
                return name;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete() {

        try {
            database.executeTransaction(s -> {
                s.remove(s.find(Community.class, getCommunityId()));
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static String trimTrailingSlashes(String urlString) {
        while (urlString.endsWith("/") && urlString.length() > 1) {
            urlString = urlString.substring(0, urlString.length() - 1);
        }
        return urlString;
    }

    public static String appendTrailingSlash(String urlString) {
        if (!urlString.endsWith("/")) {
            urlString += "/";
        }
        return urlString;
    }
}
