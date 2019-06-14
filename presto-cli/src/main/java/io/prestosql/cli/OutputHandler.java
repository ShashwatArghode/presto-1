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
package io.prestosql.cli;

import com.google.common.collect.Queues;
import io.prestosql.client.StatementClient;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class OutputHandler
        implements Closeable
{
    private static final int MAX_QUEUED_ROWS = 50_000;
    private static final int MIN_BUFFERED_ROWS = 100;
    private static final long MAX_BUFFER_TIMEOUT = 3;

    private final AtomicBoolean closed = new AtomicBoolean();
    private final BlockingQueue<List<?>> rowQueue = new LinkedBlockingQueue<>(MAX_QUEUED_ROWS);
    private final OutputPrinter printer;
    private List<List<?>> rowBuffer = new ArrayList<>();

    private CompletableFuture<Void> future;

    public OutputHandler(OutputPrinter printer)
    {
        this.printer = requireNonNull(printer, "printer is null");
    }

    @Override
    public void close()
            throws IOException
    {
        if (!closed.getAndSet(true)) {
            printer.finish();
        }
    }

    public void processRows(StatementClient client)
            throws IOException
    {
        this.future = CompletableFuture.runAsync(() -> {
            while (client.isRunning()) {
                Iterable<List<Object>> data = client.currentData().getData();
                if (data != null) {
                    for (List<Object> row : data) {
                        try {
                            rowQueue.put(unmodifiableList(row));
                        }
                        catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                }
                client.advance();
            }
        });
        try {
            while (!future.isDone()) {
                if (!rowQueue.isEmpty()) {
                    drain(rowQueue);
                }
            }
            while (!rowQueue.isEmpty()) {
                rowBuffer.add(rowQueue.poll());
            }
            printer.printRows(unmodifiableList(rowBuffer), true);
            rowBuffer.clear();
            if (future.isCompletedExceptionally()) {
                future.get();
            }
        }
        catch (InterruptedException | ExecutionException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void drain(BlockingQueue<List<?>> rowQueue)
            throws InterruptedException, IOException
    {
        Queues.drain(rowQueue, rowBuffer, MIN_BUFFERED_ROWS, MAX_BUFFER_TIMEOUT, SECONDS);
        printer.printRows(unmodifiableList(rowBuffer), false);
        rowBuffer.clear();
    }
}
