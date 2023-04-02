package com.hensemlee.bean;

import java.util.LinkedList;
import lombok.Data;

@Data
public class FixedSizeQueue<T> {
    private LinkedList<T> list = new LinkedList<>();
    private int maxSize;

    public FixedSizeQueue(int maxSize) {
        this.maxSize = maxSize;
    }

    public void add(T value) {
        list.addLast(value);
        if (list.size() > maxSize) {
            list.removeFirst();
        }
    }

    public T get(int index) {
        return list.get(index);
    }

    public int size() {
        return list.size();
    }
}
