package kr.jclab.netty.channel.iocp;

import io.netty.util.internal.SystemPropertyUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryLeakDetector {
    static final boolean MEMORY_LEAK_DETECTOR_ENABLE =
            SystemPropertyUtil.getBoolean("kr.jclab.netty.channel.iocp.memoryLeakDetector.enabled", false);
    static final Holder HOLDER = new Holder();


    public static class Item {
        private final Throwable stack;
        private final Object object;

        public Item(Throwable stack, Object object) {
            this.stack = stack;
            this.object = object;
        }

        public Throwable getStack() {
            return stack;
        }

        public Object getObject() {
            return object;
        }

        @Override
        public String toString() {
            return "Item{" +
                    "stack=" + stack +
                    ", object=" + object +
                    '}';
        }
    }

    public static class Holder {
        private final ConcurrentHashMap<Long, Item> aliveObjects = new ConcurrentHashMap<>();

        public void put(Long pointer, Item item) {
            aliveObjects.put(pointer, item);
        }

        public boolean remove(Long pointer) {
            return true; // aliveObjects.remove(pointer) != null;
        }

        public Collection<Item> aliveObjects() {
            return Collections.unmodifiableCollection(aliveObjects.values());
        }

        public Item findByAddress(long address) {
            return aliveObjects.get(address);
        }
    }


    public static void put(Long pointer, Object object) {
        if (MEMORY_LEAK_DETECTOR_ENABLE) {
            HOLDER.put(pointer, new Item(new Exception(), object));
        }
    }

    public static boolean remove(Long pointer) {
        if (MEMORY_LEAK_DETECTOR_ENABLE) {
            return HOLDER.remove(pointer);
        }
        return true;
    }

    public static Collection<Item> aliveObjects() {
        return HOLDER.aliveObjects();
    }

    public static Item findByAddress(long address) {
        return HOLDER.findByAddress(address);
    }
}
