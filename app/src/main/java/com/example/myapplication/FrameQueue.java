package com.example.myapplication;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

public class FrameQueue {
    private Queue<byte[]> bufferQueue;
    private int sent;
    private int whenToSendRequest;

    FrameQueue(){
        bufferQueue = new ArrayDeque<>();
        sent = 1;
        whenToSendRequest = 10;
    }

    public synchronized void enqueue(byte[] buffer) {
//        ByteBuffer buffer = ByteBuffer.wrap(data);
        bufferQueue.add(buffer);
        notify();
    }
    public synchronized byte[] dequeue() {
        while (bufferQueue.isEmpty()) {
            try {
                // 如果队列为空，等待数据的到来
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        sent = sent+1 % whenToSendRequest;
        return bufferQueue.poll();
    }
    public boolean isTimeToSendRequest(){
        return sent==0;
    }
    public void clear(){
        bufferQueue.clear();
    }
}
