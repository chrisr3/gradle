/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.changedetection.state;

import com.google.common.base.Function;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Ordering;
import com.google.common.hash.HashCode;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.caching.internal.DefaultBuildCacheHasher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;

public class ClasspathEntryResourceCollectionBuilder {
    private static final Ordering<Map.Entry<String, NormalizedFileSnapshot>> SNAPSHOT_ENTRY_ORDERING = Ordering.natural().onResultOf(new Function<Map.Entry<String, NormalizedFileSnapshot>, Comparable<NormalizedFileSnapshot>>() {
        @Override
        public NormalizedFileSnapshot apply(Map.Entry<String, NormalizedFileSnapshot> input) {
            return input.getValue();
        }
    });
    private final SnapshotNormalizationStrategy normalizationStrategy;
    private final StringInterner stringInterner;
    private final TaskFilePropertyCompareStrategy compareStrategy;
    private final Multimap<String, NormalizedFileSnapshot> normalizedSnapshots;

    public ClasspathEntryResourceCollectionBuilder(StringInterner stringInterner) {
        this.normalizationStrategy = TaskFilePropertySnapshotNormalizationStrategy.RELATIVE;
        this.compareStrategy = TaskFilePropertyCompareStrategy.UNORDERED;
        this.stringInterner = stringInterner;
        this.normalizedSnapshots = MultimapBuilder.hashKeys().arrayListValues().build();
    }

    void visitFile(RegularFileSnapshot file, HashCode hash) {
        if (hash != null) {
            normalizedSnapshots.put(file.getPath(), normalizationStrategy.getNormalizedSnapshot(file.withContentHash(hash), stringInterner));
        }
    }

    void visitZipFileEntry(ZipEntry zipEntry, HashCode hash) {
        if (hash != null) {
            normalizedSnapshots.put(zipEntry.getName(), new DefaultNormalizedFileSnapshot(zipEntry.getName(), new FileHashSnapshot(hash)));
        }
    }

    HashCode getHash() {
        if (normalizedSnapshots.isEmpty()) {
            return null;
        }
        DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
        Collection<NormalizedFileSnapshot> values = normalizedSnapshots.values();
        compareStrategy.appendToHasher(hasher, values);
        return hasher.hash();
    }

    void collectNormalizedSnapshots(ResourceCollectionSnapshotBuilder builder) {
        if (normalizedSnapshots.isEmpty()) {
            return;
        }
        List<Map.Entry<String, NormalizedFileSnapshot>> sorted = new ArrayList<Map.Entry<String, NormalizedFileSnapshot>>(normalizedSnapshots.entries());
        Collections.sort(sorted, SNAPSHOT_ENTRY_ORDERING);
        for (Map.Entry<String, NormalizedFileSnapshot> normalizedFileSnapshotEntry : sorted) {
            builder.collectNormalizedFileSnapshot(normalizedFileSnapshotEntry.getKey(), normalizedFileSnapshotEntry.getValue());
        }
    }

}
