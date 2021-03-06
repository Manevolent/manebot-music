package io.manebot.plugin.music.database.model;

import io.manebot.command.response.CommandListResponse;
import io.manebot.conversation.Conversation;
import io.manebot.database.Database;
import io.manebot.database.expressions.ExtendedExpressions;
import io.manebot.database.expressions.MatchMode;
import io.manebot.database.model.TimedRow;
import io.manebot.database.search.SearchArgument;
import io.manebot.database.search.SearchHandler;
import io.manebot.database.search.SearchOperator;
import io.manebot.database.search.SortOrder;
import io.manebot.database.search.handler.*;
import io.manebot.plugin.music.repository.*;
import io.manebot.user.User;
import io.manebot.virtual.Virtual;

import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "communityId,url", unique = true),
                @Index(columnList = "communityId,uuid", unique = true),
                @Index(columnList = "name"),
                @Index(columnList = "length"),
                @Index(columnList = "likes"),
                @Index(columnList = "dislikes"),
                @Index(columnList = "plays"),
                @Index(columnList = "score"),
                @Index(columnList = "userId"),
                @Index(columnList = "deleted")
        },
        uniqueConstraints = {
                @UniqueConstraint(columnNames ={"communityId","url"}),
                @UniqueConstraint(columnNames ={"communityId","uuid"})
        }
)
public class Track extends TimedRow {
    public static CommandListResponse.ListElementFormatter<Track> FORMATTER = (textBuilder, o) -> textBuilder
            .append("\"" + o.getName() + "\"")
            .append(" (")
            .appendUrl(o.getUrlString())
            .append(") (")
            .append(Integer.toString(o.getLikes() - o.getDislikes()))
            .append(" likes)");

    @Transient
    private final Database database;

    public Track(Database database) {
        this.database = database;
    }

    public Track(Database database, URL url, Community community, Double length, String name, User user) {
        this(database);

        this.uuid = Repository.toUUID(url);
        this.url = url.toExternalForm();
        this.community = community;
        this.length = length;
        this.name = name;

        if (user instanceof io.manebot.database.model.User)
            this.user = (io.manebot.database.model.User) user;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int trackId;
    
    @Column(columnDefinition = "BINARY(16)", nullable = false)
    private UUID uuid;

    @ManyToOne(optional = false)
    @JoinColumn(name = "communityId")
    private Community community;

    @Column(length = 128, nullable = false)
    private String url;

    @Column(length = 128, nullable = false)
    private String name;

    @Column(nullable = true)
    private Double length = null;

    @Column(nullable = false)
    private int likes;

    @Column(nullable = false)
    private int dislikes;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private int plays;

    @Column(nullable = false)
    private boolean deleted;

    @ManyToOne(optional = true)
    @JoinColumn(name = "userId")
    private io.manebot.database.model.User user;

    public int getTrackId() {
        return trackId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        try {
            this.name = database.executeTransaction(s -> {
                Track track = s.find(Track.class, getTrackId());
                track.name = name;
                track.setUpdated(System.currentTimeMillis());
                return name;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String getUrlString() {
        return url;
    }

    public URL toURL() {
        try {
            return new URL(getUrlString());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public Double getLength() {
        return length;
    }

    public void setLength(Double length) {
        try {
            this.length = database.executeTransaction(s -> {
                Track track = s.find(Track.class, getTrackId());
                track.length = length;
                track.setUpdated(System.currentTimeMillis());
                return length;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String getTimeSignature() {
        Double length = getLength();
        if (length == null) return "(unknown)";
        double durationD = length;
        long minutes = (long) Math.floor(durationD / 60D);
        long seconds = (long) Math.floor(durationD - (minutes * 60D));
        return formatTimeComponent(minutes) + ":" + formatTimeComponent(seconds);
    }

    public Community getCommunity() {
        return community;
    }

    public void setCommunity(Community community) {
        try {
            this.community = database.executeTransaction(s -> {
                Track track = s.find(Track.class, getTrackId());
                track.community = s.find(Community.class, community.getCommunityId());
                track.setUpdated(System.currentTimeMillis());
                return track.community;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        try {
            this.deleted = database.executeTransaction(s -> {
                Track track = s.find(Track.class, getTrackId());
                track.deleted = deleted;
                track.setUpdated(System.currentTimeMillis());
                return deleted;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int getLikes() {
        return likes;
    }

    public void setLikes(int likes) {
        try {
            this.likes = database.executeTransaction(s -> {
                Track track = s.find(Track.class, getTrackId());
                track.likes = likes;
                track.score = track.likes - track.dislikes;
                track.setUpdated(System.currentTimeMillis());
                return likes;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int getDislikes() {
        return dislikes;
    }

    public void setDislikes(int dislikes) {
        try {
            this.dislikes = database.executeTransaction(s -> {
                Track track = s.find(Track.class, getTrackId());
                track.dislikes = dislikes;
                track.score = track.likes - track.dislikes;
                track.setUpdated(System.currentTimeMillis());
                return dislikes;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int getPlays() {
        return plays;
    }

    public void setPlays(int plays) {
        try {
            this.plays = database.executeTransaction(s -> {
                Track track = s.find(Track.class, getTrackId());
                track.plays = plays;
                track.setUpdated(System.currentTimeMillis());
                return plays;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public TrackPlay addPlay(Conversation conversation, User user, double start, double end) {
        try {
            return database.executeTransaction(s -> {
                TrackPlay trackPlay = new TrackPlay(database, this, conversation, user, start, end);
                s.persist(trackPlay);

                Track track = s.find(Track.class, getTrackId());
                track.setUpdated(System.currentTimeMillis());
                track.plays ++;

                return trackPlay;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Future<TrackPlay> addPlayAsync(Conversation conversation, User user, double start, double end) {
        CompletableFuture<TrackPlay> future = new CompletableFuture<>();
        new Thread(() -> {
            try {
                future.complete(addPlay(conversation, user, start, end));
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        }).start();
        return future;
    }

    public io.manebot.database.model.User getUser() {
        return user;
    }

    public Collection<TrackTag> getTags() {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + TrackTag.class.getName() +" x " +
                            "WHERE x.track.trackId = :trackId",
                    TrackTag.class
            ).setParameter("trackId", getTrackId()).getResultList();
        });
    }

    public Collection<TrackFile> getFiles() {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + TrackFile.class.getName() +" x " +
                            "WHERE x.uuid = :uuid",
                    TrackFile.class
            ).setParameter("uuid", getUUID()).getResultList();
        });
    }

    @Override
    public int hashCode() {
        return getTrackId();
    }

    @Override
    public boolean equals(Object b) {
        return b instanceof Track && equals((Track)b);
    }

    public boolean equals(Track b) {
        return b != null && b.getTrackId() == getTrackId();
    }
    
    public UUID getUUID() {
        return uuid;
    }

    /**
     * Adds a series of tags to this track.
     * @param tags a distinct set of tags to add.
     * @return a set of track tags that were added to the track.
     */
    public Set<TrackTag> addTags(Set<Tag> tags) {
        try {
            io.manebot.database.model.User user = (io.manebot.database.model.User)
                    Objects.requireNonNull(Virtual.getInstance().currentUser(), "user");

            return database.executeTransaction(s -> {
                return tags.stream().map(tag -> {
                    TrackTag trackTag = s.createQuery(
                            "SELECT x FROM " + TrackTag.class.getName() +" x " +
                                    "WHERE x.track.trackId = :trackId " +
                                    "AND x.tag.tagId = :tagId " +
                                    "AND x.user.userId = :userId",
                            TrackTag.class
                    ).setParameter("trackId", getTrackId()).setParameter("tagId", tag.getTagId())
                            .setParameter("userId", user.getUserId())
                            .getResultStream().findFirst().orElse(null);

                    if (trackTag == null) {
                        trackTag = new TrackTag(database, this, tag, user);
                        s.persist(trackTag);
                    } else {
                        return null; // Don't return this tag instance as we didn't make it just now
                    }

                    return trackTag;
                }).filter(Objects::nonNull).collect(Collectors.toSet());
            });
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Removes a series of tags to this track.
     * @param tags a distinct set of tags to remove.
     * @return a set of track tags that were removed from the track.
     */
    public Set<TrackTag> removeTags(Set<Tag> tags) {
        io.manebot.database.model.User user = (io.manebot.database.model.User)
                Objects.requireNonNull(Virtual.getInstance().currentUser(), "user");

        try {
            return database.executeTransaction(s -> {
                return tags.stream().map(tag -> {
                    TrackTag trackTag = s.createQuery(
                            "SELECT x FROM " + TrackTag.class.getName() +" x " +
                                    "WHERE x.track.trackId = :trackId " +
                                    "AND x.tag.tagId = :tagId " +
                                    "AND x.user.userId = :userId",
                            TrackTag.class
                    ).setParameter("trackId", getTrackId()).setParameter("tagId", tag.getTagId())
                            .setParameter("userId", user.getUserId())
                            .getResultStream().findFirst().orElse(null);

                    if (trackTag != null) {
                        s.remove(trackTag);
                    }

                    return trackTag;
                }).filter(Objects::nonNull).collect(Collectors.toSet());
            });
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * The Builder is responsible for constructing a track.  This class is used by the TrackSource API implementors to
     * abstractly supply their source-specific metadata (e.g. YouTube tags) into a track instance during its
     * construction, or correct/normalize pre-determined fields such as the resource URL or track name.
     */
    public interface Builder {

        /**
         * Gets the track associated with this builder, if one exists yet.
         * @return track instance.
         */
        Track getTrack();

        /**
         * Gets the community associated with this new track.
         * @return Community.
         */
        Community getCommunity();

        /**
         * Gets the URL associated with this track builder.
         * @return URL.
         */
        URL getUrl();

        /**
         * Sets (or corrects) the URL.
         * @param url URL to set.
         * @return Builder instance for continuance.
         */
        Builder setUrl(URL url);

        /**
         * Gets the name associated with this track builder.
         * @return track name.
         */
        String getName();

        /**
         * Sets the track name.
         * @param name name to set.
         * @return Builder instance for continuance.
         */
        Builder setName(String name);

        /**
         * Gets the list of desired tags for the track.
         * @return desired track tags.
         */
        Collection<String> getTags();

        /**
         * Adds a tag to the track.
         * @param tag tag to add.
         * @return Builder instance for continuance.
         */
        Builder addTag(String tag);

        /**
         * Gets the length of the track, in seconds.
         * @return Track length, in seconds.
         */
        Double getLength();

        /**
         * Sets the length of the track, in seconds.
         * @param seconds track length, in seconds.
         * @return Builder instance for continuance.
         */
        Builder setLength(Double seconds);

        /**
         * Gets the user associated with downloading this track.
         * @return User instance.
         */
        User getUser();

        /**
         * Sets the user associated with downloading this track.
         * @param user user to set.
         * @return Builder instance for continuance.
         */
        Builder setUser(User user);

    }

    public static class DefaultBuilder implements Builder {
        private final Community community;
        private final Track track;
        private final Set<String> tags = new HashSet<>();
        private URL url;
        private Double length;
        private String name;
        private User user;

        public DefaultBuilder(Community community, Track track, URL url) {
            this.community = community;
            this.track = track;
            this.url = url;
            this.user = Virtual.getInstance().currentUser();
        }

        @Override
        public Track getTrack() {
            return track;
        }

        @Override
        public Community getCommunity() {
            return community;
        }

        @Override
        public URL getUrl() {
            return url;
        }

        @Override
        public Builder setUrl(URL url) {
            this.url = url;
            return this;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Builder setName(String name) {
            if (name == null || name.length() <= 0) throw new IllegalArgumentException("name");
            this.name = name;
            return this;
        }

        @Override
        public Collection<String> getTags() {
            return Collections.unmodifiableCollection(tags);
        }

        @Override
        public Builder addTag(String tag) {
            if (tag == null || tag.length() <= 0) throw new IllegalArgumentException("tag");
            tags.add(tag.toLowerCase());
            return this;
        }

        @Override
        public Double getLength() {
            return length;
        }

        @Override
        public Builder setLength(Double seconds) {
            if (seconds != null && seconds < 0D) throw new IllegalArgumentException("seconds");
            this.length = seconds;
            return this;
        }

        @Override
        public User getUser() {
            return user;
        }

        @Override
        public Builder setUser(User user) {
            this.user = user;
            return this;
        }
    }

    public static SearchHandler<Track> createSearch(Database database, final Community community) {
        return createSearch(database, community, (clause) -> {});
    }

    /**
     * Creates a generic search handler for tracks.
     * @param database database to create the handler on.
     * @param community community to search by.
     * @return SearchHandler instance.
     */
    public static SearchHandler<Track> createSearch(Database database, final Community community,
                                                    Consumer<SearchHandler.Clause<Track>> always) {
        return database.createSearchHandler(Track.class, (builder) -> {
            builder.string(new SearchHandlerPropertyContains((root) -> root.get("name")));

            builder.sort("date", root -> root.get("created")).defaultSort("date", SortOrder.DESCENDING);
            builder.sort("created", root -> root.get("created"));
            builder.sort("score", root -> root.get("score"));
            builder.sort("dislikes", root -> root.get("dislikes"));
            builder.sort("likes", root -> root.get("likes"));
            builder.sort("plays", root -> root.get("plays"));
            builder.sort("time", root -> root.get("length"));

            builder.argument("score", new SearchHandlerPropertyNumeric("score"));
            builder.argument("likes", new SearchHandlerPropertyNumeric("likes"));
            builder.argument("dislikes", new SearchHandlerPropertyNumeric("dislikes"));
            builder.argument("time", new SearchHandlerPropertyNumeric("length"));
            builder.argument("plays", new SearchHandlerPropertyNumeric("plays"));
            builder.argument("user", new ComparingSearchHandler(
                    new SearchHandlerPropertyEquals(root -> root.get("user").get("username")),
                    new SearchHandlerEntityProperty(root -> root.get("user").get("displayName")) {
                        @SuppressWarnings("unchecked")
                        @Override
                        protected Predicate handle(Path path, CriteriaBuilder criteriaBuilder, SearchArgument value) {
                            return ExtendedExpressions.escapedLike(
                                    criteriaBuilder,
                                    path,
                                    value.getValue(),
                                    MatchMode.START
                            );
                        }
                    },
                    SearchOperator.INCLUDE
            ));

            builder.command(new SearchHandlerPropertyIn(
                    "trackId", /* trackId on Track */
                    root -> root.get("track").get("trackId"), /* Track.trackId in TrackTag.track.trackId */
                    TrackTag.class,
                    new SearchHandlerPropertyEquals(root -> root.get("tag").get("name")) /* WHERE TrackTag.tag.name = name */
            ));

            builder.always((clause) -> {
                clause.addPredicate(SearchOperator.MERGE, clause.getCriteriaBuilder().equal(
                        clause.getRoot().get("community").get("communityId"),
                        community.getCommunityId()
                ));

                clause.addPredicate(SearchOperator.MERGE, clause.getCriteriaBuilder().equal(
                        clause.getRoot().get("deleted"),
                        false
                ));

                always.accept(clause);
            });

            return builder.build();
        });
    }


    private static String formatTimeComponent(long n) {
        assert n >= 0;

        if (n < 10)
            return "0" + n;
        else
            return Long.toString(n);
    }
}
