package org.togglz.core.repository.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.Before;
import org.junit.Test;
import org.togglz.core.Feature;
import org.togglz.core.repository.FeatureState;
import org.togglz.core.util.DbUtils;
import org.togglz.core.util.MapConverter;

public class JDBCStateRepositoryTest {

    private DataSource dataSource;

    private JDBCStateRepository repository;

    @Before
    public void before() throws SQLException {
        dataSource = createDataSource();
        repository = new JDBCStateRepository(dataSource, "TOGGLZ",
            MapConverter.create().withoutNewLines());
    }

    @Test
    public void testShouldSaveStateWithoutStrategyOrParameters() throws SQLException {

        /*
         * WHEN a feature without strategy is persisted
         */
        FeatureState state = new FeatureState(TestFeature.F1).disable();
        repository.setFeatureState(state);

        /*
         * THEN there should be a corresponding entry in the database
         */
        assertEquals(1l, query(dataSource, "SELECT COUNT(*) FROM TOGGLZ WHERE FEATURE_NAME = 'F1'"));
        assertEquals(0, query(dataSource, "SELECT FEATURE_ENABLED FROM TOGGLZ WHERE FEATURE_NAME = 'F1'"));
        assertEquals(null, query(dataSource, "SELECT STRATEGY_ID FROM TOGGLZ WHERE FEATURE_NAME = 'F1'"));
        assertEquals(null, query(dataSource, "SELECT STRATEGY_PARAMS FROM TOGGLZ WHERE FEATURE_NAME = 'F1'"));

    }

    @Test
    public void testShouldSaveStateStrategyAndParameters() throws SQLException {

        /*
         * WHEN a feature without strategy is persisted
         */
        FeatureState state = new FeatureState(TestFeature.F1)
            .enable()
            .setStrategyId("someId")
            .setParameter("param", "foo");
        repository.setFeatureState(state);

        /*
         * THEN there should be a corresponding entry in the database
         */
        assertEquals(1l, query(dataSource, "SELECT COUNT(*) FROM TOGGLZ WHERE FEATURE_NAME = 'F1'"));
        assertEquals(1, query(dataSource, "SELECT FEATURE_ENABLED FROM TOGGLZ WHERE FEATURE_NAME = 'F1'"));
        assertEquals("someId", query(dataSource, "SELECT STRATEGY_ID FROM TOGGLZ WHERE FEATURE_NAME = 'F1'"));
        assertEquals("param=foo", query(dataSource, "SELECT STRATEGY_PARAMS FROM TOGGLZ WHERE FEATURE_NAME = 'F1'"));

    }

    @Test
    public void testShouldReadStateWithoutStrategyAndParameters() throws SQLException {

        /*
         * GIVEN a database row containing a simple feature state
         */
        update(dataSource, "INSERT INTO TOGGLZ VALUES ('F1', 0, NULL, NULL)");

        /*
         * WHEN the repository reads the state
         */
        FeatureState state = repository.getFeatureState(TestFeature.F1);

        /*
         * THEN the properties should be set like expected
         */
        assertNotNull(state);
        assertEquals(TestFeature.F1, state.getFeature());
        assertEquals(false, state.isEnabled());
        assertEquals(null, state.getStrategyId());
        assertEquals(0, state.getParameterNames().size());

    }

    @Test
    public void testShouldReadStateWithStrategyAndParameters() throws SQLException {

        /*
         * GIVEN a database row containing a simple feature state
         */
        update(dataSource, "INSERT INTO TOGGLZ VALUES ('F1', 1, 'myStrategy', 'param23=foobar')");

        /*
         * WHEN the repository reads the state
         */
        FeatureState state = repository.getFeatureState(TestFeature.F1);

        /*
         * THEN the properties should be set like expected
         */
        assertNotNull(state);
        assertEquals(TestFeature.F1, state.getFeature());
        assertEquals(true, state.isEnabled());
        assertEquals("myStrategy", state.getStrategyId());
        assertEquals(1, state.getParameterNames().size());
        assertEquals("foobar", state.getParameter("param23"));

    }

    @Test
    public void testShouldUpdateExistingDatabaseEntry() throws SQLException {

        /*
         * GIVEN a database row containing a simple feature state
         */
        update(dataSource, "INSERT INTO TOGGLZ VALUES ('F1', 1, 'myStrategy', 'param23=foobar')");

        /*
         * AND the database entries are like expected
         */
        assertEquals(1l, query(dataSource, "SELECT COUNT(*) FROM TOGGLZ WHERE FEATURE_NAME = 'F1'"));
        assertEquals(1, query(dataSource, "SELECT FEATURE_ENABLED FROM TOGGLZ WHERE FEATURE_NAME = 'F1'"));
        assertEquals("myStrategy", query(dataSource, "SELECT STRATEGY_ID FROM TOGGLZ WHERE FEATURE_NAME = 'F1'"));
        assertEquals("param23=foobar", query(dataSource, "SELECT STRATEGY_PARAMS FROM TOGGLZ WHERE FEATURE_NAME = 'F1'"));

        /*
         * WHEN the repository writes new state
         */
        FeatureState state = new FeatureState(TestFeature.F1)
            .disable()
            .setStrategyId("someId")
            .setParameter("param", "foo");
        repository.setFeatureState(state);

        /*
         * THEN the properties should be set like expected
         */
        assertEquals(1l, query(dataSource, "SELECT COUNT(*) FROM TOGGLZ WHERE FEATURE_NAME = 'F1'"));
        assertEquals(0, query(dataSource, "SELECT FEATURE_ENABLED FROM TOGGLZ WHERE FEATURE_NAME = 'F1'"));
        assertEquals("someId", query(dataSource, "SELECT STRATEGY_ID FROM TOGGLZ WHERE FEATURE_NAME = 'F1'"));
        assertEquals("param=foo", query(dataSource, "SELECT STRATEGY_PARAMS FROM TOGGLZ WHERE FEATURE_NAME = 'F1'"));

    }

    private Object query(DataSource dataSource, String sql) {

        try {

            Connection connection = dataSource.getConnection();
            try {

                Statement statement = connection.createStatement();
                try {

                    ResultSet resultSet = statement.executeQuery(sql);
                    try {

                        if (resultSet.next()) {
                            return resultSet.getObject(1);
                        }
                        return null;

                    } finally {
                        DbUtils.closeQuietly(resultSet);
                    }

                } finally {
                    DbUtils.closeQuietly(statement);
                }

            } finally {
                DbUtils.closeQuietly(connection);
            }

        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }

    }

    private int update(DataSource dataSource, String sql) {

        try {

            Connection connection = dataSource.getConnection();
            try {

                Statement statement = connection.createStatement();
                try {

                    return statement.executeUpdate(sql);

                } finally {
                    DbUtils.closeQuietly(statement);
                }

            } finally {
                DbUtils.closeQuietly(connection);
            }

        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }

    }

    private DataSource createDataSource() throws SQLException {
        return JdbcConnectionPool.create("jdbc:h2:mem:", "sa", "");
    }

    private static enum TestFeature implements Feature {

        F1;

        @Override
        public boolean isActive() {
            throw new UnsupportedOperationException();
        }

    }

}
