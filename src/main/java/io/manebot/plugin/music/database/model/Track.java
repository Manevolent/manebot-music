package io.manebot.plugin.music.database.model;

import io.manebot.conversation.Conversation;
import io.manebot.database.Database;
import io.manebot.database.model.TimedRow;
import io.manebot.user.User;

import javax.persistence.*;
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
                @Index(columnList = "plays")
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"communityId","url"})}
)
public class Track extends TimedRow {
    @Transient
    private final Database database;

    public Track(Database database) {
        this.database = database;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int trackId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "communityId")
    private Community community;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String name;

    @Column(nullable = true)
    private Double length;

    @Column(nullable = false)
    private int likes;

    @Column(nullable = false)
    private int dislikes;

    @Column(nullable = false)
    private int plays;

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
                TrackPlay trackPlay = new TrackPlay(database);

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

    /**
     * The Builder is responsible for constructing a track.  This class is used by the TrackSource API implementors to
     * abstractly supply their source-specific metadata (e.g. YouTube tags) into a track instance during its
     * construction, or correct/normalize pre-determined fields such as the resource URL or track name.
     */
    public interface Builder {

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

    }

    public static class DefaultBuilder implements Builder {
        private final Community community;
        private final Set<String> tags = new HashSet<>();
        private URL url;
        private Double length;
        private String name;

        public DefaultBuilder(Community community, URL url) {
            this.community = community;
            this.url = url;
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
    }
}
