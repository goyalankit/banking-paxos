package com.ut.paxos;

import java.util.HashSet;
import java.util.Set;

public class Scout extends Process {
    ProcessId leader;
    ProcessId[] acceptors;
    BallotNumber ballot_number;
    boolean ask_for_lease;
    boolean leaseAwarded;

    public Scout(Env env, ProcessId me, ProcessId leader,
                 ProcessId[] acceptors, BallotNumber ballot_number) {
        this.env = env;
        this.me = me;
        this.acceptors = acceptors;
        this.leader = leader;
        this.ballot_number = ballot_number;
        this.leaseAwarded = false;
        this.ask_for_lease = false;
        env.addProc(me, this);
    }

    public Scout(Env env, ProcessId me, ProcessId leader, ProcessId[] acceptors, BallotNumber ballot_number, boolean ask_for_lease){
        this(env, me, leader, acceptors, ballot_number);
        this.ask_for_lease = ask_for_lease;
        this.leaseAwarded = false;
    }

    public void body() {
        P1aMessage m1;

        if(ask_for_lease){
            m1 = new P1aMessage(me, ballot_number, true);
            ask_for_lease = false;
        }
        else
            m1 = new P1aMessage(me, ballot_number);

        Set<ProcessId> waitfor = new HashSet<ProcessId>();
        for (ProcessId a : acceptors) {
            sendMessage(a, m1);
            waitfor.add(a);
        }

        Set<PValue> pvalues = new HashSet<PValue>();
        while (2 * waitfor.size() >= acceptors.length) {
            PaxosMessage msg = getNextMessage();

            if (msg instanceof P1bMessage) {
                P1bMessage m = (P1bMessage) msg;

                int cmp = ballot_number.compareTo(m.ballot_number);
                if (cmp != 0) {
                    sendMessage(leader, new PreemptedMessage(me, m.ballot_number));
                    return;
                }
                if (waitfor.contains(m.src)) {
                    waitfor.remove(m.src);
                    pvalues.addAll(m.accepted);
                }

                if(m.awardedLease)
                    leaseAwarded = true;


            } else {
                System.err.println("Scout: unexpected msg");
            }
        }
        if(leaseAwarded){
            System.err.println("Lease Awarded to leader "+leader.name);
            sendMessage(leader, new AdoptedMessage(me, ballot_number, pvalues, true));
            leaseAwarded = false;
            ask_for_lease = false;
        }
        else
            sendMessage(leader, new AdoptedMessage(me, ballot_number, pvalues));
    }
}
