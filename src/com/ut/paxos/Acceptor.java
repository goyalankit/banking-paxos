package com.ut.paxos;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class Acceptor extends Process {
    BallotNumber ballot_number = null;
    Set<PValue> accepted = new HashSet<PValue>();
    boolean leaseActive;
    long leaseTime = 10000;
    long leaseTimeout;
    ProcessId currentLeader;
    String logFile;

    public Acceptor(Env env, ProcessId me) {
        this.env = env;
        this.me = me;
        env.addProc(me, this);
        this.leaseActive = false;
        this.logFile = "logs/"+me.name.replace(":", "") + ".log";
    }

    public void body() {
        System.out.println("Here I am: " + me);
        for (; ; ) {
            PaxosMessage msg = getNextMessage();

            if(leaseActive){
                //System.err.println(me+ " Should process src " + msg.src+ " currentleader "+ currentLeader + " "+ msg.src.equals(currentLeader));

                ProcessId l = null;

                if(msg instanceof P1aMessage)
                    l = ((P1aMessage) msg).ballot_number.leader_id;
                else if(msg instanceof P2aMessage)
                    l = ((P2aMessage) msg).ballot_number.leader_id;

                if(l.equals(currentLeader))
                    processMessage(msg);
                else{
                    BallotNumber bn = new BallotNumber(-1, currentLeader);
                    sendMessage(msg.src, new P1bMessage(me, bn, new HashSet<PValue>(accepted)));
                }
            }
            else
                processMessage(msg);

        }
    }

    public void processMessage(PaxosMessage msg){
        if (msg instanceof P1aMessage) {
            P1aMessage m = (P1aMessage) msg;
            boolean awardedLease = false;

            //writeLog(me+" Phase 1a received from "+m.src);

            if (ballot_number == null ||
                    ballot_number.compareTo(m.ballot_number) <= 0) {
                ballot_number = m.ballot_number;

                if(m.asking_for_lease && !leaseActive){
                    grantLease(m.ballot_number.getLeader_id());
                    awardedLease = true;
                }
                else if(m.asking_for_lease && leaseActive && m.ballot_number.leader_id.equals(currentLeader)){
                    renewLease();
                    awardedLease = true;
                }
            }

            if(awardedLease){
                sendMessage(m.src, new P1bMessage(me, ballot_number, new HashSet<PValue>(accepted), true, (System.currentTimeMillis() + leaseTimeout)));
            }
            else{
                sendMessage(m.src, new P1bMessage(me, ballot_number, new HashSet<PValue>(accepted)));
            }

        } else if (msg instanceof P2aMessage) {
            P2aMessage m = (P2aMessage) msg;
            //System.out.println(me + " Phase 2a proposal received from " + m.ballot_number.getLeader_id() + " "+ m.command +" " + " with ballot number " + m.ballot_number);
            //System.err.println(me+" Phase 2a received for  "+m.command);
            writeLog(me+" Proposal Received for "+m.command + " from "+m.src);
            if (ballot_number == null ||
                    ballot_number.compareTo(m.ballot_number) <= 0) {
                ballot_number = m.ballot_number;
                accepted.add(new PValue(ballot_number, m.slot_number, m.command));
                writeLog(me+" Proposal Accepted for "+m.command);
            }
            sendMessage(m.src, new P2bMessage(me, ballot_number, m.slot_number));
        }
    }

    public void grantLease(ProcessId currentLeader){
        leaseActive = true;
        leaseTimeout = System.currentTimeMillis() + leaseTime;
        LeaseNotifier leaseNotifier = new LeaseNotifier();
        leaseNotifier.start();
        this.currentLeader = currentLeader;
        writeLog(me+" Grant lease to "+currentLeader.name);
    }

    public void unGrantLease(){
        leaseActive = false;
        leaseTimeout = System.currentTimeMillis();
        writeLog(me+" Expiring Lease for "+currentLeader.name);
        this.currentLeader = null;
    }

    public void renewLease(){
        System.out.println("*** Renewing Lease for "+ currentLeader.name + " ***");
        writeLog(me+" Renew Lease for "+currentLeader.name);
        leaseTimeout = System.currentTimeMillis() + leaseTime;
    }

    public void writeLog(String msg) {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true));
            bw.write(msg + "\n");
            bw.flush();
        } catch (IOException io) {
            System.err.println(io.getMessage());
        }
    }


    class LeaseNotifier extends Thread{

        public void run(){
            while(System.currentTimeMillis() < leaseTimeout){
                yield();
                continue;
            }

            unGrantLease();
        }
    }

}
