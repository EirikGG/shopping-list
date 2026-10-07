package no.eirikgg.shoppinglist.db;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.ui.LoggerUIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.sql.Connection;

public class LiquibaseMigrator {
    private final Logger log = LoggerFactory.getLogger(getClass());


    public void migrate(Db db) {
        migrate(db, "db/changelog/db.changelog-master.xml");
    }

    public void migrate(Db db, String changelog) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        try (Connection connection = db.getConnection()) {
            DatabaseFactory dbInstance = DatabaseFactory.getInstance();
            JdbcConnection jdbcConnection = new JdbcConnection(connection);
            Database database = dbInstance.findCorrectDatabaseImplementation(jdbcConnection);

            Liquibase liquibase = new Liquibase(
                    changelog,
                    new ClassLoaderResourceAccessor(),
                    database
            );


            Scope.child(Scope.Attr.ui, new LoggerUIService(), () ->
                    liquibase.update(new Contexts(), new LabelExpression())
            );
            log.info("Database migration completed using {}", changelog);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to run database migrations", e);
        }
    }
}
