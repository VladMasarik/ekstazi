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

package org.ekstazi;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.concurrent.locks.ReentrantLock;

import org.ekstazi.agent.EkstaziAgent;
import org.ekstazi.data.DependencyAnalyzer;
import org.ekstazi.dynamic.DynamicEkstazi;
import org.ekstazi.log.Log;
import org.ekstazi.research.Research;

/**
 * Tool Facade/Singleton. This class should be the only interaction between other
 * code (e.g., JUnit) and Tool. This class should be notified when the recording
 * (of coverage) should start and when the recording should stop; everything
 * else (e.g., instrumentation, hashing, etc.) should be initiated by this class
 * (or from other classes invoked by this class).
 * 
 * Note that access using this class may access monitors that can be access by
 * other threads at the same time.
 */
public final class Ekstazi {

    /** The only instance of this class */
    private static Ekstazi inst;

    /** Regression data analyzer */
    private DependencyAnalyzer mDependencyAnalyzer;
    
    /** Flag to indicate if Tool (this factory) is enabled */
    private final boolean mIsEnabled;

    /** Ensures that one test is running at a time in this VM */
    private final ReentrantLock mTestLock;
    
    /** Enable/disable run of all tests */
    private final boolean mIsForceall;

    /** Enable/disable run of failing tests */
    private final boolean mIsForcefailing;

    /**
     * Constructor.
     */
    private Ekstazi() {
        Log.d("Ekstazi Constructor");
        this.mIsEnabled = initAndReportSuccess();
        Log.d("Tool enabled for this run/VM: " + mIsEnabled);
        this.mTestLock = new ReentrantLock();
        this.mIsForceall = Config.FORCE_ALL_V;
        this.mIsForcefailing = Config.FORCE_FAILING_V;
    }
    
    /**
     * Returns the only instance of this class. This method will construct
     * and initialize the instance if it was not previously constructed.
     */
    public static Ekstazi inst() {
        Log.d("CREATING NEW EKSTAZI INSTANCE");
        Thread.dumpStack();
        synchronized (Ekstazi.class) {
            if (inst == null) {
                inst = new Ekstazi();
            }
        }
        return inst;
    }

    // RUNTIME ACCESS

    /**
     * Checks if class was analyzed
     * @param name name of the class
     * 
     * @return Whether class was analyzed
     */
    public boolean checkIfAffected(String name) {
        Log.d("Checking if affected:", name);
        return mIsEnabled ? mDependencyAnalyzer.isAffected(name) : true;
    }

    /**
     * Initiates dependency collection on a class
     * 
     * @param name class name
     */
    public void startCollectingDependencies(String name) { // name = DEFAULT
        Log.d("Begin collecting dependencies of METHOD: ", name);
        if (mIsEnabled) {
            mDependencyAnalyzer.beginCoverage(name);
        }
    }
    
    /**
     * End the dependency collection of a class.
     * @param name Class name
     */
    public void finishCollectingDependencies(String name) {
        if (StringUtils.isNotEmpty(name)) {
            Log.d("Finish collecting dependencies: ", name);
            if (mIsEnabled) {
                mDependencyAnalyzer.endCoverage(name);
            }            
        } else {
            Log.i("Input name is empty or null.");
        }
    }

    /**
     * Checks if class was analyzed, but only if the dependecny collection did not fail.
     * @param className Class name that is being checked.
     * 
     * @return Whether the class was analyzed
     */
    public boolean isClassAffected(String className) { // return if the class should be tested, and print RUN / SKIP into a class file
        Log.d("Checking if class affected:", className);
        // Check if failing tests should be run or all tests are forced.
        if ((wasFailing(className) && mIsForcefailing) || mIsForceall) {
            return true;
        }
        return mIsEnabled ? mDependencyAnalyzer.isClassAffected(className) : true;
    }

    /**
     * Initiates dependency collection on a class
     * @param className Class name
     */
    public void beginClassCoverage(String className) { // notes that class was tested and what it hash is; does not save hashes
        Log.d("Begin measuring coverage: ", className); // FINISHED HERE!!!
        if (mIsEnabled) {
            mDependencyAnalyzer.beginClassCoverage(className);
        }
    }
    
    /**
     * Checks whether the dependency analysis failed at least once.
     * @param className Class name of the analyzed class
     * 
     * @return Whether the analysis failed at least once.
     */
    private boolean wasFailing(String className) {
        if (StringUtils.isNotEmpty(className)) {  
            File testResultsDir = new File(Config.ROOT_DIR_V, Names.TEST_RESULTS_DIR_NAME);
            File outcomeFile = new File(testResultsDir, className);
            return outcomeFile.exists();
        }
        return false;
    }
    
    /**
     * Saves info about the results of running the given test class.
     * 
     * @param className name of the class that is being covered
     * @param isFailOrError whether a fail or an error occured during the class coverage phase
     */
    public void endClassCoverage(String className, boolean isFailOrError) { // just wrapper around DepAnal.endClass
        // which just saves the collected hashes / URLs
        File testResultsDir = new File(Config.ROOT_DIR_V, Names.TEST_RESULTS_DIR_NAME);
        File outcomeFile = new File(testResultsDir, className);
        if (isFailOrError) {
            // TODO: long names.
            testResultsDir.mkdirs();
            try {
                outcomeFile.createNewFile();
            } catch (IOException e) {
                Log.e("Unable to create file for a failing test: " + className, e);
            }
        } else {
            outcomeFile.delete();
        }
        
        Log.d("End measuring coverage: " + className);
        if (mIsEnabled) {
            mDependencyAnalyzer.endClassCoverage(className);
        }
    }


    /**
     * Initializes this facade. This method should be invoked only once.
     * 
     * The following steps are performed: 1) load configuration, 2) establish if
     * this Tool is enabled, 3) set paths needed for instrumentation (if
     * instrumentation is enabled and agent is not already present).
     * 
     * @return true if Tool is enabled, false otherwise.
     */
    private boolean initAndReportSuccess() {
        Config.loadConfig();
        // Initialize storer, hashes, and analyzer.
        mDependencyAnalyzer = Config.createDepenencyAnalyzer();

        boolean isEnabled = establishIfEnabled();
        // Return if not enabled or code should not be instrumented.
        if (!isEnabled || !Config.X_INSTRUMENT_CODE_V || isEkstaziSystemClassLoader()) {
            return isEnabled;
        }

        // Set the agent at runtime if not already set.
        Instrumentation instrumentation = EkstaziAgent.getInstrumentation();
        if (instrumentation == null) {
            Log.d("Agent has not been set previously");
            instrumentation = DynamicEkstazi.initAgentAtRuntimeAndReportSuccess();
            if (instrumentation == null) {
                Log.d("No Instrumentation object found; enabling Ekstazi without using any instrumentation");
                return true;
            }
        }
        Log.d("Agent has been set.");

        return true;
    }

    /**
     * Checks whether the currect class loader is set to {@link EkstaziClassLoader}.
     * 
     * @return true if current system class loader is {@link EkstaziClassLoader}.
     */
    @Research
    private boolean isEkstaziSystemClassLoader() {
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        if (systemClassLoader == null) {
            return false;
        }
        String classLoaderName = systemClassLoader.getClass().getName();
        return classLoaderName.equals(Names.EKSTAZI_CLASSLOADER_BIN);
    }

    /**
     * Checks all conditions that define if Tool is enabled or not. Tool is
     * enabled if appropriate flag is set in configuration.
     * 
     * @return whether the Tool is enabled.
     */
    private static boolean establishIfEnabled() {
        return Config.X_ENABLED_V;
    }
}
