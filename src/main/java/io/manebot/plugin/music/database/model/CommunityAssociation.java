package io.manebot.plugin.music.database.model;

import io.manebot.database.Database;
import io.manebot.database.model.Platform;
import io.manebot.database.model.TimedRow;

import javax.persistence.*;
import java.sql.SQLException;

@javax.persistence.Entity
@Table(
        uniqueConstraints = {@UniqueConstraint(columnNames ={"platformId", "id"})}
)
public class CommunityAssociation extends TimedRow {
    @Transient
    private final Database database;

    public CommunityAssociation(Database database) {
        this.database = database;
    }

    public CommunityAssociation(Database database, Community community, Platform platform, String id) {
        this.database = database;

        this.community = community;
        this.platform = platform;
        this.id = id;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int communityAssociationId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "communityId")
    private Community community;

    @ManyToOne(optional = false)
    @JoinColumn(name = "platformId")
    private Platform platform;

    @Column(length = 64, nullable = false)
    private String id;

    public Community getCommunity() {
        return community;
    }

    public Platform getPlatform() {
        return platform;
    }

    public String getId() {
        return id;
    }

    public int getCommunityAssociationId() {
        return communityAssociationId;
    }

    public void setAssociation(Platform platform, String id) {
        try {
            database.executeTransaction(s -> {
                CommunityAssociation community = s.find(CommunityAssociation.class, getCommunityAssociationId());
                community.platform = platform;
                community.id = id;
                community.setUpdated(System.currentTimeMillis());
                return community;
            });

            this.platform = platform;
            this.id = id;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete() {
        try {
            database.executeTransaction(s -> {
                s.remove(s.find(CommunityAssociation.class, getCommunityAssociationId()));
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
