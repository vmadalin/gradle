/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import org.gradle.platform.base.internal.toolchain.SearchResult;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.List;

public interface WindowsComponentLocator<T> {
    /**
     * Locates a component to use.
     *
     * @param candidate The install dir to use. Can be null.
     * @return A result containing the best component, or an unavailable result that explains why the lookup failed
     */
    SearchResult<T> locateComponent(@Nullable File candidate);

    /**
     * Locates all available components. Does not explain why things were not found. This method is only used for testing.
     */
    List<? extends T> locateAllComponents();
}
