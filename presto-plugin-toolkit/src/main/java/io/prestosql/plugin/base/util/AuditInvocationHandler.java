/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.plugin.base.util;

import com.google.common.reflect.AbstractInvocationHandler;
import io.airlift.log.RollingFileHandler;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import io.prestosql.spi.security.AccessDeniedException;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;

public class AuditInvocationHandler
        extends AbstractInvocationHandler
{
    private final Object delegate;
    private final ParameterNamesProvider parameterNames;
    private final RollingFileHandler logHandler;

    public AuditInvocationHandler(Object delegate, ParameterNamesProvider parameterNames, String auditLogPath, int maxAuditHistory, DataSize maxAuditSize)
    {
        this.delegate = requireNonNull(delegate, "delegate is null");
        this.parameterNames = requireNonNull(parameterNames, "parameterNames is null");
        this.logHandler = new RollingFileHandler(auditLogPath, maxAuditHistory, maxAuditSize.toBytes());
    }

    @Override
    protected Object handleInvocation(Object proxy, Method method, Object[] args)
            throws Throwable
    {
        Object result;
        long startNanos = System.nanoTime();
        try {
            result = method.invoke(delegate, args);
        }
        catch (AccessDeniedException e) {
            Duration elapsed = Duration.nanosSince(startNanos);
            Throwable t = e.getCause();
            LogRecord logRecord = new LogRecord(Level.INFO, (format("%s status: failed timeTaken: %s", invocationDescription(method, args), elapsed)));
            logHandler.publish(logRecord);
            throw t;
        }
        Duration elapsed = Duration.nanosSince(startNanos);
        LogRecord logRecord = new LogRecord(Level.INFO, format("%s status: succeeded timeTaken: %s", invocationDescription(method, args), elapsed));
        logHandler.publish(logRecord);
        return result;
    }

    private String invocationDescription(Method method, Object[] args)
    {
        Optional<List<String>> parameterNames = this.parameterNames.getParameterNames(method);
        return "operation: " + method.getName() + " arguments: " +
                IntStream.range(0, args.length)
                        .mapToObj(i -> {
                            if (parameterNames.isPresent()) {
                                return format("%s=%s", parameterNames.get().get(i), formatArgument(args[i]));
                            }
                            return formatArgument(args[i]);
                        })
                        .collect(joining(", ", "(", ")"));
    }

    private static String formatArgument(Object arg)
    {
        if (arg instanceof String) {
            return "'" + ((String) arg).replace("'", "''") + "'";
        }
        if (arg instanceof Optional) {
            return String.valueOf(((Optional) arg).get());
        }
        return String.valueOf(arg);
    }

    public interface ParameterNamesProvider
    {
        Optional<List<String>> getParameterNames(Method method);
    }

    public static class ReflectiveParameterNamesProvider
            implements ParameterNamesProvider
    {
        @Override
        public Optional<List<String>> getParameterNames(Method method)
        {
            Parameter[] parameters = method.getParameters();
            if (Arrays.stream(parameters).noneMatch(Parameter::isNamePresent)) {
                return Optional.empty();
            }
            return Arrays.stream(parameters)
                    .map(Parameter::getName)
                    .collect(collectingAndThen(toImmutableList(), Optional::of));
        }
    }
}
