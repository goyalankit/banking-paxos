package com.ut.paxos;

public class Message extends PaxosMessage{
}

class ReadOnlyCommandMessage extends Message{

    int minSlot;
    Command command;

    ReadOnlyCommandMessage(ProcessId src, Command cmd, int minSlot) {
        this.minSlot = minSlot;
        this.command = cmd;
        this.src = src;
    }
}

class ReadOnlyAckMessage extends Message{

    Command command;

    ReadOnlyAckMessage(ProcessId src, Command cmd) {
        this.command = cmd;
        this.src = src;
    }
}