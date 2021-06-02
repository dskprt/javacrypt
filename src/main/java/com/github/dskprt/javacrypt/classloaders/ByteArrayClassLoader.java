package com.github.dskprt.javacrypt.classloaders;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;

import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ByteArrayClassLoader extends ClassLoader {

    private final ResourceStreamHandlerFactory resourceStreamHandlerFactory = new ResourceStreamHandlerFactory();

    private final byte[] bytes;

    private final ZipInputStream zis;
    private final CountingInputStream input;
    private final Map<String, Map.Entry<byte[], ZipEntry>> entryMap;

    public ByteArrayClassLoader(byte[] bytes) throws IOException {
        this.bytes = bytes;
        this.zis = new ZipInputStream(new ByteArrayInputStream(bytes));
        this.input = new CountingInputStream(zis);
        this.entryMap = loadEntries();

        for(String s : entryMap.keySet()) {
            System.out.println(s);
        }

        URL.setURLStreamHandlerFactory(resourceStreamHandlerFactory);
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> cls = findLoadedClass(name);
        
        if(cls == null) {
            try {
                InputStream in = getResourceAsStream(name.replace('.', '/') + ".class");
                ByteArrayOutputStream out = new ByteArrayOutputStream();

                IOUtils.copy(in, out);
                byte[] bytes = out.toByteArray();
                cls = defineClass(name, bytes, 0, bytes.length);
                
                if(resolve) {
                    resolveClass(cls);
                }
            } catch(Exception e) {
                cls = super.loadClass(name, resolve);
            }
        }
        
        return cls;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        try {
            if(!name.endsWith(".class")) System.out.println("Resource: " + name);
            return new ByteArrayInputStream(entryMap.get(name).getKey());
        } catch(Exception e) {
            return null;
        }
    }

    @Override
    protected URL findResource(String name) {
        try {
            resourceStreamHandlerFactory.input = getResourceAsStream(name);
            return new URL("fuckyou:" + name);
        } catch(Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private Map<String, Map.Entry<byte[], ZipEntry>> loadEntries() throws IOException {
        Map<String, Map.Entry<byte[], ZipEntry>> map = new HashMap<>();
        ZipEntry entry;

        byte[] buffer = new byte[4096];

        while((entry = zis.getNextEntry()) != null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            int len;

            while ((len = zis.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }

            map.put(entry.getName(), new AbstractMap.SimpleEntry<>(
                    out.toByteArray(), entry
            ));
        }

        return Collections.unmodifiableMap(map);
    }

    public class ResourceConnection extends URLConnection {

        public InputStream input;

        protected ResourceConnection(URL url, InputStream input) {
            super(url);
            this.input = input;
        }

        @Override
        public void connect() throws IOException { }

        @Override
        public InputStream getInputStream() throws IOException {
            assert input != null;
            return input;
        }
    }

    public class ResourceStreamHandler extends URLStreamHandler {

        public InputStream input;

        public ResourceStreamHandler(InputStream input) {
            this.input = input;
        }

        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            return new ResourceConnection(u, input);
        }
    }

    public class ResourceStreamHandlerFactory implements URLStreamHandlerFactory {

        public InputStream input;

        @Override
        public URLStreamHandler createURLStreamHandler(String protocol) {
            if("fuckyou".equals(protocol)) {
                return new ResourceStreamHandler(input);
            }

            return null;
        }
    }
}
