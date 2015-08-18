/*
 * Copyright 2006-2015 the original author or authors.
 *
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

package com.consol.citrus.dsl.builder;

import com.consol.citrus.TestAction;
import com.consol.citrus.container.TestActionContainer;
import com.consol.citrus.dsl.runner.DefaultContainerRunner;
import com.consol.citrus.dsl.runner.TestRunner;

import java.util.List;

/**
 * Abstract container builder takes care on calling the container runner when actions are placed in the container.
 * @author Christoph Deppisch
 * @since 2.3
 */
public abstract class AbstractTestContainerBuilder<T extends TestActionContainer> extends AbstractTestActionBuilder<T> implements TestActionContainerBuilder<T> {

    /** The test runner */
    private TestRunner runner;

    /** The action container */
    private final TestActionContainer container;

    /**
     * Default constructor with test action.
     * @param container
     */
    public AbstractTestContainerBuilder(TestRunner runner, T container) {
        super(container);
        this.runner = runner;
        this.container = container;
    }

    /**
     * Default constructor.
     * @param container
     */
    public AbstractTestContainerBuilder(T container) {
        super(container);
        this.container = container;
    }

    /**
     * Delegates container execution to container runner or fills container with actions.
     * @param actions
     * @return
     */
    public TestActionContainer actions(TestAction ... actions) {
        if (runner != null) {
            return new DefaultContainerRunner(container, runner).actions(actions);
        } else {
            for (TestAction action : actions) {
                if (action instanceof TestActionBuilder<?>) {
                    container.addTestAction(((TestActionBuilder<?>) action).build());
                } else {
                    container.addTestAction(action);
                }
            }

            return container;
        }
    }

    /**
     * Delegates container execution to container runner or fills container with actions.
     * @param actions
     * @return
     */
    public TestActionContainer when(TestAction ... actions) {
        return actions(actions);
    }

    @Override
    public List<TestAction> getActions() {
        return super.build().getActions();
    }
}