package jdk.incubator.vector;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;

/**
 * A vector test plugin
 */
public class VectorPlugin implements Plugin {

    /**
     * Plugin constructor
     */
    public VectorPlugin() {

    }

    @Override
    public String getName() {
        return "VectorPlugin";
    }

    @Override
    public void init(JavacTask task, String... args) {
        System.out.println("Hello from " + getName());
    }

    @Override
    public boolean autoStart() {
        return false; //true;
    }
}