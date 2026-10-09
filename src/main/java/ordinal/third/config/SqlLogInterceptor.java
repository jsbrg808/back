package ordinal.third.config;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;

@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
public class SqlLogInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger("QUERY_LOG");

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement mappedStatement = (MappedStatement) args[0];
        Object parameterObject = args[1];
        BoundSql boundSql = findBoundSql(args, mappedStatement, parameterObject);
        String sql = renderSql(mappedStatement, boundSql, parameterObject);
        long start = System.currentTimeMillis();

        try {
            Object result = invocation.proceed();
            log.info("[{}] {} ms, rows={}\n{}", mappedStatement.getId(), elapsed(start), rowCount(result), sql);
            return result;
        } catch (Throwable ex) {
            log.error("[{}] {} ms, failed={}\n{}", mappedStatement.getId(), elapsed(start), ex.getClass().getSimpleName(), sql);
            throw ex;
        }
    }

    private BoundSql findBoundSql(Object[] args, MappedStatement mappedStatement, Object parameterObject) {
        if (args.length == 6 && args[5] instanceof BoundSql boundSql) {
            return boundSql;
        }
        return mappedStatement.getBoundSql(parameterObject);
    }

    private String renderSql(MappedStatement mappedStatement, BoundSql boundSql, Object parameterObject) {
        String sql = formatSql(boundSql.getSql());
        for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
            if (parameterMapping.getMode() == ParameterMode.OUT) {
                continue;
            }
            Object value = resolveParameter(mappedStatement, boundSql, parameterObject, parameterMapping.getProperty());
            sql = sql.replaceFirst("\\?", Matcher.quoteReplacement(formatValue(value)));
        }
        return sql;
    }

    private Object resolveParameter(MappedStatement mappedStatement, BoundSql boundSql, Object parameterObject, String propertyName) {
        if (boundSql.hasAdditionalParameter(propertyName)) {
            return boundSql.getAdditionalParameter(propertyName);
        }
        if (parameterObject == null) {
            return null;
        }

        TypeHandlerRegistry registry = mappedStatement.getConfiguration().getTypeHandlerRegistry();
        if (registry.hasTypeHandler(parameterObject.getClass())) {
            return parameterObject;
        }
        if (parameterObject instanceof Map<?, ?> map && map.containsKey(propertyName)) {
            return map.get(propertyName);
        }

        MetaObject metaObject = mappedStatement.getConfiguration().newMetaObject(parameterObject);
        if (metaObject.hasGetter(propertyName)) {
            return metaObject.getValue(propertyName);
        }
        return "<unresolved:" + propertyName + ">";
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Date || value instanceof TemporalAccessor) {
            return "'" + escape(value.toString()) + "'";
        }
        return "'" + escape(value.toString()) + "'";
    }

    private String escape(String value) {
        return value.replace("'", "''");
    }

    private String formatSql(String sql) {
        String cleaned = sql.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = cleaned.split("\n");
        int commonIndent = findCommonIndent(lines);
        StringBuilder formatted = new StringBuilder();

        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String trimmedRight = line.replaceFirst("\\s+$", "");
            if (commonIndent > 0 && trimmedRight.length() >= commonIndent) {
                trimmedRight = trimmedRight.substring(commonIndent);
            }
            if (!formatted.isEmpty()) {
                formatted.append('\n');
            }
            formatted.append(trimmedRight);
        }
        return formatted.toString();
    }

    private int findCommonIndent(String[] lines) {
        int commonIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            int indent = 0;
            while (indent < line.length() && Character.isWhitespace(line.charAt(indent))) {
                indent++;
            }
            commonIndent = Math.min(commonIndent, indent);
        }
        return commonIndent == Integer.MAX_VALUE ? 0 : commonIndent;
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    private String rowCount(Object result) {
        if (result instanceof Collection<?> collection) {
            return String.valueOf(collection.size());
        }
        if (result instanceof Number number) {
            return number.toString();
        }
        return "-";
    }
}
