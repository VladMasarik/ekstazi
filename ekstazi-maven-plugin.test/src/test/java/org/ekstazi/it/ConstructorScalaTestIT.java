/*
 * Copyright 2014-present Milos Gligoric
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

package org.ekstazi.it;

import org.junit.Ignore;
import org.junit.Test;
import org.ekstazi.it.util.EkstaziPaths;

public class ConstructorScalaTestIT extends AbstractScalaTestIT {

    @Ignore
    @Test
    public void test() throws Exception {
        String testName = "scalatest-constructor";
        EkstaziPaths.removeEkstaziDirectories(getClass(), testName);
        executeCleanTestStep(testName, 0, 1);
        executeCleanTestStep(testName, 0, 0);
        // TODO: Check dependencies file: we need to find
        // Another1.class and Another2.class.
    }
}
