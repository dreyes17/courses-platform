package com.example.courses.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the SQL issued by the current thread only, so background work (the outbox relay, listeners)
 * doesn't pollute the count. Registered through hibernate.session_factory.statement_inspector.
 */
public class SqlStatementCounter implements StatementInspector {

    private static final ThreadLocal<List<String>> STATEMENTS = ThreadLocal.withInitial(ArrayList::new);

    @Override
    public String inspect(String sql) {
        STATEMENTS.get().add(sql);
        return sql;
    }

    public static void reset() {
        STATEMENTS.get().clear();
    }

    public static List<String> statements() {
        return List.copyOf(STATEMENTS.get());
    }
}
