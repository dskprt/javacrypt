package com.github.dskprt.javacrypt.classloaders;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.jar.JarFile;

public class ByteClassLoader extends ClassLoader {

    private final JarFile jar;

    private final HashMap<String, byte[]> classes;
    private final HashMap<String, byte[]> resources;

    public ByteClassLoader(JarFile jar, HashMap<String, byte[]> classes, HashMap<String, byte[]> resources) {
        this.jar = jar;
        this.classes = classes;
        this.resources = resources;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if(classes.containsKey(name.replace(".", "/"))) {
            byte[] bytes = classes.get(name.replace(".", "/"));

            return defineClass(name, bytes, 0, bytes.length);
        }

        return super.findClass(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        if(resources.containsKey(name)) {
            return new ByteArrayInputStream(resources.get(name));
        }

        return super.getResourceAsStream(name);
    }

    @Override
    protected URL findResource(String name) {
        if(resources.containsKey(name)) {
            try {
                String base = new File(jar.getName()).toURI().toURL().toString();

                return new URL(String.format("jar:%s!/%s", base, name));
            } catch(MalformedURLException e) {
                e.printStackTrace();
                return null;
            }
        }

        return super.findResource(name);
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        List<URL> urls = new ArrayList<>();

        for(String resource : resources.keySet()) {
            if(resource.equals(name)) {
                urls.add(findResource(name));
            }
        }

        return Collections.enumeration(urls);
    }
}
