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

package test2;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

/**
 * Test application for demo application 
 * (github.com/serkan-ozal/hermgen-demo/tree/master/src/main/java/demo2/Demo2.java).
 * 
 * @author Serkan OZAL
 */
// Run with "-Djava.system.class.loader=com.hazelcast.hermgen.HermGenDistributedClassLoader"
public class Test2 {

	public static void main(String[] args) throws Exception {
        System.out.println("System classloader : " + ClassLoader.getSystemClassLoader());
        System.out.println("Main classloader   : " + Test2.class.getClassLoader());
        
        HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance();
        IMap<String, Object> userMap = hazelcastInstance.getMap("userMap");
        Object user = userMap.get("Serkan");
        System.out.println(user);
	}

}
