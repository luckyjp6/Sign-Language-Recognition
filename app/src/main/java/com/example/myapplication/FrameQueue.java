package com.example.myapplication;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

public class FrameQueue {
    private ArrayDeque<ByteBuffer> bufferQueue;
    private int sent = 1;
    private int whenToSendRequest = 10;

    FrameQueue(){
        bufferQueue = new ArrayDeque<>();
    }

    public synchronized void enqueue(ByteBuffer buffer) {
//        ByteBuffer buffer = ByteBuffer.wrap(data);
        bufferQueue.add(buffer);
        notify();
    }
    public synchronized ByteBuffer dequeue() {
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
