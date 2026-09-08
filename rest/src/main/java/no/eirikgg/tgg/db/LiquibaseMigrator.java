package no.eirikgg.tgg.db;

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
    private static final String CHANGELOG_FILE = "db/changelog/db.changelog-master.xml";


    public void migrate(Db db) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        try (Connection connection = db.getConnection()) {
            DatabaseFactory dbInstance = DatabaseFactory.getInstance();
            JdbcConnection jdbcConnection = new JdbcConnection(connection);
            Database database = dbInstance.findCorrectDatabaseImplementation(jdbcConnection);

            Liquibase liquibase = new Liquibase(
                    CHANGELOG_FILE,
                    new ClassLoaderResourceAccessor(),
                    database
            );


            Scope.child(Scope.Attr.ui, new LoggerUIService(), () ->
                    liquibase.update(new Contexts(), new LabelExpression())
            );
            log.info("Database migration completed using {}", CHANGELOG_FILE);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to run database migrations", e);
        }
    }
}
