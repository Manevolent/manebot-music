package io.manebot.plugin.music.database.model;

import io.manebot.conversation.Conversation;
import io.manebot.database.Database;
import io.manebot.database.expressions.ExtendedExpressions;
import io.manebot.database.expressions.MatchMode;
import io.manebot.database.model.TimedRow;
import io.manebot.database.search.SearchArgument;
import io.manebot.database.search.SearchHandler;
import io.manebot.database.search.SearchOperator;
import io.manebot.database.search.handler.*;
import io.manebot.user.User;
import io.manebot.virtual.Virtual;

import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "communityId,url", unique = true),
                @Index(columnList = "name"),
                @Index(columnList = "length"),
                @Index(columnList = "likes"),
                @Index(columnList = "dislikes"),
                @Index(columnList = "plays"),
                @Index(columnList = "score"),
                @Index(columnList = "userId")
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"communityId","url"})}
)
public class Track extends TimedRow {
    @Transient
    private final Database database;

    public Track(Database database) {
        this.database = database;
    }

    public Track(Database database, URL url, Community community, Double length, String name, User user) {
        this(database);
        this.url = url.toExternalForm();
        this.community = community;
        this.length = length;
        this.name = name;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int trackId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "communityId")
    private Community community;

    @Column(length = 128, nullable = false)
    private String url;

    @Column(length = 128, nullable = false)
    private String name;

    @Column(nullable = true)
    private Double length;

    @Column(nullable = false)
    private int likes;

    @Column(nullable = false)
    private int dislikes;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private int plays;

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
            return URI.create(getUrlString()).toURL();
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

    public void delete() {
        throw new UnsupportedOperationException();
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

    /**
     * Creates a generic search handler for tracks.
     * @param database database to create the handler on.
     * @param community community to search by.
     * @return SearchHandler instance.
     */
    public static SearchHandler<Track> createSearch(Database database, final Community community) {
        return database.createSearchHandler(Track.class, (builder) -> {
            builder.string(new SearchHandlerPropertyContains((root) -> root.get("name")));

            builder.sort("date", root -> root.get("created")).defaultSort("date");
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
                    SearchOperator.MERGE
            ));

            builder.command(new SearchHandlerPropertyIn(
                    "trackId", /* trackId on Track */
                    root -> root.get("track").get("trackId"), /* Track.trackId in TrackTag.track.trackId */
                    TrackTag.class,
                    new SearchHandlerPropertyEquals(root -> root.get("tag").get("name")) /* WHERE TrackTag.tag.name = name */
            ));

            builder.always((clause) -> clause.addPredicate(SearchOperator.MERGE, clause.getCriteriaBuilder().equal(
                    clause.getRoot().get("community").get("communityId"),
                    community.getCommunityId()
            )));

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
