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
package io.trino.plugin.datasketches.state;

import io.airlift.slice.Slice;
import io.trino.spi.function.AccumulatorState;
import io.trino.spi.function.AccumulatorStateMetadata;

/**
 * State object to keep track of sketch aggregations.
 */
@AccumulatorStateMetadata(stateSerializerClass = SketchStateSerializer.class, stateFactoryClass = SketchStateFactory.class)
public interface SketchState
        extends AccumulatorState
{
    Slice getSketch();

    int getNominalEntries();

    long getSeed();

    void setSketch(Slice value);

    void setNominalEntries(int value);

    void setSeed(long value);

    void merge(SketchState state);
}