/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.internal.scan.UsedByScanPlugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@UsedByScanPlugin("test-distribution")
@NullMarked
public class DefaultTestDescriptor extends AbstractTestDescriptor {
    private final String displayName;
    @Nullable
    private final String className;
    private final String classDisplayName;

    @UsedByScanPlugin("test-distribution")
    public DefaultTestDescriptor(Object id, @Nullable String className, String name) {
        this(id, className, name, null, name);
    }

    @UsedByScanPlugin("test-distribution")
    public DefaultTestDescriptor(Object id, @Nullable String className, String name, @Nullable String classDisplayName, String displayName) {
        super(id, name);
        this.className = className;
        this.classDisplayName = classDisplayName == null ? className : classDisplayName;
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        String className = getClassName();
        return "Test " + getName() + (className == null ? "" : ("(" + className + ")"));
    }

    @Override
    public boolean isComposite() {
        return false;
    }

    @Nullable
    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getMethodName() {
        return getName();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getClassDisplayName() {
        return classDisplayName;
    }
}
