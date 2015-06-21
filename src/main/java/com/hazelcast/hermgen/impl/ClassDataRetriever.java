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

package com.hazelcast.hermgen.impl;

import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.hazelcast.config.Config;
import com.hazelcast.core.Cluster;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.core.IMap;
import com.hazelcast.core.Member;
import com.hazelcast.hermgen.ClassData;
import com.hazelcast.hermgen.ClassDataFinder;

/**
 * Finds data (bytecode / byte[]) of {@link Class} with given name 
 * over Hazelcast Distributed ClassLoader group.
 *
 * @author Serkan OZAL
 */
public class ClassDataRetriever {

    private static final Logger LOGGER = 
            Logger.getLogger(ClassDataRetriever.class.getName());
    
    private volatile HazelcastInstance hazelcastInstance;
    private IExecutorService executorService;
    private Cluster cluster;
    private IMap<String, ClassData> classDataMap;

    public ClassDataRetriever() {
        init(null);
    }

    public ClassDataRetriever(boolean init) {
        if (init) {
            init(null);
        }
    }

    public ClassDataRetriever(HazelcastInstance hzInstance) {
        init(hzInstance);
    }

    public boolean isAvailable() {
        return hazelcastInstance != null;
    }

    private void init(HazelcastInstance hzInstance) {
        if (hzInstance == null) {
            Config config = new Config();
            config.getGroupConfig().setName("hermgen-group");
            hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        } else {
            hazelcastInstance = hzInstance;
        }
        executorService = hazelcastInstance.getExecutorService("hermgen-executor");
        classDataMap = hazelcastInstance.getMap("hermgen-map");
        cluster = hazelcastInstance.getCluster();
    }

    public HazelcastInstance getHazelcastInstance() {
        return hazelcastInstance;
    }

    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        init(hazelcastInstance);
    }

    public byte[] getClassData(String className, ClassDataFinder classDataFinder) {
        if (!isAvailable()) {
            return null;
        }
        if (classDataFinder == null) {
            classDataFinder = new DefaultClassDataFinder(className);
        }
        ClassData classData = classDataMap.get(className);
        if (classData != null) {
            return classData.getClassDefinition();
        }
        for (Member member : cluster.getMembers()) {
            if (member.localMember()) {
                continue;
            }
            Future<ClassData> classDataFuture = 
                    executorService.submitToMember(classDataFinder, member);
            try {
                classData = classDataFuture.get();
                if (classData != null) {
                    classDataMap.put(className, classData);
                    byte[] classDef = classData.getClassDefinition();
                    if (classDef != null) {
                        return classDef;
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Unable to get class data for class " + className + 
                           " from member " + member, e);
            }
        }
        return null;
    }

    public void destroy() {
        if (hazelcastInstance != null) {
            hazelcastInstance.shutdown();
        }
    }

}
