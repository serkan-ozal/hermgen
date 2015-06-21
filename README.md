# HermGen
Hazelcast Based Distributed ClassLoader and PermGen

1. What is HermGen?
==============
**HermGen** is a kind of classloader searches unknown classes over cluster based on Hazelcast. **HermGen**'s distributed classloader searches classes at classpath at first. Then if the requested class coulnd't be found at classpath, **HermGen** sends workers for getting bytecode of requested class to other members in cluster. These workers runs on other members and search the requested class on them and send the bytecode of requested class to caller as response if it is able to find on target member.

The demo application is at [here](https://github.com/serkan-ozal/hermgen-demo).

2. Installation
==============
In your `pom.xml`, you must add repository and dependency for **HermGen**. 
You can change `hermgen.version` to any existing **HermGen** library version.

``` xml
...
<properties>
    ...
    <hermgen.version>1.0</hermgen.version>
    ...
</properties>
...
<dependencies>
    ...
	<dependency>
		<groupId>com.hazelcast</groupId>
		<artifactId>hermgen</artifactId>
		<version>${hermgen.version}</version>
	</dependency>
	...
</dependencies>
...
<repositories>
	...
	<repository>
		<id>serkanozal-maven-repository</id>
		<url>https://github.com/serkan-ozal/maven-repository/raw/master/</url>
	</repository>
	...
</repositories>
...
```

3. Configurations
==============
- `hazelcast.hermgen.dontCreateEmbeddedInstance`: Disables creating embedded Hazelcast instance to connect the cluster for retrieving requested class data. So in this case **HermGen** expects Hazelcast instance configured programmatically by user as `HermGenDistributedClassLoader.getInstance().setHazelcastInstance(hazelcastInstance);`. Default value is `false`.

4. Usage
==============
To enable **HermGen**, you must set the system classloader by `-Djava.system.class.loader=com.hazelcast.hermgen.HermGenDistributedClassLoader` as VM argument.

By default, `HermGenDistributedClassLoader` starts an embedded Hazelcast instance (with group name `hermgen-group`) to connect the cluster for retrieving requested class data. 

If you want to provide your custom Hazelcast instance, you must set `hazelcast.hermgen.dontCreateEmbeddedInstance` to `true` by `-Dhazelcast.hermgen.dontCreateEmbeddedInstance=true` and you can inject your Hazelcast instance over `HermGenDistributedClassLoader` like this:

``` java
HazelcastInstance myHzInstance = ...
...
HermGenDistributedClassLoader.getInstance().setHazelcastInstance(myHzInstance);
```

However note that, until you inject your Hazelcast instance to `HermGenDistributedClassLoader`, `HermGenDistributedClassLoader` doesn't search unknown classes over cluster so you get `ClassNotFoundException` for classes these are not exist in your local classpath.

5. Contribution
==============
- If you think that there is a bug about **HermGen**, please feel create a `bug` labelled issue ticket [here](https://github.com/serkan-ozal/hermgen/issues/new)
- Even if you have a new feature or enhancement request for **HermGen**, you can create an `enhancement` labelled issue at [here](https://github.com/serkan-ozal/hermgen/issues/new)
- Or if you have a question about **HermGen**, you can aslo create a `question` labelled issue at [here](https://github.com/serkan-ozal/hermgen/issues/new)
- I am glad to get your work into **HermGen** so if you have done some stuff (fix, enhancement or new feature) about **HermGen** and want to put them into **HermGen**, you can send a pull request from your repository.

6. Roadmap
==============

- Class selector strategy at caller side if there are different versions of requested class on the cluster.
