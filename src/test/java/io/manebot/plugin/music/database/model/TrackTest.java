package io.manebot.plugin.music.database.model;

import io.manebot.command.CommandSender;
import io.manebot.database.Database;
import io.manebot.database.DatabaseManager;
import io.manebot.database.HibernateManager;
import io.manebot.database.model.Entity;
import io.manebot.database.model.EntityType;
import io.manebot.database.model.User;
import io.manebot.database.search.Search;
import io.manebot.database.search.SearchResult;
import io.manebot.user.UserType;
import io.manebot.virtual.DefaultVirtual;
import io.manebot.virtual.Virtual;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.*;

public class TrackTest {

    @Test
    public void testSearch_Track() throws SQLException, MalformedURLException {
        Properties properties = new Properties();
        properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        properties.put("hibernate.connection.url", "jdbc:h2:mem:myDb;DB_CLOSE_DELAY=-1");
        properties.put("hibernate.show_sql", "true");
        properties.put("hibernate.format_sql", "true");

        DatabaseManager databaseManager = new HibernateManager(null, properties);
        Database testDatabase =
                databaseManager.defineDatabase("test", builder ->
                        builder.registerEntity(Entity.class)
                                .registerEntity(User.class)
                                .registerEntity(TrackRepository.class)
                                .registerEntity(Community.class)
                                .registerEntity(Track.class)
                                .registerEntity(Tag.class)
                                .registerEntity(TrackTag.class)
                );

        Entity testEntity = testDatabase.executeTransaction(em -> {
            Entity entity = new Entity(testDatabase, EntityType.USER);
            em.persist(entity);
            return entity;
        });

        User testUser = testDatabase.executeTransaction(em -> {
            User user = new User(testDatabase, testEntity, "Test", UserType.SYSTEM);
            em.persist(user);
            return user;
        });

        TrackRepository testTrackRepository = testDatabase.executeTransaction(em -> {
            TrackRepository repository = new TrackRepository(testDatabase,
                    "Test", "Test", "Test", "Test");
            em.persist(repository);
            return repository;
        });

        Community testCommunity = testDatabase.executeTransaction(em -> {
            Community community = new Community(testDatabase, "Test", testTrackRepository);
            em.persist(community);
            return community;
        });

        Tag testTag = testDatabase.executeTransaction(em -> {
            Tag tag = new Tag(testDatabase, "tag");
            em.persist(tag);
            return tag;
        });

        Track testTrackSearchable = testDatabase.executeTransaction(em -> {
            Track track = new Track(testDatabase, new URL("https://www.amazon.com/gp/customer-reviews/RLDPOZSE2K9G9?ref=va_cr_lb"),
                    testCommunity, null, "Test", testUser);
            em.persist(track);
            return track;
        });

        Track testTrackUnsearchable = testDatabase.executeTransaction(em -> {
            Track track = new Track(testDatabase, new URL("http://test2.com/"),
                    testCommunity, null, "Test", testUser);
            em.persist(track);
            return track;
        });

        Virtual.setInstance(new DefaultVirtual(testUser));

        testTrackSearchable.addTags(Set.of(testTag));
        testTrackUnsearchable.addTags(Set.of(testTag));
        testTrackUnsearchable.setDeleted(true);

        SearchResult<Track> result =
                Track.createSearch(testDatabase, testCommunity).search(Search.parse("user:Test"), 5);
        assertTrue(result.getResults().contains(testTrackSearchable));
        assertFalse(result.getResults().contains(testTrackUnsearchable));
    }
}
