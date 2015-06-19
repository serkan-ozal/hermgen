/*
 * Copyright (c) 2008-2015, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.hermgen;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hermgen.impl.ClassDataRetriever;
import com.hazelcast.hermgen.impl.ClasspathUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Logger;

/**
 * {@link URLClassLoader} implementation to search unknown classes 
 * over Hazelcast Distributed ClassLoader cluster.
 * 
 * @author Serkan OZAL
 */
// -Djava.system.class.loader=com.hazelcast.hermgen.HermGenDistributedClassLoader
public class HermGenDistributedClassLoader extends URLClassLoader {

    protected static final Logger LOGGER =
            Logger.getLogger(HermGenDistributedClassLoader.class.getName());
    protected static final boolean dontCreateEmbeddedInstance =
            Boolean.getBoolean("hazelcast.hermgen.dontCreateEmbeddedInstance");

    private static HermGenDistributedClassLoader INSTANCE;

    protected boolean initInProgress;
    protected ClassLoader parent;
    protected ClassDataRetriever classDataRetriever;
    protected final Set<String> classNamePrefixesToAskParent = new HashSet<String>();

    public HermGenDistributedClassLoader() {
        super(findClasspathUrls(null));
        setUp(null);
        INSTANCE = this;
    }

    public HermGenDistributedClassLoader(ClassLoader parent) {
        super(findClasspathUrls(parent), null);
        this.parent = parent;
        setUp(null);
        INSTANCE = this;
    }

    public HermGenDistributedClassLoader(ClassLoader parent,
                                           Collection<String> classNamePrefixesToAskParent) {
        super(findClasspathUrls(parent), null);
        this.parent = parent;
        setUp(classNamePrefixesToAskParent);
        INSTANCE = this;
    }

    public static HermGenDistributedClassLoader getInstance() {
        return INSTANCE;
    }

    protected static URL[] findClasspathUrls(ClassLoader classLoader) {
        Set<URL> urls = ClasspathUtil.findClasspathUrls(classLoader);
        return urls.toArray(new URL[0]);
    }

    protected static File[] getExtDirs() {
        String extDirsProperty = System.getProperty("java.ext.dirs");
        File[] extDirs;
        if (extDirsProperty != null) {
            StringTokenizer st = new StringTokenizer(extDirsProperty, File.pathSeparator);
            int count = st.countTokens();
            extDirs = new File[count];
            for (int i = 0; i < count; i++) {
                extDirs[i] = new File(st.nextToken());
            }
        } else {
            extDirs = new File[0];
        }
        return extDirs;
    }

    protected static Set<URL> getExtURLs(File[] dirs) throws IOException {
        Set<URL> urls = new HashSet<URL>();
        for (int i = 0; i < dirs.length; i++) {
            String[] files = dirs[i].list();
            if (files != null) {
                for (int j = 0; j < files.length; j++) {
                    if (!files[j].equals("meta-index")) {
                        File f = new File(dirs[i], files[j]);
                        urls.add(f.toURI().toURL());
                    }
                }
            }
        }
        return urls;
    }

    private void setUp(Collection<String> additionalPrefixes) {
        classNamePrefixesToAskParent.add("java");
        classNamePrefixesToAskParent.add("javax");
        classNamePrefixesToAskParent.add("sun");
        classNamePrefixesToAskParent.add("com.sun");
        classNamePrefixesToAskParent.add("com.hazelcast");
        if (additionalPrefixes != null) {
            for (String prefix : additionalPrefixes) {
                classNamePrefixesToAskParent.add(prefix);
            }
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        Class<?> clazz = findLoadedClass(name);
        if (clazz == null) {
            LOGGER.finest("Will load class: " + name);
            if (parent != null && shouldLoadWithParentClassLoader(name)) {
                clazz = parent.loadClass(name);
                LOGGER.finest("Loaded class: " + name + 
                              " and its classloader is " + clazz.getClassLoader());
                if (shouldLoadedClassTriggerInitialize(clazz)) {
                    initIfNeeded();
                }
            } else {
                clazz = findClass(name);
            }
        }
        if (resolve) {
            resolveClass(clazz);
        }
        return clazz;
    }

    protected boolean shouldLoadWithParentClassLoader(String name) {
        for (String prefix : classNamePrefixesToAskParent) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    protected boolean shouldLoadedClassTriggerInitialize(Class<?> clazz) {
        return String.class.equals(clazz);
    }

    @Override
    protected synchronized Class<?> findClass(String name)
            throws ClassNotFoundException {
        try {
            LOGGER.finest("Will find class: " + name);
            Class<?> clazz = super.findClass(name);
            LOGGER.finest("Found class: " + name + " and its classloader is " + 
                          clazz.getClassLoader());
            return clazz;
        } catch (ClassNotFoundException e) {
            return handleClassNotFoundException(name, e);
        }
    }

    @SuppressWarnings("deprecation")
    protected Class<?> handleClassNotFoundException(String name, ClassNotFoundException e)
            throws ClassNotFoundException {
        if (shouldThrowExceptionOnClassNotFoundException(name, e)) {
            throw e;
        }

        initIfNeeded();

        byte[] classDef = findNotFoundClassData(name);
        if (classDef == null) {
            throw new ClassNotFoundException(name);
        } else {
            return defineClass(classDef, 0, classDef.length);
        }
    }

    protected boolean shouldThrowExceptionOnClassNotFoundException(String name,
                                                                   ClassNotFoundException e) {
        return initInProgress || name.startsWith("com.hazelcast");
    }

    protected byte[] findNotFoundClassData(String name) {
        if (classDataRetriever.isAvailable()) {
            return classDataRetriever.getClassData(name, null);
        } else {
            return null;
        }
    }

    protected synchronized void init() throws ClassNotFoundException {
        initInProgress = true;
        try {
            LOGGER.info("Initializing ...");
            classDataRetriever = new ClassDataRetriever(!dontCreateEmbeddedInstance);
            LOGGER.info("Initialized");
        } catch (Throwable t) {
            throw new IllegalStateException("Could not be initialized !", t);
        } finally {
            initInProgress = false;
        }
    }

    protected void initIfNeeded() throws ClassNotFoundException {
        if (classDataRetriever == null) {
            init();
        }
    }

    public HazelcastInstance getHazelcastInstance() {
        if (classDataRetriever != null) {
            return classDataRetriever.getHazelcastInstance();
        } else {
            return null;
        }
    }

    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        if (classDataRetriever != null) {
            classDataRetriever.setHazelcastInstance(hazelcastInstance);
        } else {
            classDataRetriever = new ClassDataRetriever(hazelcastInstance);
        }
    }
    
    public void destroy() {
        if (classDataRetriever != null) {
            classDataRetriever.destroy();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        destroy();
    }

}
