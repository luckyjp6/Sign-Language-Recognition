package com.example.myapplication;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

public class ModelReturnQueue {
    private ArrayDeque<String> bufferQueue;
    ModelReturnQueue(){
        bufferQueue = new ArrayDeque<>();
    }

    public synchronized void enqueue(String input){
        bufferQueue.add(input);
        notify();
    }
    public synchronized String dequeue(){
        while (bufferQueue.isEmpty()) {
            try {
                // 如果队列为空，等待数据的到来
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return bufferQueue.poll();
    }
    public Boolean isEmpty() {
        return bufferQueue.isEmpty();
    }
    public void clear(){
        bufferQueue.clear();
    }
}
